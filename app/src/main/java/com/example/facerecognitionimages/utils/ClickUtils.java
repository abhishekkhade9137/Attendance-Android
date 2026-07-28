package com.example.facerecognitionimages.utils;

import android.os.SystemClock;
import android.view.View;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClickUtils {
    private static final Map<Object, Long> lastClickMap = new ConcurrentHashMap<>();
    private static final String DEFAULT_KEY = "DEFAULT_GLOBAL_KEY";

    /**
     * Prevents rapid double-clicks on buttons globally using default key.
     * @return true if this is a fast double click, false otherwise.
     */
    public static boolean isFastDoubleClick() {
        return isFastDoubleClick(DEFAULT_KEY, 800);
    }

    /**
     * Prevents rapid double-clicks per View.
     */
    public static boolean isFastDoubleClick(View view) {
        return isFastDoubleClick(view, 800);
    }

    /**
     * Prevents rapid double-clicks per View with custom interval.
     */
    public static boolean isFastDoubleClick(View view, long intervalMs) {
        if (view == null) {
            return isFastDoubleClick(DEFAULT_KEY, intervalMs);
        }
        Object key = (view.getId() != View.NO_ID) ? view.getId() : System.identityHashCode(view);
        return isFastDoubleClick(key, intervalMs);
    }

    /**
     * Prevents rapid double-clicks per key with custom interval.
     */
    public static boolean isFastDoubleClick(Object key, long intervalMs) {
        Object actualKey = (key != null) ? key : DEFAULT_KEY;
        long currentTime = SystemClock.elapsedRealtime();
        Long lastTime = lastClickMap.get(actualKey);
        if (lastTime != null) {
            long timeDiff = currentTime - lastTime;
            if (timeDiff >= 0 && timeDiff < intervalMs) {
                return true;
            }
        }
        lastClickMap.put(actualKey, currentTime);
        return false;
    }
}
