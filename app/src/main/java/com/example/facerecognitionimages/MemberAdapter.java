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
        
        File imgFile = new File(holder.itemView.getContext().getFilesDir(), name + "_face.png");
        if (imgFile.exists()) {
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
