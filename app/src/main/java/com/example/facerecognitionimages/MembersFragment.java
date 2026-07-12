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
        adapter = new MemberAdapter(memberList, this::deleteMember);
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
            List<String> names = new ArrayList<>();
            for (MemberEntity m : members) {
                names.add(m.name);
            }
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    memberList.clear();
                    memberList.addAll(names);
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
            
            // Delete image file
            File imgFile = new File(requireContext().getFilesDir(), name + "_face.png");
            if (imgFile.exists()) {
                imgFile.delete();
            }
            
            loadMembers();
        });
    }
}
