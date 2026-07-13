package com.example.facerecognitionimages;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.net.Uri;
import android.widget.Toast;
import android.content.Context;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.facerecognitionimages.db.AppDatabase;
import com.example.facerecognitionimages.db.LogEntity;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DashboardFragment extends Fragment {

    private TextView countRegisteredTv, countTodayTv, countInTv, countOutTv;
    private Button btnScanIn, btnScanOut;
    
    private final ActivityResultLauncher<Intent> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        Toast.makeText(requireContext(), "Exporting...", Toast.LENGTH_SHORT).show();
                        com.example.facerecognitionimages.utils.BackupUtils.exportBackup(requireContext(), uri, new com.example.facerecognitionimages.utils.BackupUtils.BackupCallback() {
                            @Override
                            public void onSuccess() {
                                Context ctx = getContext();
                                if (ctx != null && getActivity() != null) {
                                    getActivity().runOnUiThread(() -> Toast.makeText(ctx, "Backup exported successfully!", Toast.LENGTH_LONG).show());
                                }
                            }
                            @Override
                            public void onError(String message) {
                                Context ctx = getContext();
                                if (ctx != null && getActivity() != null) {
                                    getActivity().runOnUiThread(() -> Toast.makeText(ctx, "Export failed: " + message, Toast.LENGTH_LONG).show());
                                }
                            }
                        });
                    }
                }
            });

    private final ActivityResultLauncher<Intent> importLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        Toast.makeText(requireContext(), "Importing...", Toast.LENGTH_SHORT).show();
                        com.example.facerecognitionimages.utils.BackupUtils.importBackup(requireContext(), uri, new com.example.facerecognitionimages.utils.BackupUtils.BackupCallback() {
                            @Override
                            public void onSuccess() {
                                Context ctx = getContext();
                                if (ctx != null && getActivity() != null) {
                                    getActivity().runOnUiThread(() -> {
                                        Toast.makeText(ctx, "Backup imported successfully!", Toast.LENGTH_LONG).show();
                                        updateStats(); // Refresh dashboard
                                    });
                                }
                            }
                            @Override
                            public void onError(String message) {
                                Context ctx = getContext();
                                if (ctx != null && getActivity() != null) {
                                    getActivity().runOnUiThread(() -> Toast.makeText(ctx, "Import failed: " + message, Toast.LENGTH_LONG).show());
                                }
                            }
                        });
                    }
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);
        
        countRegisteredTv = view.findViewById(R.id.countRegistered);
        countTodayTv = view.findViewById(R.id.countToday);
        countInTv = view.findViewById(R.id.countIn);
        countOutTv = view.findViewById(R.id.countOut);
        
        btnScanIn = view.findViewById(R.id.btnScanIn);
        btnScanOut = view.findViewById(R.id.btnScanOut);
        
        btnScanIn.setOnClickListener(v -> {
            if (com.example.facerecognitionimages.utils.ClickUtils.isFastDoubleClick()) return;
            Intent intent = new Intent(requireContext(), RecognitionActivity.class);
            intent.putExtra("SCAN_MODE", "IN");
            startActivity(intent);
        });

        btnScanOut.setOnClickListener(v -> {
            if (com.example.facerecognitionimages.utils.ClickUtils.isFastDoubleClick()) return;
            Intent intent = new Intent(requireContext(), RecognitionActivity.class);
            intent.putExtra("SCAN_MODE", "OUT");
            startActivity(intent);
        });
        
        view.findViewById(R.id.btnBackup).setOnClickListener(v -> {
            if (com.example.facerecognitionimages.utils.ClickUtils.isFastDoubleClick()) return;
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/zip");
            intent.putExtra(Intent.EXTRA_TITLE, "facerecognition_backup.zip");
            exportLauncher.launch(intent);
        });
        
        view.findViewById(R.id.btnRestore).setOnClickListener(v -> {
            if (com.example.facerecognitionimages.utils.ClickUtils.isFastDoubleClick()) return;
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/zip");
            importLauncher.launch(intent);
        });
        
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateStats();
    }

    private void updateStats() {
        Context context = getContext();
        if (context == null) return;
        
        AppDatabase.databaseWriteExecutor.execute(() -> {
            int registeredCount = AppDatabase.getDatabase(context).memberDao().getUniqueMemberCount();
            
            String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            List<LogEntity> todayLogs = AppDatabase.getDatabase(context).logDao().getLogsByDate(todayDate);
            
            int inCount = 0;
            int outCount = 0;
            for (LogEntity log : todayLogs) {
                if ("IN".equals(log.type)) inCount++;
                else outCount++;
            }
            
            int todayCount = todayLogs.size();
            
            if (getActivity() != null) {
                int finalInCount = inCount;
                int finalOutCount = outCount;
                getActivity().runOnUiThread(() -> {
                    if (getView() == null) return;
                    countRegisteredTv.setText(String.valueOf(registeredCount));
                    countTodayTv.setText(String.valueOf(todayCount));
                    countInTv.setText(String.valueOf(finalInCount));
                    countOutTv.setText(String.valueOf(finalOutCount));
                });
            }
        });
    }
}
