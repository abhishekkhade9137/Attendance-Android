package com.example.facerecognitionimages.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "logs")
public class LogEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @NonNull
    public String name;

    @NonNull
    public String date; // Format: YYYY-MM-DD

    @NonNull
    public String time; // Format: HH:mm:ss

    @NonNull
    public String type; // "IN" or "OUT"
}
