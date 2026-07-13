package com.example.facerecognitionimages;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class MemberBottomSheetFragment extends BottomSheetDialogFragment {

    private static final String ARG_MEMBER_NAME = "member_name";
    
    private String memberName;
    private MemberActionListener listener;

    public interface MemberActionListener {
        void onViewPhotos(String name);
        void onDeleteMember(String name);
    }

    public static MemberBottomSheetFragment newInstance(String name) {
        MemberBottomSheetFragment fragment = new MemberBottomSheetFragment();
        Bundle args = new Bundle();
        args.putString(ARG_MEMBER_NAME, name);
        fragment.setArguments(args);
        return fragment;
    }

    public void setListener(MemberActionListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_member, container, false);
        
        if (getArguments() != null) {
            memberName = getArguments().getString(ARG_MEMBER_NAME);
        }

        TextView tvName = view.findViewById(R.id.tvMemberName);
        tvName.setText(memberName != null ? memberName : "Member");

        view.findViewById(R.id.actionViewPhotos).setOnClickListener(v -> {
            if (listener != null) listener.onViewPhotos(memberName);
            dismiss();
        });

        view.findViewById(R.id.actionDelete).setOnClickListener(v -> {
            if (listener != null) listener.onDeleteMember(memberName);
            dismiss();
        });

        return view;
    }
}
