package com.example.facerecognitionimages;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.facerecognitionimages.db.AppDatabase;
import com.example.facerecognitionimages.db.LogEntity;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LogsFragment extends Fragment {

    private RecyclerView recyclerView;
    private LogAdapter adapter;
    private TextView emptyState, tvCurrentDate;
    private List<String> logList = new ArrayList<>();
    private String currentDateFilter = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_logs, container, false);
        
        recyclerView = view.findViewById(R.id.logsRecyclerView);
        emptyState = view.findViewById(R.id.emptyState);
        tvCurrentDate = view.findViewById(R.id.tvCurrentDate);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new LogAdapter(logList);
        recyclerView.setAdapter(adapter);
        
        view.findViewById(R.id.btnClearLogs).setOnClickListener(v -> clearLogs());
        view.findViewById(R.id.btnFilterDate).setOnClickListener(v -> showDatePicker());
        view.findViewById(R.id.btnExport).setOnClickListener(v -> exportCsv());
        
        tvCurrentDate.setOnClickListener(v -> {
            currentDateFilter = null;
            tvCurrentDate.setText("All Time");
            loadLogs();
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadLogs();
    }

    private void loadLogs() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<LogEntity> logs;
            if (currentDateFilter == null) {
                logs = AppDatabase.getDatabase(requireContext()).logDao().getAllLogs();
            } else {
                logs = AppDatabase.getDatabase(requireContext()).logDao().getLogsByDate(currentDateFilter);
            }
            
            List<String> formattedLogs = new ArrayList<>();
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            
            for (LogEntity log : logs) {
                String typeStr = log.type;
                if ("OUT".equals(log.type)) {
                    List<LogEntity> userLogs = AppDatabase.getDatabase(requireContext()).logDao()
                        .getLogsForUserOnDate(log.name, log.date);
                    LogEntity firstIn = null;
                    for (LogEntity uLog : userLogs) {
                        if ("IN".equals(uLog.type) && uLog.time.compareTo(log.time) < 0) {
                            firstIn = uLog;
                            break;
                        }
                    }
                    if (firstIn != null) {
                        try {
                            Date inTime = timeFormat.parse(firstIn.time);
                            Date outTime = timeFormat.parse(log.time);
                            long diffMs = outTime.getTime() - inTime.getTime();
                            long hours = diffMs / (1000 * 60 * 60);
                            long mins = (diffMs / (1000 * 60)) % 60;
                            typeStr = String.format(Locale.getDefault(), "OUT (%dh %dm)", hours, mins);
                        } catch (Exception e) {}
                    }
                }
                
                formattedLogs.add(log.name + "|" + log.date + "|" + log.time + "|" + typeStr);
            }
            
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    logList.clear();
                    logList.addAll(formattedLogs);
                    if (logList.isEmpty()) {
                        emptyState.setVisibility(View.VISIBLE);
                        recyclerView.setVisibility(View.GONE);
                    } else {
                        adapter.notifyDataSetChanged();
                        emptyState.setVisibility(View.GONE);
                        recyclerView.setVisibility(View.VISIBLE);
                    }
                });
            }
        });
    }
    
    private void clearLogs() {
        // Clear old txt file if exists
        File logFile = new File(requireContext().getFilesDir(), "attendance_log.txt");
        if(logFile.exists()) {
            logFile.delete();
        }
        
        // Clear DB
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase.getDatabase(requireContext()).clearAllTables();
            loadLogs();
        });
    }

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(requireContext(), (view, year, month, dayOfMonth) -> {
            currentDateFilter = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth);
            tvCurrentDate.setText(currentDateFilter);
            loadLogs();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void exportCsv() {
        if (logList.isEmpty()) {
            Toast.makeText(requireContext(), "No logs to export", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File file = new File(dir, "Attendance_Export_" + System.currentTimeMillis() + ".csv");
            FileWriter writer = new FileWriter(file);
            writer.append("Name,Date,Time,Type\n");
            for (String log : logList) {
                String[] parts = log.split("\\|");
                if (parts.length >= 4) {
                    writer.append(parts[0]).append(",")
                          .append(parts[1]).append(",")
                          .append(parts[2]).append(",")
                          .append(parts[3]).append("\n");
                }
            }
            writer.flush();
            writer.close();
            Toast.makeText(requireContext(), "Exported to Downloads!", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Export failed", Toast.LENGTH_SHORT).show();
        }
    }
}
