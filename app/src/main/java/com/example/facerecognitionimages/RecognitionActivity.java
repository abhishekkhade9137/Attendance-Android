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
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.facerecognitionimages.utils.UIHelper;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.media.MediaPlayer;
import android.media.RingtoneManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
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
import com.example.facerecognitionimages.db.LogEntity;
import com.example.facerecognitionimages.db.MemberEntity;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class RecognitionActivity extends AppCompatActivity {

    private PreviewView previewView;
    private ImageView overlayView;
    private TextView statusText;
    private View processingCard;
    private ExecutorService cameraExecutor;
    private ExecutorService recognitionExecutor;
    private CameraSelector currentCameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
    private ProcessCameraProvider cameraProvider;

    private FaceDetector detector;
    private Facenet model;
    public static class PersonEmbedding {
        public String name;
        public float[] embedding;
        public PersonEmbedding(String name, float[] embedding) {
            this.name = name;
            this.embedding = embedding;
        }
    }
    public static java.util.List<PersonEmbedding> faceEmbeddingsList = new java.util.ArrayList<>();
    private static class QueuedFace {
        Bitmap bitmap;
        Integer trackingId;
        QueuedFace(Bitmap b, Integer id) {
            bitmap = b; trackingId = id;
        }
    }

    private Map<String, Long> lastMarkedTime = new HashMap<>();
    private Map<Integer, Long> lastQueuedTime = new HashMap<>();
    private Map<Integer, String> recognizedTrackingIds = new ConcurrentHashMap<>();
    private ConcurrentLinkedQueue<QueuedFace> faceProcessingQueue = new ConcurrentLinkedQueue<>();
    private String currentAttendanceType = "IN";
    
    private Bitmap reusableBitmap = null;
    private Canvas reusableCanvas = null;
    private Paint boxPaint = new Paint();
    private Paint textPaint = new Paint();
    
    private final AtomicBoolean isProcessingFrame = new AtomicBoolean(false);

    private static final int PERMISSION_CODE = 100;

    ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    Intent intent = new Intent(RecognitionActivity.this, GroupPhotoActivity.class);
                    intent.putExtra("IMAGE_URI", imageUri.toString());
                    intent.putExtra("SCAN_MODE", currentAttendanceType);
                    startActivity(intent);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recognition);

        previewView = findViewById(R.id.previewView);
        overlayView = findViewById(R.id.overlayView);
        statusText = findViewById(R.id.statusText);
        processingCard = findViewById(R.id.processingCard);

        String initialMode = getIntent().getStringExtra("SCAN_MODE");
        if (initialMode != null) {
            currentAttendanceType = initialMode;
        } else {
            currentAttendanceType = "IN";
        }

        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(5f);
        textPaint.setTextSize(50f);
        textPaint.setFakeBoldText(true);

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
                .enableTracking()
                .build();
        detector = FaceDetection.getClient(options);

        try {
            try {
                org.tensorflow.lite.support.model.Model.Options tfOptions = new org.tensorflow.lite.support.model.Model.Options.Builder()
                        .setDevice(org.tensorflow.lite.support.model.Model.Device.GPU)
                        .build();
                model = Facenet.newInstance(this, tfOptions);
            } catch (Exception e) {
                Log.e("RecognitionActivity", "GPU acceleration failed, falling back to CPU", e);
                model = Facenet.newInstance(this);
            }
        } catch (IOException e) {
            Log.e("RecognitionActivity", "Model error", e);
        }

        loadEmbeddings();
        startRecognitionLoop();

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
                    if (isProcessingFrame.get()) {
                        imageProxy.close();
                        return;
                    }
                    isProcessingFrame.set(true);
                    // Extract bitmap and pass to UI thread for drawing and MLKit processing
                    // We must do it on UI thread to ensure bitmap matches previewView accurately for bounds
                    runOnUiThread(() -> {
                        Bitmap bitmap = previewView.getBitmap();
                        if (bitmap != null) {
                            InputImage image = InputImage.fromBitmap(bitmap, 0);
                            detector.process(image)
                                .addOnSuccessListener(faces -> handleFaces(faces, bitmap))
                                .addOnFailureListener(e -> isProcessingFrame.set(false))
                                .addOnCompleteListener(task -> imageProxy.close());
                        } else {
                            imageProxy.close();
                            isProcessingFrame.set(false);
                        }
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
                    Log.e("RecognitionActivity", "Camera binding failed", e);
                    runOnUiThread(() -> Toast.makeText(this, "Camera error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }

            } catch (Exception e) {
                Log.e("RecognitionActivity", "Camera start failed", e);
                runOnUiThread(() -> Toast.makeText(this, "Camera initialization failed.", Toast.LENGTH_LONG).show());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void handleFaces(List<Face> faces, Bitmap bitmap) {
        if (faces.isEmpty()) {
            if (reusableCanvas != null) {
                reusableCanvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR);
                overlayView.invalidate();
            }
            isProcessingFrame.set(false);
            return;
        }

        if (reusableBitmap == null || reusableBitmap.getWidth() != bitmap.getWidth() || reusableBitmap.getHeight() != bitmap.getHeight()) {
            reusableBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
            reusableCanvas = new Canvas(reusableBitmap);
        }
        
        reusableCanvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR);

        long currentTime = System.currentTimeMillis();

        for (Face face : faces) {
            Rect bounds = face.getBoundingBox();
            Integer trackingId = face.getTrackingId();

            if (trackingId != null && recognizedTrackingIds.containsKey(trackingId)) {
                // Already recognized, just draw green box and name
                boxPaint.setColor(Color.GREEN);
                reusableCanvas.drawRect(bounds, boxPaint);
                textPaint.setColor(Color.GREEN);
                reusableCanvas.drawText(recognizedTrackingIds.get(trackingId), bounds.left, bounds.top - 10, textPaint);
                continue;
            }

            boxPaint.setColor(Color.WHITE);
            reusableCanvas.drawRect(bounds, boxPaint);

            if (trackingId != null) {
                Long lastQueued = lastQueuedTime.get(trackingId);
                // Queue same face at most once every 1 second (1000ms) to ensure it gets recognized quickly if initial crop was bad
                if (lastQueued == null || currentTime - lastQueued > 1000) {
                    lastQueuedTime.put(trackingId, currentTime);
                    
                    int left = Math.max(0, bounds.left);
                    int top = Math.max(0, bounds.top);
                    int right = Math.min(bitmap.getWidth(), bounds.right);
                    int bottom = Math.min(bitmap.getHeight(), bounds.bottom);
                    int width = right - left;
                    int height = bottom - top;

                    if (width > 0 && height > 0) {
                        Bitmap cropped = Bitmap.createBitmap(bitmap, left, top, width, height);
                        faceProcessingQueue.add(new QueuedFace(cropped, trackingId));
                    }
                }
            }
        }
        overlayView.setImageBitmap(reusableBitmap);
        isProcessingFrame.set(false);
    }

    private void startRecognitionLoop() {
        recognitionExecutor.execute(() -> {
            while (!Thread.interrupted()) {
                QueuedFace qFace = faceProcessingQueue.poll();
                if (qFace != null) {
                    runOnUiThread(() -> processingCard.setVisibility(View.VISIBLE));
                    
                    String name = recognizeFace(qFace.bitmap);
                    if (!name.equals("Unknown")) {
                        if (qFace.trackingId != null) {
                            recognizedTrackingIds.put(qFace.trackingId, name);
                        }
                        markAttendance(name);
                    }
                    
                    runOnUiThread(() -> {
                        if (faceProcessingQueue.isEmpty()) {
                            processingCard.setVisibility(View.GONE);
                        }
                    });
                } else {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        });
    }

    private Bitmap getBitmapFromUri(Uri uri) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        
        ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r");
        BitmapFactory.decodeFileDescriptor(pfd.getFileDescriptor(), null, options);
        
        int reqWidth = 1080;
        int reqHeight = 1920;
        int inSampleSize = 1;

        if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
            final int halfHeight = options.outHeight / 2;
            final int halfWidth = options.outWidth / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        options.inJustDecodeBounds = false;
        options.inSampleSize = inSampleSize;
        
        Bitmap bitmap = BitmapFactory.decodeFileDescriptor(pfd.getFileDescriptor(), null, options);
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
        try {
            Bitmap bitmap = getBitmapFromUri(uri);
            
            InputImage image = InputImage.fromBitmap(bitmap, 0);
            detector.process(image)
                    .addOnSuccessListener(faces -> {
                        if (faces.isEmpty()) {
                            Toast.makeText(this, "No faces found in the selected photo.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Toast.makeText(this, "Found " + faces.size() + " faces. Processing attendance...", Toast.LENGTH_LONG).show();
                        for (Face face : faces) {
                            Rect bounds = face.getBoundingBox();
                            int left = Math.max(0, bounds.left);
                            int top = Math.max(0, bounds.top);
                            int right = Math.min(bitmap.getWidth(), bounds.right);
                            int bottom = Math.min(bitmap.getHeight(), bounds.bottom);
                            int width = right - left;
                            int height = bottom - top;
                            if (width > 0 && height > 0) {
                                Bitmap cropped = Bitmap.createBitmap(bitmap, left, top, width, height);
                                faceProcessingQueue.add(new QueuedFace(cropped, face.getTrackingId()));
                            }
                        }
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Failed to analyze photo.", Toast.LENGTH_SHORT).show());
        } catch (IOException e) {
            Log.e("RecognitionActivity", "Gallery error", e);
        }
    }

    private String recognizeFace(Bitmap cropped) {
        if (model == null) return "Unknown";
        try {
            Bitmap resized = Bitmap.createScaledBitmap(cropped, 160, 160, true);

            ByteBuffer buffer = ByteBuffer.allocateDirect(4 * 160 * 160 * 3);
            buffer.order(ByteOrder.nativeOrder());
            int[] pixels = new int[160 * 160];
            resized.getPixels(pixels, 0, 160, 0, 0, 160, 160);
            for (int val : pixels) {
                // FaceNet standard normalization: (pixel - 127.5) / 127.5
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

            return findMatch(emb);
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private String findMatch(float[] embedding) {
        String name = "Unknown";
        float minDistance = 0.75f; // Stricter threshold for siblings (was 1.0f)
        for (PersonEmbedding entry : faceEmbeddingsList) {
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

    private void setAttendanceType(String type) {
        currentAttendanceType = type;
    }

    private void markAttendance(String name) {
        runOnUiThread(() -> {
            String type = currentAttendanceType;
            long currentTime = System.currentTimeMillis();
            
            // Throttle based on SharedPreferences cooldown (in seconds)
            android.content.SharedPreferences prefs = getSharedPreferences(SettingsActivity.PREF_NAME, Context.MODE_PRIVATE);
            int cooldownSecs = prefs.getInt(SettingsActivity.KEY_COOLDOWN, 3);
            long cooldownMs = cooldownSecs * 1000L;
            
            if (lastMarkedTime.containsKey(name) && (currentTime - lastMarkedTime.get(name) < cooldownMs)) {
                return;
            }
            
            lastMarkedTime.put(name, currentTime);
            
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
                    boolean hapticsEnabled = prefs.getBoolean(SettingsActivity.KEY_HAPTICS, true);
                    if (hapticsEnabled) {
                        // Haptic Feedback
                        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                        if (vibrator != null && vibrator.hasVibrator()) {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK));
                            } else {
                                vibrator.vibrate(50); // Fallback for older devices
                            }
                        }
                        
                        // Audio Cue
                        try {
                            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                            MediaPlayer mp = MediaPlayer.create(getApplicationContext(), notification);
                            mp.start();
                            mp.setOnCompletionListener(MediaPlayer::release);
                        } catch (Exception e) {}
                    }
                    
                    // Snackbar Notification
                    UIHelper.showSuccessSnackbar(findViewById(android.R.id.content), "Marked " + type + " for " + name);
                });
            });
        });
    }

    private void loadEmbeddings() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<MemberEntity> members = AppDatabase.getDatabase(this).memberDao().getAllMembers();
            java.util.List<PersonEmbedding> list = new java.util.ArrayList<>();
            for (MemberEntity m : members) {
                list.add(new PersonEmbedding(m.name, m.embedding));
            }
            faceEmbeddingsList = list;
            
            // Count unique members for the UI
            java.util.HashSet<String> uniqueNames = new java.util.HashSet<>();
            for (PersonEmbedding p : list) uniqueNames.add(p.name);
            runOnUiThread(() -> statusText.setText("Registered members: " + uniqueNames.size() + " (" + list.size() + " faces)"));
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
}