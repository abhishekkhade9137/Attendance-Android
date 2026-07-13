package com.example.facerecognitionimages;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class LogAdapter extends RecyclerView.Adapter<LogAdapter.LogViewHolder> {

    public interface OnLogDeleteListener {
        void onDeleteClick(int logId, int position);
    }

    private List<String> logList;
    private OnLogDeleteListener deleteListener;

    public LogAdapter(List<String> logList, OnLogDeleteListener deleteListener) {
        this.logList = logList;
        this.deleteListener = deleteListener;
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_log, parent, false);
        return new LogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        String logData = logList.get(position);
        
        // Expected new format: Name|YYYY-MM-DD|HH:mm:ss|TYPE
        // Old format: Name - YYYY-MM-DD HH:mm:ss
        
        if (logData.contains("|")) {
            String[] parts = logData.split("\\|");
            if (parts.length >= 4) {
                holder.logName.setText(parts[0]);
                holder.logTime.setText(parts[1] + " " + parts[2]);
                holder.logType.setText(parts[3]);
                
                if (parts[3].equals("IN")) {
                    holder.logType.setTextColor(Color.parseColor("#000000")); // Black for IN
                    holder.statusIndicator.setBackgroundColor(Color.parseColor("#000000"));
                } else {
                    holder.logType.setTextColor(Color.parseColor("#666666")); // Gray for OUT
                    holder.statusIndicator.setBackgroundColor(Color.parseColor("#666666"));
                }
                if (parts.length >= 5 && deleteListener != null) {
                    try {
                        final int logId = Integer.parseInt(parts[4]);
                        holder.itemView.setOnLongClickListener(v -> {
                            deleteListener.onDeleteClick(logId, position);
                            return true;
                        });
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }
                }
            }
        } else {
            // Fallback for old format
            String[] parts = logData.split(" - ");
            if (parts.length >= 2) {
                holder.logName.setText(parts[0]);
                holder.logTime.setText(parts[1]);
            } else {
                holder.logName.setText(logData);
            }
            holder.logType.setText("LOG");
            holder.statusIndicator.setBackgroundColor(Color.BLACK);
        }
    }

    @Override
    public int getItemCount() {
        return logList.size();
    }

    public static class LogViewHolder extends RecyclerView.ViewHolder {
        TextView logName, logTime, logType;
        View statusIndicator;

        public LogViewHolder(@NonNull View itemView) {
            super(itemView);
            logName = itemView.findViewById(R.id.logName);
            logTime = itemView.findViewById(R.id.logTime);
            logType = itemView.findViewById(R.id.logType);
            statusIndicator = itemView.findViewById(R.id.statusIndicator);
        }
    }
}