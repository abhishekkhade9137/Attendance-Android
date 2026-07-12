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
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
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
import com.example.facerecognitionimages.db.LogEntity;
import com.example.facerecognitionimages.db.MemberEntity;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RecognitionActivity extends AppCompatActivity {

    private PreviewView previewView;
    private ImageView overlayView;
    private TextView statusText;
    private ExecutorService cameraExecutor;
    private CameraSelector currentCameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
    private ProcessCameraProvider cameraProvider;

    private FaceDetector detector;
    private Facenet512 model;
    public static HashMap<String, float[]> faceEmbeddingsMap = new HashMap<>();
    private Map<String, Long> lastMarkedTime = new HashMap<>();
    private Map<String, Boolean> hasBlinked = new HashMap<>();
    private boolean isProcessing = false;
    private android.widget.RadioGroup attendanceTypeGroup;

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
        setContentView(R.layout.activity_recognition);

        previewView = findViewById(R.id.previewView);
        overlayView = findViewById(R.id.overlayView);
        statusText = findViewById(R.id.statusText);
        attendanceTypeGroup = findViewById(R.id.attendanceTypeGroup);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        cameraExecutor = Executors.newSingleThreadExecutor();

        findViewById(R.id.btnFlipCamera).setOnClickListener(v -> flipCamera());
        findViewById(R.id.btnGallery).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            galleryLauncher.launch(intent);
        });

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build();
        detector = FaceDetection.getClient(options);

        try {
            model = Facenet512.newInstance(this);
        } catch (IOException e) {
            Log.e("RecognitionActivity", "Model error", e);
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
                    if (isProcessing) {
                        imageProxy.close();
                        return;
                    }
                    isProcessing = true;
                    runOnUiThread(() -> {
                        Bitmap bitmap = previewView.getBitmap();
                        if (bitmap != null) {
                            processLiveFrame(bitmap, true);
                        }
                        imageProxy.close();
                        isProcessing = false;
                    });
                });

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, currentCameraSelector, preview, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                Log.e("RecognitionActivity", "Camera start failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void processLiveFrame(Bitmap bitmap, boolean isLiveCamera) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        detector.process(image)
                .addOnSuccessListener(faces -> {
                    if (faces.isEmpty()) {
                        overlayView.setImageBitmap(null);
                        return;
                    }

                    Bitmap canvasBitmap = null;
                    Canvas canvas = null;
                    Paint boxPaint = null;
                    Paint textPaint = null;

                    if (isLiveCamera) {
                        canvasBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
                        canvas = new Canvas(canvasBitmap);
                        
                        boxPaint = new Paint();
                        boxPaint.setStyle(Paint.Style.STROKE);
                        boxPaint.setStrokeWidth(5f);

                        textPaint = new Paint();
                        textPaint.setTextSize(50f);
                        textPaint.setFakeBoldText(true);
                    }

                    for (Face face : faces) {
                        Rect bounds = face.getBoundingBox();
                        String name = recognizeFace(bitmap, bounds);
                        
                        boolean isRecognized = !name.equals("Unknown");
                        int color = isRecognized ? Color.GREEN : Color.RED;
                        
                        String displayText = name;
                        if (isRecognized) {
                            markAttendance(name);
                        }
                        
                        if (isLiveCamera) {
                            boxPaint.setColor(color);
                            textPaint.setColor(color);
                            canvas.drawRect(bounds, boxPaint);
                            canvas.drawText(displayText, bounds.left, bounds.top - 10, textPaint);
                        }
                    }
                    if (isLiveCamera) {
                        overlayView.setImageBitmap(canvasBitmap);
                    }
                });
    }

    private void processGalleryImage(Uri uri) {
        try {
            ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r");
            Bitmap bitmap = BitmapFactory.decodeFileDescriptor(pfd.getFileDescriptor());
            pfd.close();
            processLiveFrame(bitmap, false);
        } catch (IOException e) {
            Log.e("RecognitionActivity", "Gallery error", e);
        }
    }

    private String recognizeFace(Bitmap bitmap, Rect bounds) {
        if (model == null) return "Unknown";
        try {
            int left = Math.max(0, bounds.left);
            int top = Math.max(0, bounds.top);
            int right = Math.min(bitmap.getWidth(), bounds.right);
            int bottom = Math.min(bitmap.getHeight(), bounds.bottom);
            int width = right - left;
            int height = bottom - top;

            if (width <= 0 || height <= 0) return "Unknown";

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

            return findMatch(emb);
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private String findMatch(float[] embedding) {
        String name = "Unknown";
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

    private void markAttendance(String name) {
        String type = attendanceTypeGroup.getCheckedRadioButtonId() == R.id.radioCheckIn ? "IN" : "OUT";
        long currentTime = System.currentTimeMillis();
        
        // Throttle to 60 seconds (1 minute) per person to prevent duplicate logs
        if (lastMarkedTime.containsKey(name) && (currentTime - lastMarkedTime.get(name) < 60000)) {
            return;
        }
        
        lastMarkedTime.put(name, currentTime);
        hasBlinked.put(name, false); // Reset blink
        
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        
        LogEntity log = new LogEntity();
        log.name = name;
        log.date = date;
        log.time = time;
        log.type = type;
        
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase.getDatabase(this).logDao().insertLog(log);
            runOnUiThread(() -> {
                Toast.makeText(this, "Marked " + type + " for " + name, Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void loadEmbeddings() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<MemberEntity> members = AppDatabase.getDatabase(this).memberDao().getAllMembers();
            HashMap<String, float[]> map = new HashMap<>();
            for (MemberEntity m : members) {
                map.put(m.name, m.embedding);
            }
            faceEmbeddingsMap = map;
            runOnUiThread(() -> statusText.setText("Registered members: " + faceEmbeddingsMap.size()));
        });
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
}