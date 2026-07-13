package com.example.facerecognitionimages;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.content.Context;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.exifinterface.media.ExifInterface;

import com.example.facerecognitionimages.db.AppDatabase;
import com.example.facerecognitionimages.db.LogEntity;
import com.example.facerecognitionimages.db.MemberEntity;
import com.example.facerecognitionimages.ml.Facenet;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class GroupPhotoActivity extends AppCompatActivity {

    private ImageView imageView;
    private BoundingBoxOverlay overlayView;
    private TextView statusText;
    private Button btnSubmit;
    private CardView processingCard;

    private FaceDetector detector;
    private Facenet model;
    private ExecutorService executor;

    private String scanMode = "IN";
    private Uri imageUri;
    private Bitmap currentBitmap;

    private List<MemberEntity> allMembers = new ArrayList<>();
    private List<String> uniqueMemberNames = new ArrayList<>();
    private List<BoundingBoxOverlay.Box> detectedBoxes = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_photo);

        imageView = findViewById(R.id.imageView);
        overlayView = findViewById(R.id.overlayView);
        statusText = findViewById(R.id.statusText);
        btnSubmit = findViewById(R.id.btnSubmit);
        processingCard = findViewById(R.id.processingCard);
        
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        scanMode = getIntent().getStringExtra("SCAN_MODE");
        if (scanMode == null) scanMode = "IN";
        
        String uriStr = getIntent().getStringExtra("IMAGE_URI");
        if (uriStr != null) {
            imageUri = Uri.parse(uriStr);
        } else {
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(this, "No image provided", Toast.LENGTH_SHORT).show();
            });
            finish();
            return;
        }

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .build();
        detector = FaceDetection.getClient(options);

        try {
            try {
                org.tensorflow.lite.support.model.Model.Options tfOptions = new org.tensorflow.lite.support.model.Model.Options.Builder()
                        .setDevice(org.tensorflow.lite.support.model.Model.Device.GPU)
                        .build();
                model = Facenet.newInstance(this, tfOptions);
            } catch (Exception e) {
                Log.e("GroupPhoto", "GPU acceleration failed, falling back to CPU", e);
                model = Facenet.newInstance(this);
            }
        } catch (IOException e) {
            Log.e("GroupPhoto", "Model error", e);
        }

        executor = Executors.newSingleThreadExecutor();

        overlayView.setOnBoxClickListener((box, index) -> {
            if (!box.recognized) {
                showTaggingDialog(box, index);
            } else {
                // Allow overriding even if recognized
                showTaggingDialog(box, index);
            }
        });

        btnSubmit.setOnClickListener(v -> submitAttendance());

        loadDataAndProcess();
    }

    private void loadDataAndProcess() {
        processingCard.setVisibility(View.VISIBLE);
        AppDatabase.databaseWriteExecutor.execute(() -> {
            allMembers = AppDatabase.getDatabase(this).memberDao().getAllMembers();
            uniqueMemberNames.clear();
            for (MemberEntity m : allMembers) {
                if (!uniqueMemberNames.contains(m.name)) {
                    uniqueMemberNames.add(m.name);
                }
            }
            java.util.Collections.sort(uniqueMemberNames);

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                try {
                    currentBitmap = getBitmapFromUri(imageUri);
                    if (currentBitmap != null) {
                        imageView.setImageBitmap(currentBitmap);
                        // Start Face Detection
                        InputImage image = InputImage.fromBitmap(currentBitmap, 0);
                        detector.process(image)
                                .addOnSuccessListener(faces -> processFaces(faces))
                                .addOnFailureListener(e -> {
                                    processingCard.setVisibility(View.GONE);
                                    statusText.setText("Face detection failed: " + e.getMessage());
                                });
                    } else {
                        processingCard.setVisibility(View.GONE);
                        statusText.setText("Failed to load image.");
                    }
                } catch (Exception e) {
                    processingCard.setVisibility(View.GONE);
                    statusText.setText("Error: " + e.getMessage());
                }
            });
        });
    }

    private void processFaces(List<Face> faces) {
        if (faces.isEmpty()) {
            processingCard.setVisibility(View.GONE);
            statusText.setText("No faces detected in the image.");
            return;
        }
        
        statusText.setText("Recognizing " + faces.size() + " faces...");
        detectedBoxes.clear();

        executor.execute(() -> {
            for (Face face : faces) {
                Rect bounds = face.getBoundingBox();
                
                // Adjust bounds for aspect ratio difference between bitmap and imageView
                // Since we use scaleType=fitCenter, we need to map the bitmap coordinates to the ImageView's displayed rect
                // We will do this mapping dynamically in the BoundingBoxOverlay or map it here.
                // It's easier to map it here before creating the Box.
                
                float[] emb = getEmbedding(currentBitmap, bounds);
                String label = "";
                boolean recognized = false;
                
                if (emb != null) {
                    float bestScore = -1f;
                    String bestName = "";
                    for (MemberEntity m : allMembers) {
                        float score = cosineSimilarity(emb, m.embedding);
                        if (score > bestScore) {
                            bestScore = score;
                            bestName = m.name;
                        }
                    }
                    if (bestScore > 0.75f) { // Stricter threshold for siblings (was 0.6f)
                        label = bestName;
                        recognized = true;
                    }
                }
                
                // We must store the original bitmap bounds, then map them later to screen
                detectedBoxes.add(new BoundingBoxOverlay.Box(bounds, label, recognized, emb));
            }

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                processingCard.setVisibility(View.GONE);
                mapBoxesToScreenAndDraw();
                
                long recCount = detectedBoxes.stream().filter(b -> b.recognized).count();
                statusText.setText("Detected " + faces.size() + " faces. Recognized " + recCount + ". Tap red boxes to tag manually.");
                btnSubmit.setEnabled(true);
            });
        });
    }
    
    private void mapBoxesToScreenAndDraw() {
        if (currentBitmap == null || imageView.getWidth() == 0) return;
        
        float vWidth = imageView.getWidth();
        float vHeight = imageView.getHeight();
        float bWidth = currentBitmap.getWidth();
        float bHeight = currentBitmap.getHeight();
        
        float scale = Math.min(vWidth / bWidth, vHeight / bHeight);
        float dx = (vWidth - bWidth * scale) / 2f;
        float dy = (vHeight - bHeight * scale) / 2f;
        
        List<BoundingBoxOverlay.Box> screenBoxes = new ArrayList<>();
        for (BoundingBoxOverlay.Box box : detectedBoxes) {
            Rect r = box.rect;
            int left = (int) (r.left * scale + dx);
            int top = (int) (r.top * scale + dy);
            int right = (int) (r.right * scale + dx);
            int bottom = (int) (r.bottom * scale + dy);
            screenBoxes.add(new BoundingBoxOverlay.Box(new Rect(left, top, right, bottom), box.label, box.recognized, box.embedding));
        }
        
        overlayView.setBoxes(screenBoxes);
    }
    
    // We need to re-map when layout happens if dimensions change
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            mapBoxesToScreenAndDraw();
        }
    }

    private void showTaggingDialog(BoundingBoxOverlay.Box screenBox, int screenIndex) {
        AlertDialog.Builder optionsBuilder = new AlertDialog.Builder(this);
        optionsBuilder.setTitle("Face Options");
        String[] options = {"Change Person", "Remove / Ignore Face"};
        
        optionsBuilder.setItems(options, (dialogInterface, i) -> {
            BoundingBoxOverlay.Box originalBox = detectedBoxes.get(screenIndex);
            if (i == 0) {
                // Change Person
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Tag Person");
                ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, uniqueMemberNames);
                builder.setAdapter(adapter, (dialog, which) -> {
                    String selectedName = uniqueMemberNames.get(which);
                    originalBox.label = selectedName;
                    originalBox.recognized = true;
                    
                    // Learn from the mistake
                    if (originalBox.embedding != null) {
                        AppDatabase.databaseWriteExecutor.execute(() -> {
                            com.example.facerecognitionimages.utils.SmartFaceManager.saveFaceSmartly(this, selectedName, originalBox.embedding);
                        });
                        Toast.makeText(this, "Learned face for " + selectedName, Toast.LENGTH_SHORT).show();
                    }
                    
                    mapBoxesToScreenAndDraw();
                    updateStatusText();
                });
                builder.setNegativeButton("Cancel", null);
                builder.show();
            } else if (i == 1) {
                // Remove / Ignore Face
                originalBox.label = "";
                originalBox.recognized = false;
                mapBoxesToScreenAndDraw();
                updateStatusText();
            }
        });
        optionsBuilder.show();
    }

    private void updateStatusText() {
        long recCount = detectedBoxes.stream().filter(b -> b.recognized).count();
        statusText.setText("Detected " + detectedBoxes.size() + " faces. Recognized " + recCount + ". Tap red boxes to tag manually.");
    }

    private void submitAttendance() {
        processingCard.setVisibility(View.VISIBLE);
        btnSubmit.setEnabled(false);
        
        List<String> toMark = new ArrayList<>();
        for (BoundingBoxOverlay.Box b : detectedBoxes) {
            if (b.recognized && b.label != null && !b.label.isEmpty()) {
                if (!toMark.contains(b.label)) {
                    toMark.add(b.label);
                }
            }
        }
        
        if (toMark.isEmpty()) {
            processingCard.setVisibility(View.GONE);
            Toast.makeText(this, "No one is tagged!", Toast.LENGTH_SHORT).show();
            btnSubmit.setEnabled(true);
            return;
        }

        AppDatabase.databaseWriteExecutor.execute(() -> {
            String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            String timeStr = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            
            for (String name : toMark) {
                LogEntity log = new LogEntity();
                log.name = name;
                log.type = scanMode;
                log.date = dateStr;
                log.time = timeStr;
                AppDatabase.getDatabase(this).logDao().insertLog(log);
            }
            
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                processingCard.setVisibility(View.GONE);
                
                android.content.SharedPreferences prefs = getSharedPreferences(SettingsActivity.PREF_NAME, Context.MODE_PRIVATE);
                boolean hapticsEnabled = prefs.getBoolean(SettingsActivity.KEY_HAPTICS, true);
                
                if (hapticsEnabled) {
                    // Haptic Feedback
                    Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                    if (vibrator != null && vibrator.hasVibrator()) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK));
                        } else {
                            vibrator.vibrate(50);
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

                Toast.makeText(this, "Marked " + toMark.size() + " people as " + scanMode + "!", Toast.LENGTH_LONG).show();
                finish();
            });
        });
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
            e.printStackTrace();
            return null;
        }
    }

    private float cosineSimilarity(float[] emb1, float[] emb2) {
        if (emb1 == null || emb2 == null || emb1.length != emb2.length) return 0f;
        float dotProduct = 0f;
        for (int i = 0; i < emb1.length; i++) {
            dotProduct += emb1[i] * emb2[i];
        }
        return dotProduct;
    }

    private Bitmap getBitmapFromUri(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, options);
            if (is != null) is.close();

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

            is = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(is, null, options);
            if (is != null) is.close();

            is = getContentResolver().openInputStream(uri);
            ExifInterface exif = new ExifInterface(is);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (is != null) is.close();

            Matrix matrix = new Matrix();
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90) matrix.postRotate(90);
            else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) matrix.postRotate(180);
            else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) matrix.postRotate(270);

            if (matrix.isIdentity()) {
                return bitmap;
            }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
