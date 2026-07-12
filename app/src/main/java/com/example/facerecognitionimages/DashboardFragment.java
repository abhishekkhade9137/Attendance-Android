package com.example.facerecognitionimages;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.facerecognitionimages.db.AppDatabase;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DashboardFragment extends Fragment {

    private TextView countRegisteredTv, countTodayTv;
    private Button btnOpenScanner;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);
        
        countRegisteredTv = view.findViewById(R.id.countRegistered);
        countTodayTv = view.findViewById(R.id.countToday);
        btnOpenScanner = view.findViewById(R.id.btnOpenScanner);
        
        btnOpenScanner.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), RecognitionActivity.class));
        });
        
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateStats();
    }

    private void updateStats() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            int registeredCount = AppDatabase.getDatabase(requireContext()).memberDao().getMemberCount();
            
            String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            int todayCount = AppDatabase.getDatabase(requireContext()).logDao().getLogCountByDate(todayDate);
            
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    countRegisteredTv.setText(String.valueOf(registeredCount));
                    countTodayTv.setText(String.valueOf(todayCount));
                });
            }
        });
    }
}
