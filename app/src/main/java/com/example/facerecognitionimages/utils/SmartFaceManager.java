package com.example.facerecognitionimages.utils;

import android.content.Context;
import android.util.Log;

import com.example.facerecognitionimages.db.AppDatabase;
import com.example.facerecognitionimages.db.MemberEntity;

import java.util.List;

public class SmartFaceManager {

    private static final float SIMILARITY_THRESHOLD = 0.90f; // If >90% similar, don't save
    private static final int MAX_FACES = 5;

    public static boolean saveFaceSmartly(Context context, String name, float[] newEmbedding) {
        AppDatabase db = AppDatabase.getDatabase(context);
        List<MemberEntity> existingFaces = db.memberDao().getMembersByNameAsc(name);

        if (existingFaces.isEmpty()) {
            // First time saving, just insert
            MemberEntity member = new MemberEntity();
            member.name = name;
            member.embedding = newEmbedding;
            db.memberDao().insertMember(member);
            return true;
        }

        // Check if the new face is too similar to ANY existing face
        for (MemberEntity existing : existingFaces) {
            float sim = cosineSimilarity(newEmbedding, existing.embedding);
            if (sim > SIMILARITY_THRESHOLD) {
                // Redundant face, adds no new information
                Log.d("SmartFaceManager", "Face too similar (" + sim + ") to existing template. Skipping save.");
                return false;
            }
        }

        // It is diverse! Let's see if we hit the limit
        if (existingFaces.size() >= MAX_FACES) {
            // We have 5 faces. We must delete one to make room.
            // We keep existingFaces.get(0) because it's the anchor (the original registration).
            // We delete the oldest non-anchor: existingFaces.get(1).
            db.memberDao().deleteMemberById(existingFaces.get(1).id);
        }

        // Save the new diverse face
        MemberEntity member = new MemberEntity();
        member.name = name;
        member.embedding = newEmbedding;
        db.memberDao().insertMember(member);
        Log.d("SmartFaceManager", "Saved new diverse face for " + name);
        return true;
    }

    private static float cosineSimilarity(float[] emb1, float[] emb2) {
        if (emb1 == null || emb2 == null || emb1.length != emb2.length) return 0f;
        float dotProduct = 0f;
        for (int i = 0; i < emb1.length; i++) {
            dotProduct += emb1[i] * emb2[i];
        }
        return dotProduct;
    }
}
