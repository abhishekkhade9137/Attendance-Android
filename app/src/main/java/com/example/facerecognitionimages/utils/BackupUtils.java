package com.example.facerecognitionimages.utils;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.facerecognitionimages.db.AppDatabase;
import com.example.facerecognitionimages.db.MemberEntity;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class BackupUtils {
    private static final String TAG = "BackupUtils";
    private static final String JSON_FILENAME = "members.json";

    public interface BackupCallback {
        void onSuccess();
        void onError(String message);
    }

    public static void exportBackup(Context context, Uri destUri, BackupCallback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                // 1. Get data
                List<MemberEntity> members = AppDatabase.getDatabase(context).memberDao().getAllMembers();
                String json = new Gson().toJson(members);

                // 2. Open ZipOutputStream
                try (OutputStream os = context.getContentResolver().openOutputStream(destUri);
                     ZipOutputStream zos = new ZipOutputStream(os)) {
                    
                    // Write JSON
                    ZipEntry jsonEntry = new ZipEntry(JSON_FILENAME);
                    zos.putNextEntry(jsonEntry);
                    zos.write(json.getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();

                    // Write Images
                    File dir = context.getFilesDir();
                    File[] files = dir.listFiles((d, name) -> name.endsWith("_face.png"));
                    if (files != null) {
                        for (File file : files) {
                            ZipEntry imgEntry = new ZipEntry(file.getName());
                            zos.putNextEntry(imgEntry);
                            try (FileInputStream fis = new FileInputStream(file)) {
                                byte[] buffer = new byte[1024];
                                int len;
                                while ((len = fis.read(buffer)) > 0) {
                                    zos.write(buffer, 0, len);
                                }
                            }
                            zos.closeEntry();
                        }
                    }
                }
                
                if (callback != null) {
                    mainHandler.post(callback::onSuccess);
                }
            } catch (Exception e) {
                Log.e(TAG, "Export failed", e);
                if (callback != null) {
                    final String msg = e.getMessage();
                    mainHandler.post(() -> callback.onError(msg));
                }
            }
        });
    }

    public static void importBackup(Context context, Uri sourceUri, BackupCallback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                File destinationDir = context.getFilesDir();
                String canonicalDest = destinationDir.getCanonicalPath();
                if (!canonicalDest.endsWith(File.separator)) {
                    canonicalDest += File.separator;
                }
                try (InputStream is = context.getContentResolver().openInputStream(sourceUri);
                     ZipInputStream zis = new ZipInputStream(is)) {
                     
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        File outFile = new File(destinationDir, entry.getName());
                        String canonicalOut = outFile.getCanonicalPath();
                        if (!canonicalOut.startsWith(canonicalDest) && !canonicalOut.equals(destinationDir.getCanonicalPath())) {
                            throw new IOException("Zip entry is outside target directory: " + entry.getName());
                        }

                        if (entry.getName().equals(JSON_FILENAME)) {
                            // Read JSON using ByteArrayOutputStream to safely handle multi-byte UTF-8 boundaries
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            byte[] buffer = new byte[1024];
                            int len;
                            while ((len = zis.read(buffer)) > 0) {
                                baos.write(buffer, 0, len);
                            }
                            String jsonStr = baos.toString(StandardCharsets.UTF_8.name());
                            
                            List<MemberEntity> members = new Gson().fromJson(jsonStr,
                                    new TypeToken<List<MemberEntity>>(){}.getType());
                                    
                            if (members != null) {
                                for (MemberEntity m : members) {
                                    m.id = 0; // Reset ID for autogenerate
                                    AppDatabase.getDatabase(context).memberDao().insertMember(m);
                                }
                            }
                        } else if (entry.getName().endsWith("_face.png")) {
                            // Extract Image
                            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                                byte[] buffer = new byte[1024];
                                int len;
                                while ((len = zis.read(buffer)) > 0) {
                                    fos.write(buffer, 0, len);
                                }
                            }
                        }
                        zis.closeEntry();
                    }
                }
                if (callback != null) {
                    mainHandler.post(callback::onSuccess);
                }
            } catch (Exception e) {
                Log.e(TAG, "Import failed", e);
                if (callback != null) {
                    final String msg = e.getMessage();
                    mainHandler.post(() -> callback.onError(msg));
                }
            }
        });
    }
}
