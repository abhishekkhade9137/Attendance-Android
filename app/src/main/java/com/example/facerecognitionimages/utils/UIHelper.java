package com.example.facerecognitionimages.utils;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;

public class UIHelper {

    public static void showSuccessSnackbar(View view, String message) {
        showCustomSnackbar(view, "✅ " + message, "#10B981");
    }

    public static void showErrorSnackbar(View view, String message) {
        showCustomSnackbar(view, "⚠️ " + message, "#EF4444");
    }

    private static void showCustomSnackbar(View view, String message, String colorHex) {
        if (view == null) return;
        Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG);
        View snackbarView = snackbar.getView();
        
        // Setup top sliding behavior safely
        android.view.ViewGroup.LayoutParams lp = snackbarView.getLayoutParams();
        if (lp instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) lp;
            params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            params.setMargins(0, 100, 0, 0);
            snackbarView.setLayoutParams(params);
        } else if (lp instanceof androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams) {
            androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams params =
                (androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams) lp;
            params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            params.setMargins(0, 100, 0, 0);
            snackbarView.setLayoutParams(params);
        } else if (lp instanceof android.view.ViewGroup.MarginLayoutParams) {
            android.view.ViewGroup.MarginLayoutParams params = (android.view.ViewGroup.MarginLayoutParams) lp;
            params.setMargins(0, 100, 0, 0);
            snackbarView.setLayoutParams(params);
        }
        
        // Custom styling
        snackbarView.setBackgroundColor(Color.parseColor(colorHex));
        snackbarView.setBackground(getRoundedBackground(colorHex));
        
        TextView textView = snackbarView.findViewById(com.google.android.material.R.id.snackbar_text);
        if (textView != null) {
            textView.setTextColor(Color.WHITE);
            textView.setTextSize(16f);
            textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        }
        
        snackbar.show();
    }
    
    private static android.graphics.drawable.GradientDrawable getRoundedBackground(String colorHex) {
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        shape.setCornerRadius(24f);
        shape.setColor(Color.parseColor(colorHex));
        return shape;
    }
}
