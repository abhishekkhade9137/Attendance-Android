package com.example.facerecognitionimages;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import android.content.Context;

import com.example.facerecognitionimages.db.AppDatabase;
import com.example.facerecognitionimages.db.MemberEntity;
import androidx.appcompat.widget.SearchView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MembersFragment extends Fragment {

    private RecyclerView recyclerView;
    private MemberAdapter adapter;
    private android.widget.LinearLayout emptyState;
    private com.facebook.shimmer.ShimmerFrameLayout shimmerLayout;
    private SwipeRefreshLayout swipeRefreshLayout;
    private SearchView searchView;
    private List<String> memberList = new ArrayList<>();
    private List<String> filteredList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_members, container, false);
        
        recyclerView = view.findViewById(R.id.membersRecyclerView);
        emptyState = view.findViewById(R.id.emptyState);
        shimmerLayout = view.findViewById(R.id.shimmerLayout);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        searchView = view.findViewById(R.id.searchView);
        
        swipeRefreshLayout.setOnRefreshListener(this::loadMembers);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new MemberAdapter(filteredList, this::deleteMember, this::showBottomSheet);
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
            // Simulate brief network/db delay to show off shimmer
            try { Thread.sleep(500); } catch (InterruptedException e) {}
            
            if (getActivity() == null) return;

            List<MemberEntity> members = AppDatabase.getDatabase(context).memberDao().getAllMembers();
            java.util.HashSet<String> uniqueNames = new java.util.HashSet<>();
            for (MemberEntity m : members) {
                uniqueNames.add(m.name);
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
                    memberList.clear();
                    memberList.addAll(uniqueNames);
                    filterList(searchView.getQuery().toString());
                });
            }
        });
    }

    private void filterList(String query) {
        filteredList.clear();
        if (query == null || query.trim().isEmpty()) {
            filteredList.addAll(memberList);
        } else {
            String lowerQuery = query.toLowerCase();
            for (String member : memberList) {
                if (member.toLowerCase().contains(lowerQuery)) {
                    filteredList.add(member);
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

    private void deleteMember(String name) {
        Context context = getContext();
        if (context == null) return;

        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase.getDatabase(context).memberDao().deleteAllMembersByName(name);
            
            // Delete all image files for this person
            File dir = context.getFilesDir();
            File[] files = dir.listFiles((d, f) -> f.startsWith(name + "_") && f.endsWith("_face.png"));
            if (files != null) {
                for (File f : files) f.delete();
            }
            File legacy = new File(dir, name + "_face.png");
            if (legacy.exists()) legacy.delete();
            
            if (getActivity() != null) {
                getActivity().runOnUiThread(this::loadMembers);
            }
        });
    }

    private void showBottomSheet(String name) {
        MemberBottomSheetFragment bottomSheet = MemberBottomSheetFragment.newInstance(name);
        bottomSheet.setListener(new MemberBottomSheetFragment.MemberActionListener() {
            @Override
            public void onViewPhotos(String memberName) {
                viewMemberPhotos(memberName);
            }
            @Override
            public void onDeleteMember(String memberName) {
                // Show confirmation before deleting
                new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete " + memberName + "?")
                    .setMessage("This will remove all their photos and facial data.")
                    .setPositiveButton("Delete", (d, w) -> deleteMember(memberName))
                    .setNegativeButton("Cancel", null)
                    .show();
            }
        });
        bottomSheet.show(getParentFragmentManager(), "MemberBottomSheet");
    }

    private void viewMemberPhotos(String name) {
        if (getActivity() == null) return;
        
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity());
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_member_photos, null);
        builder.setView(dialogView);
        
        TextView title = dialogView.findViewById(R.id.dialogTitle);
        title.setText("Photos for " + name + "\n(Long press to delete)");
        
        android.widget.LinearLayout photosContainer = dialogView.findViewById(R.id.photosContainer);
        
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<MemberEntity> members = AppDatabase.getDatabase(requireContext()).memberDao().getMembersByNameDesc(name);
            
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (getView() == null) return;
                    Context ctx = getContext();
                    if (ctx == null) return;
                    File dir = ctx.getFilesDir();
                    File[] files = dir.listFiles((d, f) -> f.startsWith(name + "_") && f.endsWith("_face.png"));
                    
                    if (files == null || files.length == 0) {
                        File legacy = new File(dir, name + "_face.png");
                        if (legacy.exists()) {
                            files = new File[]{legacy};
                        }
                    }
                    
                    if (files != null) {
                        java.util.Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
                        
                        for (int i = 0; i < files.length; i++) {
                            File file = files[i];
                            MemberEntity correspondingMember = null;
                            if (i < members.size()) {
                                correspondingMember = members.get(i);
                            }
                            
                            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath());
                            if (bitmap != null) {
                                android.widget.ImageView iv = new android.widget.ImageView(getActivity());
                                android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(300, 300);
                                params.setMargins(0, 0, 16, 0);
                                iv.setLayoutParams(params);
                                iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                                iv.setImageBitmap(bitmap);
                                
                                final MemberEntity finalMember = correspondingMember;
                                iv.setOnLongClickListener(v -> {
                                    new MaterialAlertDialogBuilder(getActivity())
                                        .setTitle("Delete Photo")
                                        .setMessage("Are you sure you want to delete this photo?")
                                        .setPositiveButton("Delete", (d, w) -> {
                                            file.delete();
                                            photosContainer.removeView(iv);
                                            if (finalMember != null) {
                                                Context ctx2 = getContext();
                                                if (ctx2 != null) {
                                                    AppDatabase.databaseWriteExecutor.execute(() -> {
                                                        AppDatabase.getDatabase(ctx2).memberDao().deleteMemberById(finalMember.id);
                                                        if (photosContainer.getChildCount() == 0) {
                                                            if (getActivity() != null) {
                                                                getActivity().runOnUiThread(this::loadMembers); // Refresh if no photos left
                                                            }
                                                        }
                                                    });
                                                }
                                            }
                                            android.widget.Toast.makeText(getActivity(), "Photo deleted", android.widget.Toast.LENGTH_SHORT).show();
                                        })
                                        .setNegativeButton("Cancel", null)
                                        .show();
                                    return true;
                                });
                                
                                photosContainer.addView(iv);
                            }
                        }
                    }
                });
            }
        });
        
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialogView.findViewById(R.id.btnClose).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

}
