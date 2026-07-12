package com.example.facerecognitionimages;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LogsActivity extends AppCompatActivity {

    RecyclerView recyclerView;
    LogAdapter adapter;
    TextView emptyState;
    List<String> logList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logs);

        recyclerView = findViewById(R.id.logsRecyclerView);
        emptyState = findViewById(R.id.emptyState);
        
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        loadLogs();
        
        adapter = new LogAdapter(logList);
        recyclerView.setAdapter(adapter);
    }

    private void loadLogs() {
        File logFile = new File(getFilesDir(), "attendance_log.txt");
        if (logFile.exists()) {
            try {
                FileInputStream fis = openFileInput("attendance_log.txt");
                BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        logList.add(line);
                    }
                }
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        if (logList.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            Collections.reverse(logList); // Show latest first
            emptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }
}