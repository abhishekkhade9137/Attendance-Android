package com.example.facerecognitionimages;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class BoundingBoxOverlay extends View {

    public static class Box {
        public Rect rect;
        public String label;
        public boolean recognized;
        public float[] embedding;
        
        public Box(Rect rect, String label, boolean recognized, float[] embedding) {
            this.rect = rect;
            this.label = label;
            this.recognized = recognized;
            this.embedding = embedding;
        }
    }

    public interface OnBoxClickListener {
        void onBoxClicked(Box box, int index);
    }

    private List<Box> boxes = new ArrayList<>();
    private Paint paintRecognized;
    private Paint paintUnrecognized;
    private Paint textPaint;
    private OnBoxClickListener clickListener;

    public BoundingBoxOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paintRecognized = new Paint();
        paintRecognized.setColor(Color.GREEN);
        paintRecognized.setStyle(Paint.Style.STROKE);
        paintRecognized.setStrokeWidth(8f);

        paintUnrecognized = new Paint();
        paintUnrecognized.setColor(Color.WHITE);
        paintUnrecognized.setStyle(Paint.Style.STROKE);
        paintUnrecognized.setStrokeWidth(8f);

        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(50f);
        textPaint.setFakeBoldText(true);
        textPaint.setShadowLayer(4f, 2f, 2f, Color.BLACK);
    }

    public void setBoxes(List<Box> boxes) {
        this.boxes = boxes;
        invalidate();
    }

    public void setOnBoxClickListener(OnBoxClickListener listener) {
        this.clickListener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (Box box : boxes) {
            Paint paint = box.recognized ? paintRecognized : paintUnrecognized;
            canvas.drawRect(box.rect, paint);
            
            if (box.label != null && !box.label.isEmpty()) {
                canvas.drawText(box.label, box.rect.left, box.rect.bottom + 50, textPaint);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float x = event.getX();
            float y = event.getY();
            
            for (int i = 0; i < boxes.size(); i++) {
                Box box = boxes.get(i);
                if (box.rect.contains((int) x, (int) y)) {
                    if (clickListener != null) {
                        clickListener.onBoxClicked(box, i);
                    }
                    return true;
                }
            }
        }
        return super.onTouchEvent(event);
    }
}
