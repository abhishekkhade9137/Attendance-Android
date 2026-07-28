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
    private BoundingBoxOverlay overlayView;
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
    public static volatile java.util.List<PersonEmbedding> faceEmbeddingsList = new java.util.concurrent.CopyOnWriteArrayList<>();
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
    private volatile boolean isActivityDestroyed = false;

    // Performance tracking
    private long lastFpsTime = 0;
    private int frameCount = 0;
    private static final String PERF_TAG = "PerfLog";
    
    // Reusable memory buffers for neural network (prevents OOM Native Crashes)
    private ByteBuffer faceBuffer;
    private int[] facePixels;

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
        
        setupToolbar();

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
                // x86 emulators have a known native SIGSEGV bug in TFLite when using multi-threading.
                int threads = android.os.Build.SUPPORTED_ABIS[0].contains("x86") ? 1 : 4;
                org.tensorflow.lite.support.model.Model.Options cpuOptions = new org.tensorflow.lite.support.model.Model.Options.Builder()
                        .setDevice(org.tensorflow.lite.support.model.Model.Device.CPU)
                        .setNumThreads(threads)
                        .build();
                model = Facenet.newInstance(this, cpuOptions);
            }
        } catch (IOException e) {
            Log.e("RecognitionActivity", "Model error", e);
        }

        loadEmbeddings();
        startRecognitionLoop();
        
        setupModeToggle();

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_CODE);
        }
    }

    private void setupModeToggle() {
        com.google.android.material.button.MaterialButtonToggleGroup toggleGroup = findViewById(R.id.toggleModeGroup);
        if ("OUT".equals(currentAttendanceType)) {
            toggleGroup.check(R.id.toggleModeOut);
        } else {
            toggleGroup.check(R.id.toggleModeIn);
        }
        
        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.toggleModeIn) {
                    currentAttendanceType = "IN";
                } else if (checkedId == R.id.toggleModeOut) {
                    currentAttendanceType = "OUT";
                }
                setupToolbar();
                recognizedTrackingIds.clear(); // Re-recognize to mark new type
                UIHelper.showSuccessSnackbar(findViewById(android.R.id.content), "Mode changed to: " + currentAttendanceType);
            }
        });
    }

    private void setupToolbar() {
        TextView toolbarTitle = findViewById(R.id.toolbarTitle);
        if (toolbarTitle != null) {
            toolbarTitle.setText("Live Attendance: " + currentAttendanceType);
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
                Preview preview = new Preview.Builder()
                        .setTargetAspectRatio(androidx.camera.core.AspectRatio.RATIO_16_9)
                        .build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetAspectRatio(androidx.camera.core.AspectRatio.RATIO_16_9)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
                    if (isProcessingFrame.get() || isActivityDestroyed || isFinishing()) {
                        imageProxy.close();
                        return;
                    }
                    isProcessingFrame.set(true);
                    
                    // FPS Tracking
                    frameCount++;
                    long currentMillis = System.currentTimeMillis();
                    if (currentMillis - lastFpsTime >= 1000) {
                        Log.d(PERF_TAG, "Camera & Tracking Loop FPS: " + frameCount);
                        frameCount = 0;
                        lastFpsTime = currentMillis;
                    }
                    
                    long startFrameTime = System.currentTimeMillis();
                    
                    @androidx.annotation.OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
                    android.media.Image mediaImage = imageProxy.getImage();
                    if (mediaImage != null) {
                        int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
                        InputImage image = InputImage.fromMediaImage(mediaImage, rotationDegrees);
                        
                        detector.process(image)
                            .addOnSuccessListener(cameraExecutor, faces -> {
                                if (!isActivityDestroyed && !isFinishing()) {
                                    handleFaces(faces, imageProxy, rotationDegrees);
                                }
                            })
                            .addOnFailureListener(cameraExecutor, e -> {})
                            .addOnCompleteListener(cameraExecutor, task -> {
                                long mlkitLatency = System.currentTimeMillis() - startFrameTime;
                                if (frameCount == 1) { // Log once per second
                                    Log.d(PERF_TAG, "MLKit Detection latency: " + mlkitLatency + "ms");
                                }
                                imageProxy.close();
                                isProcessingFrame.set(false);
                            });
                    } else {
                        imageProxy.close();
                        isProcessingFrame.set(false);
                    }
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

    private void handleFaces(List<Face> faces, androidx.camera.core.ImageProxy imageProxy, int rotationDegrees) {
        if (faces.isEmpty()) {
            runOnUiThread(() -> overlayView.setBoxes(new java.util.ArrayList<>()));
            return;
        }

        Bitmap rawBitmap = null;
        long currentTime = System.currentTimeMillis();
        
        int viewWidth = overlayView.getWidth();
        int viewHeight = overlayView.getHeight();
        
        int imageWidth = imageProxy.getWidth();
        int imageHeight = imageProxy.getHeight();
        if (rotationDegrees == 90 || rotationDegrees == 270) {
            imageWidth = imageProxy.getHeight();
            imageHeight = imageProxy.getWidth();
        }

        float scaleX = (float) viewWidth / imageWidth;
        float scaleY = (float) viewHeight / imageHeight;
        float scale = Math.max(scaleX, scaleY);
        float dx = (viewWidth - imageWidth * scale) / 2f;
        float dy = (viewHeight - imageHeight * scale) / 2f;

        boolean isFrontCamera = (currentCameraSelector == androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA);
        List<BoundingBoxOverlay.Box> mappedBoxes = new java.util.ArrayList<>();

        for (Face face : faces) {
            Rect bounds = face.getBoundingBox();
            Integer trackingId = face.getTrackingId();

            // 1. Map coordinates for drawing (done instantly for 60fps tracking)
            float mappedLeft = bounds.left;
            float mappedRight = bounds.right;
            if (isFrontCamera) {
                mappedLeft = imageWidth - bounds.right;
                mappedRight = imageWidth - bounds.left;
            }

            int leftScreen = (int) (mappedLeft * scale + dx);
            int topScreen = (int) (bounds.top * scale + dy);
            int rightScreen = (int) (mappedRight * scale + dx);
            int bottomScreen = (int) (bounds.bottom * scale + dy);
            Rect mappedRect = new Rect(leftScreen, topScreen, rightScreen, bottomScreen);

            // 2. Check tracking status
            String name = null;
            boolean recognized = false;
            boolean needsRecognition = false;
            
            if (trackingId != null) {
                if (recognizedTrackingIds.containsKey(trackingId)) {
                    name = recognizedTrackingIds.get(trackingId);
                    recognized = true;
                } else {
                    Long lastQueued = lastQueuedTime.get(trackingId);
                    // Try to re-recognize much faster (every 200ms instead of 1000ms) if they are "Unknown", 
                    // to quickly recover from motion blur. Max 2 items in queue to prevent CPU lock.
                    if ((lastQueued == null || currentTime - lastQueued > 200) && faceProcessingQueue.size() < 2) {
                        needsRecognition = true;
                        lastQueuedTime.put(trackingId, currentTime);
                    }
                }
            } else {
                needsRecognition = true; // Fallback if no tracking ID
            }

            // 3. Crop face for recognition ONLY if needed (avoids allocating Bitmaps 60x a second)
            if (needsRecognition) {
                if (rawBitmap == null) {
                    long startBitmapTime = System.currentTimeMillis();
                    rawBitmap = imageProxy.toBitmap();
                    Log.d(PERF_TAG, "Bitmap allocation took: " + (System.currentTimeMillis() - startBitmapTime) + "ms");
                }
                
                long startCropTime = System.currentTimeMillis();
                
                android.graphics.Matrix rawToUpright = new android.graphics.Matrix();
                rawToUpright.postRotate(rotationDegrees);
                android.graphics.RectF rawRect = new android.graphics.RectF(0, 0, rawBitmap.getWidth(), rawBitmap.getHeight());
                rawToUpright.mapRect(rawRect);
                rawToUpright.postTranslate(-rawRect.left, -rawRect.top);
                
                android.graphics.Matrix uprightToRaw = new android.graphics.Matrix();
                rawToUpright.invert(uprightToRaw);
                
                android.graphics.RectF faceRectUpright = new android.graphics.RectF(bounds);
                android.graphics.RectF faceRectRaw = new android.graphics.RectF();
                uprightToRaw.mapRect(faceRectRaw, faceRectUpright);
                
                int rawCropLeft = Math.max(0, (int) faceRectRaw.left);
                int rawCropTop = Math.max(0, (int) faceRectRaw.top);
                int rawCropRight = Math.min(rawBitmap.getWidth(), (int) faceRectRaw.right);
                int rawCropBottom = Math.min(rawBitmap.getHeight(), (int) faceRectRaw.bottom);
                int rWidth = rawCropRight - rawCropLeft;
                int rHeight = rawCropBottom - rawCropTop;
                
                if (rWidth > 0 && rHeight > 0) {
                    android.graphics.Matrix rotationMatrix = new android.graphics.Matrix();
                    rotationMatrix.postRotate(rotationDegrees);
                    Bitmap croppedAndRotated = Bitmap.createBitmap(rawBitmap, rawCropLeft, rawCropTop, rWidth, rHeight, rotationMatrix, true);
                    
                    faceProcessingQueue.add(new QueuedFace(croppedAndRotated, trackingId));
                    Log.d(PERF_TAG, "Cropping and rotating tiny face took: " + (System.currentTimeMillis() - startCropTime) + "ms. Queue size now: " + faceProcessingQueue.size());
                }
            }

            mappedBoxes.add(new BoundingBoxOverlay.Box(mappedRect, name, recognized, null));
        }

        if (rawBitmap != null && !rawBitmap.isRecycled()) {
            rawBitmap.recycle();
        }

        runOnUiThread(() -> overlayView.setBoxes(mappedBoxes));
    }

    private void startRecognitionLoop() {
        recognitionExecutor.execute(() -> {
            while (!Thread.interrupted() && !isActivityDestroyed) {
                QueuedFace qFace = faceProcessingQueue.poll();
                if (qFace != null) {
                    runOnUiThread(() -> {
                        if (isActivityDestroyed || isFinishing()) return;
                        processingCard.setVisibility(View.VISIBLE);
                    });
                    
                    long startRecognition = System.currentTimeMillis();
                    String name = recognizeFace(qFace.bitmap);
                    long recogTime = System.currentTimeMillis() - startRecognition;
                    Log.d(PERF_TAG, "FaceNet Recognition loop took: " + recogTime + "ms for result: " + name);
                    
                    if (!name.equals("Unknown")) {
                        if (qFace.trackingId != null) {
                            recognizedTrackingIds.put(qFace.trackingId, name);
                        }
                        markAttendance(name);
                    }
                    
                    // CRITICAL: Free up memory immediately after processing
                    if (qFace.bitmap != null && !qFace.bitmap.isRecycled()) {
                        qFace.bitmap.recycle();
                    }
                    
                    runOnUiThread(() -> {
                        if (isActivityDestroyed || isFinishing()) return;
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
        if (pfd == null) return null;
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

        if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            return null;
        }

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
                if (!matrix.isIdentity() && bitmap != null && bitmap.getWidth() > 0 && bitmap.getHeight() > 0) {
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

            // Lazy initialize reusable buffers to prevent massive memory leaks
            if (faceBuffer == null) {
                faceBuffer = ByteBuffer.allocateDirect(4 * 160 * 160 * 3);
                faceBuffer.order(ByteOrder.nativeOrder());
            }
            if (facePixels == null) {
                facePixels = new int[160 * 160];
            }
            
            faceBuffer.rewind(); // Reset buffer position to 0 for reuse
            resized.getPixels(facePixels, 0, 160, 0, 0, 160, 160);
            
            for (int val : facePixels) {
                // FaceNet standard normalization: (pixel - 127.5) / 127.5
                faceBuffer.putFloat((((val >> 16) & 0xFF) - 127.5f) / 127.5f);
                faceBuffer.putFloat((((val >> 8) & 0xFF) - 127.5f) / 127.5f);
                faceBuffer.putFloat(((val & 0xFF) - 127.5f) / 127.5f);
            }
            
            resized.recycle(); // Free temporary scaled bitmap

            TensorBuffer input = TensorBuffer.createFixedSize(new int[]{1, 160, 160, 3}, DataType.FLOAT32);
            input.loadBuffer(faceBuffer);
            Facenet.Outputs outputs = model.process(input);
            float[] emb = outputs.getOutputFeature0AsTensorBuffer().getFloatArray();
            
            // L2 Normalize
            float sum = 0;
            for (float v : emb) sum += v * v;
            float norm = (float) Math.sqrt(sum);
            if (norm > 0) for (int i = 0; i < emb.length; i++) emb[i] /= norm;

            return findMatch(emb);
        } catch (Throwable e) {
            Log.e("RecognitionActivity", "Error in recognizeFace", e);
            return "Unknown";
        }
    }

    private String findMatch(float[] embedding) {
        String name = "Unknown";
        float minDistance = 0.85f; // Relaxed threshold (was 0.75f)
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

    private static final Map<String, Long> globalLastMarkedTime = new ConcurrentHashMap<>();

    private void markAttendance(String name) {
        runOnUiThread(() -> {
            if (isActivityDestroyed || isFinishing()) return;
            String type = currentAttendanceType;
            long currentTime = System.currentTimeMillis();
            
            // Throttle based on SharedPreferences cooldown (in seconds)
            android.content.SharedPreferences prefs = getSharedPreferences(SettingsActivity.PREF_NAME, Context.MODE_PRIVATE);
            int cooldownSecs = prefs.getInt(SettingsActivity.KEY_COOLDOWN, 3);
            long cooldownMs = cooldownSecs * 1000L;
            
            Long lastMarked = globalLastMarkedTime.get(name);
            if (lastMarked != null && (currentTime - lastMarked < cooldownMs)) {
                return;
            }
            
            globalLastMarkedTime.put(name, currentTime);
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
                            if (mp != null) {
                                mp.start();
                                mp.setOnCompletionListener(MediaPlayer::release);
                            }
                        } catch (Exception e) {}
                    }
                    
                    // Silent UI feedback in the status text box
                    statusText.setText("✅ Marked " + type + ": " + name);
                });
            });
        });
    }

    private void loadEmbeddings() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            if (isActivityDestroyed) return;
            List<MemberEntity> members = AppDatabase.getDatabase(this).memberDao().getAllMembers();
            java.util.List<PersonEmbedding> list = new java.util.ArrayList<>();
            for (MemberEntity m : members) {
                list.add(new PersonEmbedding(m.name, m.embedding));
            }
            faceEmbeddingsList.clear();
            faceEmbeddingsList.addAll(list);
            
            // Count unique members for the UI
            java.util.HashSet<String> uniqueNames = new java.util.HashSet<>();
            for (PersonEmbedding p : list) uniqueNames.add(p.name);
            runOnUiThread(() -> {
                if (isActivityDestroyed || isFinishing()) return;
                statusText.setText("Registered members: " + uniqueNames.size() + " (" + list.size() + " faces)");
            });
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
        isActivityDestroyed = true;
        super.onDestroy();
        if (cameraExecutor != null) cameraExecutor.shutdown();
        if (recognitionExecutor != null) recognitionExecutor.shutdownNow();
        if (model != null) model.close();
        if (detector != null) detector.close();
    }
}
