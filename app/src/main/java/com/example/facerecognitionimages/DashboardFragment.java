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
        


        view.findViewById(R.id.btnSettings).setOnClickListener(v -> {
            if (com.example.facerecognitionimages.utils.ClickUtils.isFastDoubleClick()) return;
            startActivity(new Intent(requireContext(), SettingsActivity.class));
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
