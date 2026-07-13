package com.example.facerecognitionimages;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Button;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.content.Intent;
import android.content.Context;
import android.net.Uri;
import androidx.core.content.FileProvider;
import com.example.facerecognitionimages.utils.UIHelper;
import androidx.appcompat.app.AlertDialog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.widget.SearchView;

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
    private android.widget.LinearLayout emptyState;
    private TextView tvCurrentDate;
    private com.facebook.shimmer.ShimmerFrameLayout shimmerLayout;
    private SwipeRefreshLayout swipeRefreshLayout;
    private SearchView searchView;
    private List<String> logList = new ArrayList<>();
    private List<String> filteredList = new ArrayList<>();
    private String currentDateFilter = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_logs, container, false);
        
        recyclerView = view.findViewById(R.id.logsRecyclerView);
        emptyState = view.findViewById(R.id.emptyState);
        tvCurrentDate = view.findViewById(R.id.tvCurrentDate);
        shimmerLayout = view.findViewById(R.id.shimmerLayout);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        searchView = view.findViewById(R.id.searchView);
        
        swipeRefreshLayout.setOnRefreshListener(this::loadLogs);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new LogAdapter(filteredList);
        recyclerView.setAdapter(adapter);
        
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filterList(query);
                return true;
            }
            @Override
            public boolean onQueryTextChange(String newText) {
                filterList(newText);
                return true;
            }
        });
        
        view.findViewById(R.id.btnClearLogs).setOnClickListener(v -> clearLogs());
        view.findViewById(R.id.btnFilterDate).setOnClickListener(v -> showDatePicker());
        view.findViewById(R.id.btnExport).setOnClickListener(v -> exportCsv());
        
        tvCurrentDate.setOnClickListener(v -> {
            currentDateFilter = null;
            tvCurrentDate.setText("All Time");
            loadLogs();
        });

        view.findViewById(R.id.fabManualEntry).setOnClickListener(v -> showManualEntryDialog());

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadLogs();
    }

    private void loadLogs() {
        if (getView() == null) return;
        if (shimmerLayout != null) {
            shimmerLayout.startShimmer();
            shimmerLayout.setVisibility(View.VISIBLE);
        }
        recyclerView.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
        
        Context context = getContext();
        if (context == null) return;

        AppDatabase.databaseWriteExecutor.execute(() -> {
            // Simulate brief delay for shimmer effect
            try { Thread.sleep(500); } catch (InterruptedException e) {}
            
            if (getActivity() == null) return;
            
            List<LogEntity> logs;
            if (currentDateFilter == null) {
                logs = AppDatabase.getDatabase(context).logDao().getAllLogs();
            } else {
                logs = AppDatabase.getDatabase(context).logDao().getLogsByDate(currentDateFilter);
            }
            
            List<String> formattedLogs = new ArrayList<>();
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            
            for (LogEntity log : logs) {
                String typeStr = log.type;
                if ("OUT".equals(log.type)) {
                    List<LogEntity> userLogs = AppDatabase.getDatabase(context).logDao()
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
                    if (getView() == null) return;
                    if (swipeRefreshLayout != null) {
                        swipeRefreshLayout.setRefreshing(false);
                    }
                    if (shimmerLayout != null) {
                        shimmerLayout.stopShimmer();
                        shimmerLayout.setVisibility(View.GONE);
                    }
                    logList.clear();
                    logList.addAll(formattedLogs);
                    filterList(searchView.getQuery().toString());
                });
            }
        });
    }

    private void filterList(String query) {
        filteredList.clear();
        if (query == null || query.trim().isEmpty()) {
            filteredList.addAll(logList);
        } else {
            String lowerQuery = query.toLowerCase();
            for (String log : logList) {
                if (log.toLowerCase().contains(lowerQuery)) {
                    filteredList.add(log);
                }
            }
        }
        
        if (filteredList.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            adapter.notifyDataSetChanged();
            emptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }
    
    private void clearLogs() {
        Context context = getContext();
        if (context == null) return;
        
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
            .setTitle("Clear All Logs")
            .setMessage("Are you sure you want to permanently delete all attendance logs? This action cannot be undone.")
            .setPositiveButton("Clear", (dialog, which) -> {
                // Clear old txt file if exists
                File file = new File(context.getExternalFilesDir(null), "attendance_logs.txt");
                if (file.exists()) {
                    file.delete();
                }
                
                AppDatabase.databaseWriteExecutor.execute(() -> {
                    AppDatabase.getDatabase(context).logDao().deleteAllLogs();
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            loadLogs();
                            UIHelper.showSuccessSnackbar(requireView(), "All logs cleared");
                        });
                    }
                });
            })
            .setNegativeButton("Cancel", null)
            .show();
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
            UIHelper.showErrorSnackbar(requireView(), "No logs to export");
            return;
        }
        try {
            Context context = requireContext();
            File cacheDir = new File(context.getCacheDir(), "logs");
            if (!cacheDir.exists()) cacheDir.mkdirs();
            File file = new File(cacheDir, "Attendance_Export_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".csv");
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
            
            Uri csvUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
            
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/csv");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Attendance Logs");
            shareIntent.putExtra(Intent.EXTRA_STREAM, csvUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            startActivity(Intent.createChooser(shareIntent, "Share Logs via"));
        } catch (Exception e) {
            UIHelper.showErrorSnackbar(requireView(), "Export failed: " + e.getMessage());
        }
    }

    private void showManualEntryDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_manual_entry, null);
        builder.setView(dialogView);
        
        EditText etName = dialogView.findViewById(R.id.etName);
        EditText etDate = dialogView.findViewById(R.id.etDate);
        EditText etTime = dialogView.findViewById(R.id.etTime);
        RadioGroup rgType = dialogView.findViewById(R.id.rgType);
        
        // Default to today and now
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        etDate.setText(today);
        etTime.setText(time);
        
        builder.setPositiveButton("Save", (dialog, which) -> {
            String name = etName.getText().toString().trim();
            String date = etDate.getText().toString().trim();
            String t = etTime.getText().toString().trim();
            String type = rgType.getCheckedRadioButtonId() == R.id.rbIn ? "IN" : "OUT";
            
            if (name.isEmpty() || date.isEmpty() || t.isEmpty()) {
                Toast.makeText(requireContext(), "All fields are required", Toast.LENGTH_SHORT).show();
                return;
            }
            
            LogEntity log = new LogEntity();
            log.name = name;
            log.date = date;
            log.time = t;
            log.type = type;
            
            Context context = getContext();
            if (context == null) return;
            
            AppDatabase.databaseWriteExecutor.execute(() -> {
                AppDatabase.getDatabase(context).logDao().insertLog(log);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        loadLogs();
                        androidx.appcompat.app.AlertDialog dialogToDismiss = null;
                        if (dialog instanceof androidx.appcompat.app.AlertDialog) dialogToDismiss = (androidx.appcompat.app.AlertDialog) dialog;
                        if (dialogToDismiss != null) dialogToDismiss.dismiss();
                        Toast.makeText(requireContext(), "Manual entry added", Toast.LENGTH_SHORT).show();
                    });
                }
            });
        });
        
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }
}
