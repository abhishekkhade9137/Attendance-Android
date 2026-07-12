package com.example.facerecognitionimages;

import android.content.Intent;
import android.graphics.Color;
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
import com.example.facerecognitionimages.db.LogEntity;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DashboardFragment extends Fragment {

    private TextView countRegisteredTv, countTodayTv;
    private Button btnScanIn, btnScanOut;
    private BarChart barChart;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);
        
        countRegisteredTv = view.findViewById(R.id.countRegistered);
        countTodayTv = view.findViewById(R.id.countToday);
        btnScanIn = view.findViewById(R.id.btnScanIn);
        btnScanOut = view.findViewById(R.id.btnScanOut);
        barChart = view.findViewById(R.id.barChart);
        
        btnScanIn.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), RecognitionActivity.class);
            intent.putExtra("SCAN_MODE", "IN");
            startActivity(intent);
        });

        btnScanOut.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), RecognitionActivity.class);
            intent.putExtra("SCAN_MODE", "OUT");
            startActivity(intent);
        });
        
        setupChart();
        
        return view;
    }

    private void setupChart() {
        barChart.getDescription().setEnabled(false);
        barChart.setDrawGridBackground(false);
        barChart.getAxisRight().setEnabled(false);
        barChart.getAxisLeft().setGranularity(1f);
        barChart.getAxisLeft().setAxisMinimum(0f);
        
        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(new String[]{"IN", "OUT"}));
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
            List<LogEntity> todayLogs = AppDatabase.getDatabase(requireContext()).logDao().getLogsByDate(todayDate);
            
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
                    countRegisteredTv.setText(String.valueOf(registeredCount));
                    countTodayTv.setText(String.valueOf(todayCount));
                    
                    List<BarEntry> entries = new ArrayList<>();
                    entries.add(new BarEntry(0f, finalInCount));
                    entries.add(new BarEntry(1f, finalOutCount));
                    
                    BarDataSet dataSet = new BarDataSet(entries, "Today's Attendance");
                    dataSet.setColor(Color.BLACK);
                    dataSet.setValueTextSize(12f);
                    
                    BarData barData = new BarData(dataSet);
                    barData.setBarWidth(0.5f);
                    
                    barChart.setData(barData);
                    barChart.invalidate();
                });
            }
        });
    }
}
