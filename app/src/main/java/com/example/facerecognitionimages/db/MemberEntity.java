package com.example.facerecognitionimages.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "members")
public class MemberEntity {
    @PrimaryKey
    @NonNull
    public String name;

    @NonNull
    public float[] embedding;
}
