package com.example.facerecognitionimages;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import android.widget.ImageButton;

public class MemberAdapter extends RecyclerView.Adapter<MemberAdapter.MemberViewHolder> {

    public interface OnMemberDeleteListener {
        void onDelete(String name);
    }

    private List<String> memberList;
    private OnMemberDeleteListener deleteListener;

    public MemberAdapter(List<String> memberList, OnMemberDeleteListener deleteListener) {
        this.memberList = memberList;
        this.deleteListener = deleteListener;
    }

    @NonNull
    @Override
    public MemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_member, parent, false);
        return new MemberViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MemberViewHolder holder, int position) {
        String name = memberList.get(position);
        holder.memberName.setText(name);
        
        File dir = holder.itemView.getContext().getFilesDir();
        File[] files = dir.listFiles((d, f) -> f.startsWith(name + "_") && f.endsWith("_face.png"));
        File imgFile = null;
        if (files == null || files.length == 0) {
            File legacy = new File(dir, name + "_face.png");
            if (legacy.exists()) imgFile = legacy;
        } else {
            java.util.Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
            imgFile = files[0];
        }

        if (imgFile != null && imgFile.exists()) {
            Bitmap myBitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
            holder.memberImage.setImageBitmap(myBitmap);
        } else {
            holder.memberImage.setImageResource(R.drawable.ic_baseline_image_24);
        }

        holder.btnDeleteMember.setOnClickListener(v -> {
            if (deleteListener != null) {
                deleteListener.onDelete(name);
            }
        });
    }

    @Override
    public int getItemCount() {
        return memberList.size();
    }

    public static class MemberViewHolder extends RecyclerView.ViewHolder {
        TextView memberName;
        ImageView memberImage;
        ImageButton btnDeleteMember;

        public MemberViewHolder(@NonNull View itemView) {
            super(itemView);
            memberName = itemView.findViewById(R.id.memberName);
            memberImage = itemView.findViewById(R.id.memberImage);
            btnDeleteMember = itemView.findViewById(R.id.btnDeleteMember);
        }
    }
}
