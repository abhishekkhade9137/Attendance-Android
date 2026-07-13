package com.example.facerecognitionimages;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.facerecognitionimages.db.AppDatabase;
import com.example.facerecognitionimages.db.MemberEntity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MembersFragment extends Fragment {

    private RecyclerView recyclerView;
    private MemberAdapter adapter;
    private TextView emptyState;
    private List<String> memberList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_members, container, false);
        
        recyclerView = view.findViewById(R.id.membersRecyclerView);
        emptyState = view.findViewById(R.id.emptyState);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new MemberAdapter(memberList, this::deleteMember, this::viewMemberPhotos);
        recyclerView.setAdapter(adapter);
        
        view.findViewById(R.id.btnAddMember).setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), RegisterActivity.class));
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadMembers();
    }

    private void loadMembers() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<MemberEntity> members = AppDatabase.getDatabase(requireContext()).memberDao().getAllMembers();
            java.util.HashSet<String> uniqueNames = new java.util.HashSet<>();
            for (MemberEntity m : members) {
                uniqueNames.add(m.name);
            }
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    memberList.clear();
                    memberList.addAll(uniqueNames);
                    if (memberList.isEmpty()) {
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

    private void deleteMember(String name) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            MemberEntity member = new MemberEntity();
            member.name = name;
            AppDatabase.getDatabase(requireContext()).memberDao().deleteMember(member);
            
            // Delete all image files for this person
            File dir = requireContext().getFilesDir();
            File[] files = dir.listFiles((d, f) -> f.startsWith(name + "_") && f.endsWith("_face.png"));
            if (files != null) {
                for (File f : files) f.delete();
            }
            File legacy = new File(dir, name + "_face.png");
            if (legacy.exists()) legacy.delete();
            
            loadMembers();
        });
    }

    private void viewMemberPhotos(String name) {
        if (getActivity() == null) return;
        
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(getActivity());
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_member_photos, null);
        builder.setView(dialogView);
        
        TextView title = dialogView.findViewById(R.id.dialogTitle);
        title.setText("Photos for " + name);
        
        android.widget.LinearLayout photosContainer = dialogView.findViewById(R.id.photosContainer);
        
        File dir = requireContext().getFilesDir();
        File[] files = dir.listFiles((d, f) -> f.startsWith(name + "_") && f.endsWith("_face.png"));
        
        if (files == null || files.length == 0) {
            File legacy = new File(dir, name + "_face.png");
            if (legacy.exists()) {
                files = new File[]{legacy};
            }
        }
        
        if (files != null) {
            java.util.Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
            
            for (File file : files) {
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath());
                if (bitmap != null) {
                    android.widget.ImageView iv = new android.widget.ImageView(getActivity());
                    android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(300, 300);
                    params.setMargins(0, 0, 16, 0);
                    iv.setLayoutParams(params);
                    iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                    iv.setImageBitmap(bitmap);
                    photosContainer.addView(iv);
                }
            }
        }
        
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialogView.findViewById(R.id.btnClose).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

}
