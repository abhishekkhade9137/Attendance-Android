package com.example.facerecognitionimages.utils;

public class ClickUtils {
    private static long lastClickTime = 0;
    
    /**
     * Prevents rapid double-clicks on buttons.
     * @return true if this is a fast double click, false otherwise.
     */
    public static boolean isFastDoubleClick() {
        long time = System.currentTimeMillis();
        long timeD = time - lastClickTime;
        if (0 < timeD && timeD < 800) {
            return true;
        }
        lastClickTime = time;
        return false;
    }
}
