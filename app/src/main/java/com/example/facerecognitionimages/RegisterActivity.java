package com.example.facerecognitionimages;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
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
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.facerecognitionimages.ml.Facenet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import com.example.facerecognitionimages.db.AppDatabase;
import com.example.facerecognitionimages.db.MemberEntity;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.FileOutputStream;
import java.io.IOException;
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
    private View processingCard;
    private ExecutorService cameraExecutor;
    private ExecutorService recognitionExecutor;
    private long lastToastTime = 0;
    private CameraSelector currentCameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
    private ProcessCameraProvider cameraProvider;

    private FaceDetector detector;
    private Facenet model;
    public static java.util.List<RecognitionActivity.PersonEmbedding> faceEmbeddingsList = new java.util.ArrayList<>();
    private boolean isProcessing = false;
    private boolean isDialogActive = false;
    private boolean isReadyToScan = false;
    private android.widget.Button btnReady;
    
    private Bitmap reusableBitmap = null;
    private Canvas reusableCanvas = null;
    private Paint boxPaint = new Paint();

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
        processingCard = findViewById(R.id.processingCard);
        
        btnReady = findViewById(R.id.btnReady);
        btnReady.setOnClickListener(v -> {
            isReadyToScan = true;
            btnReady.setEnabled(false);
            btnReady.setText("Scanning...");
        });
        
        boxPaint.setColor(Color.WHITE);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(5f);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        cameraExecutor = Executors.newSingleThreadExecutor();
        recognitionExecutor = Executors.newSingleThreadExecutor(r -> new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            r.run();
        }));
        findViewById(R.id.btnFlipCamera).setOnClickListener(v -> flipCamera());
        findViewById(R.id.btnGallery).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            galleryLauncher.launch(intent);
        });

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build();
        detector = FaceDetection.getClient(options);

        try {
            model = Facenet.newInstance(this);
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
                    if (isProcessing || isDialogActive || !isReadyToScan) {
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

                try {
                    if (!cameraProvider.hasCamera(currentCameraSelector)) {
                        if (currentCameraSelector == CameraSelector.DEFAULT_BACK_CAMERA && cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                            currentCameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
                        } else if (currentCameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA && cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                            currentCameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                        } else {
                            runOnUiThread(() -> Toast.makeText(this, "No camera found on this device.", Toast.LENGTH_LONG).show());
                            return;
                        }
                    }
                    cameraProvider.bindToLifecycle(this, currentCameraSelector, preview, imageAnalysis);
                } catch (Exception e) {
                    Log.e("RegisterActivity", "Camera binding failed", e);
                    runOnUiThread(() -> Toast.makeText(this, "Camera error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }

            } catch (Exception e) {
                Log.e("RegisterActivity", "Camera start failed", e);
                runOnUiThread(() -> Toast.makeText(this, "Camera initialization failed.", Toast.LENGTH_LONG).show());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void detectAndRegister(Bitmap bitmap) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        detector.process(image)
                .addOnSuccessListener(faces -> {
                    if (faces.isEmpty()) {
                        if (reusableCanvas != null) {
                            reusableCanvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR);
                            overlayView.invalidate();
                        }
                        return;
                    }

                    if (reusableBitmap == null || reusableBitmap.getWidth() != bitmap.getWidth() || reusableBitmap.getHeight() != bitmap.getHeight()) {
                        reusableBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
                        reusableCanvas = new Canvas(reusableBitmap);
                    }
                    
                    reusableCanvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR);

                    for (Face face : faces) {
                        reusableCanvas.drawRect(face.getBoundingBox(), boxPaint);
                    }
                    overlayView.setImageBitmap(reusableBitmap);

                    if (!isDialogActive && !faces.isEmpty()) {
                        isDialogActive = true;
                        isReadyToScan = false;
                        btnReady.setEnabled(true);
                        btnReady.setText("Ready");
                        
                        Face face = faces.get(0);
                        Rect bounds = face.getBoundingBox();
                        
                        processingCard.setVisibility(View.VISIBLE);
                        recognitionExecutor.execute(() -> {
                            float[] embedding = getEmbedding(bitmap, bounds);
                            runOnUiThread(() -> {
                                processingCard.setVisibility(View.GONE);
                                if (embedding != null) {
                                    showRegistrationDialog(bitmap, bounds, embedding);
                                } else {
                                    isDialogActive = false;
                                }
                            });
                        });
                    }
                });
    }

    private void showRegistrationDialog(Bitmap bitmap, Rect bounds, float[] embedding) {
        String existingMatch = findMatch(embedding);

        int left = Math.max(0, bounds.left);
        int top = Math.max(0, bounds.top);
        int right = Math.min(bitmap.getWidth(), bounds.right);
        int bottom = Math.min(bitmap.getHeight(), bounds.bottom);
        int width = right - left;
        int height = bottom - top;
        Bitmap cropped = null;
        if (width > 0 && height > 0) {
            cropped = Bitmap.createBitmap(bitmap, left, top, width, height);
        }
        final Bitmap finalCropped = cropped;

        if (existingMatch != null) {
            AlertDialog.Builder verifyBuilder = new AlertDialog.Builder(this);
            verifyBuilder.setTitle("Verify Person");
            verifyBuilder.setMessage("This face looks like " + existingMatch + ". Is this the same person?");
            if (finalCropped != null) {
                ImageView iv = new ImageView(this);
                iv.setImageBitmap(finalCropped);
                iv.setPadding(0, 30, 0, 30);
                verifyBuilder.setView(iv);
            }
            verifyBuilder.setPositiveButton("Yes, add photo", (dialog, which) -> {
                saveFaceData(existingMatch, embedding, finalCropped);
                isDialogActive = false;
            });
            verifyBuilder.setNegativeButton("No, new person", (dialog, which) -> {
                showNewPersonDialog(finalCropped, embedding);
            });
            verifyBuilder.setCancelable(false);
            verifyBuilder.show();
        } else {
            showNewPersonDialog(finalCropped, embedding);
        }
    }

    private void showNewPersonDialog(Bitmap finalCropped, float[] embedding) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.register_face_dialogue, null);
        builder.setView(dialogView);
        builder.setCancelable(false);

        ImageView dlgImage = dialogView.findViewById(R.id.dlg_image);
        EditText dlgInput = dialogView.findViewById(R.id.dlg_input);
        Button dlgBtn = dialogView.findViewById(R.id.button2);

        if (finalCropped != null) {
            dlgImage.setImageBitmap(finalCropped);
        }

        AlertDialog alertDialog = builder.create();

        dlgBtn.setOnClickListener(v -> {
            String name = dlgInput.getText().toString().trim();
            if (!name.isEmpty()) {
                saveFaceData(name, embedding, finalCropped);
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
    }

    private void saveFaceData(String name, float[] embedding, Bitmap finalCropped) {
        MemberEntity member = new MemberEntity();
        member.name = name;
        member.embedding = embedding;
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase.getDatabase(RegisterActivity.this).memberDao().insertMember(member);
            AppDatabase.getDatabase(RegisterActivity.this).memberDao().keepRecentFaces(name, 5);
            loadEmbeddings();
        });

        if (finalCropped != null) {
            java.io.File imgFile = new java.io.File(getFilesDir(), name + "_face.png");
            if (!imgFile.exists()) {
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(imgFile)) {
                    finalCropped.compress(Bitmap.CompressFormat.PNG, 100, out);
                } catch (java.io.IOException e) {
                    e.printStackTrace();
                }
            }
        }

        Toast.makeText(this, "Registered photo for: " + name, Toast.LENGTH_SHORT).show();
    }

    private float[] getEmbedding(Bitmap bitmap, Rect bounds) {
        if (model == null) return null;
        try {
            int left = Math.max(0, bounds.left);
            int top = Math.max(0, bounds.top);
            int right = Math.min(bitmap.getWidth(), bounds.right);
            int bottom = Math.min(bitmap.getHeight(), bounds.bottom);
            int width = right - left;
            int height = bottom - top;

            if (width <= 0 || height <= 0) return null;

            Bitmap cropped = Bitmap.createBitmap(bitmap, left, top, width, height);
            Bitmap resized = Bitmap.createScaledBitmap(cropped, 160, 160, true);

            ByteBuffer buffer = ByteBuffer.allocateDirect(4 * 160 * 160 * 3);
            buffer.order(ByteOrder.nativeOrder());
            int[] pixels = new int[160 * 160];
            resized.getPixels(pixels, 0, 160, 0, 0, 160, 160);
            for (int val : pixels) {
                buffer.putFloat((((val >> 16) & 0xFF) - 127.5f) / 127.5f);
                buffer.putFloat((((val >> 8) & 0xFF) - 127.5f) / 127.5f);
                buffer.putFloat(((val & 0xFF) - 127.5f) / 127.5f);
            }

            TensorBuffer input = TensorBuffer.createFixedSize(new int[]{1, 160, 160, 3}, DataType.FLOAT32);
            input.loadBuffer(buffer);
            Facenet.Outputs outputs = model.process(input);
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

    @SuppressLint("ClickableViewAccessibility")
    private void processGalleryFaces(Bitmap bitmap) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        detector.process(image)
                .addOnSuccessListener(faces -> {
                    if (faces.isEmpty()) {
                        Toast.makeText(this, "No faces detected in image", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Draw boxes on a copy of the bitmap
                    Bitmap canvasBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
                    Canvas canvas = new Canvas(canvasBitmap);
                    Paint paint = new Paint();
                    paint.setColor(Color.WHITE);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(8f);

                    for (Face face : faces) {
                        canvas.drawRect(face.getBoundingBox(), paint);
                    }

                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    View dialogView = getLayoutInflater().inflate(R.layout.dialog_choose_face, null);
                    builder.setView(dialogView);
                    AlertDialog alertDialog = builder.create();

                    ImageView ivGalleryFaces = dialogView.findViewById(R.id.ivGalleryFaces);
                    ivGalleryFaces.setImageBitmap(canvasBitmap);

                    ivGalleryFaces.setOnTouchListener((v, event) -> {
                        if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                            if (isDialogActive) return true; // Prevent tapping another face while dialog is open

                            // Map touch coordinates to bitmap coordinates
                            float[] pts = {event.getX(), event.getY()};
                            android.graphics.Matrix inverse = new android.graphics.Matrix();
                            ivGalleryFaces.getImageMatrix().invert(inverse);
                            inverse.mapPoints(pts);

                            float mappedX = pts[0];
                            float mappedY = pts[1];

                            for (Face face : faces) {
                                if (face.getBoundingBox().contains((int) mappedX, (int) mappedY)) {
                                    // Do NOT dismiss the gallery dialog here! Allow multiple faces to be selected.
                                    
                                    Rect bounds = face.getBoundingBox();
                                    processingCard.setVisibility(View.VISIBLE);
                                    recognitionExecutor.execute(() -> {
                                        float[] embedding = getEmbedding(bitmap, bounds);
                                        runOnUiThread(() -> {
                                            processingCard.setVisibility(View.GONE);
                                            if (embedding != null) {
                                                isDialogActive = true;
                                                showRegistrationDialog(bitmap, bounds, embedding);
                                            } else {
                                                Toast.makeText(this, "Failed to extract face", Toast.LENGTH_SHORT).show();
                                            }
                                        });
                                    });
                                    return true;
                                }
                            }
                        }
                        return true;
                    });

                    dialogView.findViewById(R.id.btnCancelGallery).setOnClickListener(v -> alertDialog.dismiss());
                    alertDialog.show();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Face detection failed", Toast.LENGTH_SHORT).show());
    }

    private Bitmap getBitmapFromUri(Uri uri) throws IOException {
        ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r");
        Bitmap bitmap = BitmapFactory.decodeFileDescriptor(pfd.getFileDescriptor());
        pfd.close();

        try (java.io.InputStream input = getContentResolver().openInputStream(uri)) {
            if (input != null) {
                androidx.exifinterface.media.ExifInterface exif = new androidx.exifinterface.media.ExifInterface(input);
                int orientation = exif.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL);

                android.graphics.Matrix matrix = new android.graphics.Matrix();
                switch (orientation) {
                    case androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90:
                        matrix.postRotate(90);
                        break;
                    case androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180:
                        matrix.postRotate(180);
                        break;
                    case androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270:
                        matrix.postRotate(270);
                        break;
                }
                if (!matrix.isIdentity()) {
                    bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                }
            }
        }
        return bitmap;
    }

    private void processGalleryImage(Uri uri) {
        progressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                Bitmap bitmap = getBitmapFromUri(uri);
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    processGalleryFaces(bitmap);
                });
            } catch (IOException e) {
                runOnUiThread(() -> progressBar.setVisibility(View.GONE));
            }
        }).start();
    }

    private void loadEmbeddings() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<MemberEntity> members = AppDatabase.getDatabase(this).memberDao().getAllMembers();
            java.util.List<RecognitionActivity.PersonEmbedding> list = new java.util.ArrayList<>();
            for (MemberEntity m : members) {
                list.add(new RecognitionActivity.PersonEmbedding(m.name, m.embedding));
            }
            faceEmbeddingsList = list;
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
        recognitionExecutor.shutdownNow();
        if (model != null) model.close();
    }

    private String findMatch(float[] embedding) {
        String name = null;
        float minDistance = 1.0f; // FaceNet threshold usually around 1.0 for L2
        for (RecognitionActivity.PersonEmbedding entry : faceEmbeddingsList) {
            float dist = 0;
            float[] stored = entry.embedding;
            for (int i = 0; i < embedding.length; i++) {
                float diff = embedding[i] - stored[i];
                dist += diff * diff;
            }
            dist = (float) Math.sqrt(dist);
            if (dist < minDistance) {
                minDistance = dist;
                name = entry.name;
            }
        }
        return name;
    }
}