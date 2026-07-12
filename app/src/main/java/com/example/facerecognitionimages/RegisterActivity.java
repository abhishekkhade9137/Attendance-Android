package com.example.facerecognitionimages;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.facerecognitionimages.ml.Facenet512;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import com.example.facerecognitionimages.db.AppDatabase;
import com.example.facerecognitionimages.db.MemberEntity;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RegisterActivity extends AppCompatActivity {

    private PreviewView previewView;
    private ImageView overlayView;
    private ProgressBar progressBar;
    private ExecutorService cameraExecutor;
    private long lastToastTime = 0;
    private CameraSelector currentCameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
    private ProcessCameraProvider cameraProvider;

    private FaceDetector detector;
    private Facenet512 model;
    public static HashMap<String, float[]> faceEmbeddingsMap = new HashMap<>();
    private boolean isProcessing = false;
    private boolean isDialogActive = false;

    private static final int PERMISSION_CODE = 100;

    ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    processGalleryImage(imageUri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        previewView = findViewById(R.id.previewView);
        overlayView = findViewById(R.id.overlayView);
        progressBar = findViewById(R.id.progressBar);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        cameraExecutor = Executors.newSingleThreadExecutor();
        findViewById(R.id.btnFlipCamera).setOnClickListener(v -> flipCamera());
        findViewById(R.id.btnGallery).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            galleryLauncher.launch(intent);
        });

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .build();
        detector = FaceDetection.getClient(options);

        try {
            model = Facenet512.newInstance(this);
        } catch (IOException e) {
            Log.e("RegisterActivity", "Model error", e);
        }

        loadEmbeddings();

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_CODE);
        }
    }

    private void flipCamera() {
        if (currentCameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            currentCameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
        } else {
            currentCameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
        }
        startCamera();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
                    if (isProcessing || isDialogActive) {
                        imageProxy.close();
                        return;
                    }
                    isProcessing = true;
                    runOnUiThread(() -> {
                        Bitmap bitmap = previewView.getBitmap();
                        if (bitmap != null) {
                            detectAndRegister(bitmap);
                        }
                        imageProxy.close();
                        isProcessing = false;
                    });
                });

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, currentCameraSelector, preview, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                Log.e("RegisterActivity", "Camera start failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void detectAndRegister(Bitmap bitmap) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        detector.process(image)
                .addOnSuccessListener(faces -> {
                    if (faces.isEmpty()) {
                        overlayView.setImageBitmap(null);
                        return;
                    }

                    Bitmap canvasBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(canvasBitmap);
                    Paint paint = new Paint();
                    paint.setColor(Color.WHITE);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(5f);

                    for (Face face : faces) {
                        canvas.drawRect(face.getBoundingBox(), paint);
                    }
                    overlayView.setImageBitmap(canvasBitmap);

                    if (!isDialogActive && !faces.isEmpty()) {
                        showRegistrationDialog(bitmap, faces.get(0).getBoundingBox());
                    }
                });
    }

    private void showRegistrationDialog(Bitmap bitmap, Rect bounds) {
        isDialogActive = true;
        
        float[] embedding = getEmbedding(bitmap, bounds);
        if (embedding == null) {
            isDialogActive = false;
            return;
        }

        String existingMatch = findMatch(embedding);
        if (existingMatch != null) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastToastTime > 3000) {
                runOnUiThread(() -> Toast.makeText(RegisterActivity.this, "Face already registered as " + existingMatch + "!", Toast.LENGTH_SHORT).show());
                lastToastTime = currentTime;
            }
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> isDialogActive = false, 2000);
            return;
        }

        runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            LayoutInflater inflater = getLayoutInflater();
            View dialogView = inflater.inflate(R.layout.register_face_dialogue, null);
            builder.setView(dialogView);
            builder.setCancelable(false);

            ImageView dlgImage = dialogView.findViewById(R.id.dlg_image);
            EditText dlgInput = dialogView.findViewById(R.id.dlg_input);
            Button dlgBtn = dialogView.findViewById(R.id.button2);

            // Crop face for dialog preview
            int left = Math.max(0, bounds.left);
            int top = Math.max(0, bounds.top);
            int right = Math.min(bitmap.getWidth(), bounds.right);
            int bottom = Math.min(bitmap.getHeight(), bounds.bottom);
            Bitmap cropped = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top);
            dlgImage.setImageBitmap(cropped);

            AlertDialog alertDialog = builder.create();

            dlgBtn.setOnClickListener(v -> {
                String name = dlgInput.getText().toString().trim();
                if (!name.isEmpty()) {
                    if (faceEmbeddingsMap.containsKey(name)) {
                        Toast.makeText(RegisterActivity.this, "Name already registered!", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    faceEmbeddingsMap.put(name, embedding);
                    
                    MemberEntity member = new MemberEntity();
                    member.name = name;
                    member.embedding = embedding;
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        AppDatabase.getDatabase(RegisterActivity.this).memberDao().insertMember(member);
                    });
                    
                    try (FileOutputStream out = openFileOutput(name + "_face.png", Context.MODE_PRIVATE)) {
                        cropped.compress(Bitmap.CompressFormat.PNG, 100, out);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    
                    Toast.makeText(this, "Registered: " + name, Toast.LENGTH_SHORT).show();
                    alertDialog.dismiss();
                    isDialogActive = false;
                } else {
                    Toast.makeText(this, "Enter name", Toast.LENGTH_SHORT).show();
                }
            });

            Button cancelBtn = new Button(this);
            cancelBtn.setText("Cancel");
            ((android.view.ViewGroup)dialogView).addView(cancelBtn);
            cancelBtn.setOnClickListener(v -> {
                alertDialog.dismiss();
                isDialogActive = false;
            });

            alertDialog.show();
        });
    }

    private float[] getEmbedding(Bitmap bitmap, Rect bounds) {
        if (model == null) return null;
        try {
            int left = Math.max(0, bounds.left);
            int top = Math.max(0, bounds.top);
            int width = Math.min(bitmap.getWidth() - left, bounds.width());
            int height = Math.min(bitmap.getHeight() - top, bounds.height());
            if (width <= 0 || height <= 0) return null;

            Bitmap cropped = Bitmap.createBitmap(bitmap, left, top, width, height);
            Bitmap resized = Bitmap.createScaledBitmap(cropped, 160, 160, true);
            
            ByteBuffer buffer = ByteBuffer.allocateDirect(4 * 160 * 160 * 3);
            buffer.order(ByteOrder.nativeOrder());
            int[] pixels = new int[160 * 160];
            resized.getPixels(pixels, 0, 160, 0, 0, 160, 160);
            for (int val : pixels) {
                buffer.putFloat(((val >> 16) & 0xFF) / 255.0f);
                buffer.putFloat(((val >> 8) & 0xFF) / 255.0f);
                buffer.putFloat((val & 0xFF) / 255.0f);
            }

            TensorBuffer input = TensorBuffer.createFixedSize(new int[]{1, 160, 160, 3}, DataType.FLOAT32);
            input.loadBuffer(buffer);
            Facenet512.Outputs outputs = model.process(input);
            float[] emb = outputs.getOutputFeature0AsTensorBuffer().getFloatArray();
            
            // L2 Normalize
            float sum = 0;
            for (float v : emb) sum += v * v;
            float norm = (float) Math.sqrt(sum);
            if (norm > 0) for (int i = 0; i < emb.length; i++) emb[i] /= norm;
            
            return emb;
        } catch (Exception e) {
            return null;
        }
    }

    private void processGalleryImage(Uri uri) {
        progressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r");
                Bitmap bitmap = BitmapFactory.decodeFileDescriptor(pfd.getFileDescriptor());
                pfd.close();
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    detectAndRegister(bitmap);
                });
            } catch (IOException e) {
                runOnUiThread(() -> progressBar.setVisibility(View.GONE));
            }
        }).start();
    }

    private void loadEmbeddings() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<MemberEntity> members = AppDatabase.getDatabase(this).memberDao().getAllMembers();
            HashMap<String, float[]> map = new HashMap<>();
            for (MemberEntity m : members) {
                map.put(m.name, m.embedding);
            }
            faceEmbeddingsMap = map;
        });
    }

    private void saveEmbeddings() {
        // Obsolete
    }

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_CODE && allPermissionsGranted()) {
            startCamera();
        } else {
            Toast.makeText(this, "Permission required", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        if (model != null) model.close();
    }

    private String findMatch(float[] embedding) {
        String name = null;
        float minDistance = 0.75f;
        for (Map.Entry<String, float[]> entry : faceEmbeddingsMap.entrySet()) {
            float dist = 0;
            float[] stored = entry.getValue();
            for (int i = 0; i < embedding.length; i++) {
                float diff = embedding[i] - stored[i];
                dist += diff * diff;
            }
            dist = (float) Math.sqrt(dist);
            if (dist < minDistance) {
                minDistance = dist;
                name = entry.getKey();
            }
        }
        return name;
    }
}