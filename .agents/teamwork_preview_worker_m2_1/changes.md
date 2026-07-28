# Summary of Source Code Changes

## Overview
This document details all bugs, root causes, and hardening fixes implemented in the `Attendance-Android` codebase.

---

## Modified Files & Fixes Applied

### 1. `app/src/main/java/com/example/facerecognitionimages/utils/UIHelper.java`
- **Root Cause**: Uncontrolled cast `(FrameLayout.LayoutParams) snackbarView.getLayoutParams()` caused `ClassCastException` when Snackbar parent view layout parameters were `CoordinatorLayout.LayoutParams` or generic `ViewGroup.MarginLayoutParams`.
- **Fix Applied**: Added runtime type checks (`instanceof FrameLayout.LayoutParams`, `instanceof CoordinatorLayout.LayoutParams`, `instanceof MarginLayoutParams`) to cast safely depending on layout container type. Also added null check on `snackbar_text` TextView.

### 2. `app/src/main/java/com/example/facerecognitionimages/utils/ClickUtils.java`
- **Root Cause**: Double-click prevention used a single global wall-clock timestamp (`System.currentTimeMillis()`) stored in static variable `lastClickTime`. This caused cross-button interactions across screens to block each other and was vulnerable to system time adjustments.
- **Fix Applied**: Refactored `ClickUtils` to use monotonic clock (`SystemClock.elapsedRealtime()`) and per-view/key tracking via `ConcurrentHashMap<Object, Long>`. Added overloaded methods `isFastDoubleClick(View)`, `isFastDoubleClick(View, long)`, `isFastDoubleClick(Object, long)` to support granular debouncing per view.

### 3. `app/src/main/java/com/example/facerecognitionimages/LoginActivity.java`
- **Root Cause**: Lacked input sanitization (accepted non-numeric inputs or pasted text), did not debounce the login button, and needed strict handling for numeric PIN entries such as `"0044"`.
- **Fix Applied**:
  - Added click debouncing using `ClickUtils.isFastDoubleClick(v)` on `btnLogin`.
  - Added numeric-only input sanitization regex check (`enteredPin.matches("^[0-9]+$")`), returning `"PIN must contain only digits"` on non-numeric input and `"PIN cannot be empty"` when empty.
  - Ensured PIN `"0044"` (and any PIN with leading zeros) is preserved and checked via String equality (`enteredPin.equals(savedPin)`), avoiding integer conversion truncation.

### 4. `app/src/main/java/com/example/facerecognitionimages/RegisterActivity.java`
- **Root Cause**:
  - `faceEmbeddingsList` was declared as a plain `ArrayList`, causing `ConcurrentModificationException` when iterated on recognition threads while being updated on database background threads.
  - `BitmapFactory.decodeFileDescriptor` and `Bitmap.createBitmap` lacked null and non-positive dimension checks.
  - `detector.close()` was missing in `onDestroy()`.
- **Fix Applied**:
  - Replaced `ArrayList` with `java.util.concurrent.CopyOnWriteArrayList` for `faceEmbeddingsList` in declaration and assignment.
  - Added null check on `ParcelFileDescriptor` and decoded `Bitmap`, as well as `width > 0 && height > 0` and bounds validation before `Bitmap.createBitmap`.
  - Added `if (detector != null) detector.close()` inside `onDestroy()`.

### 5. `app/src/main/java/com/example/facerecognitionimages/RecognitionActivity.java`
- **Root Cause**:
  - `faceEmbeddingsList` was declared as a plain `ArrayList`, causing `ConcurrentModificationException` during concurrent reads/writes.
  - `MediaPlayer.create(...)` was called without checking for `null` before calling `.start()`, leading to potential NullPointerExceptions if audio initialization failed.
  - Missing null/dimension checks on bitmap decoding/cropping.
  - `detector.close()` was omitted in `onDestroy()`.
- **Fix Applied**:
  - Changed `faceEmbeddingsList` to `CopyOnWriteArrayList` for thread-safe concurrent iteration and updates.
  - Added `if (mp != null)` guard around `mp.start()` and `mp.setOnCompletionListener`.
  - Added null and dimension checks on `pfd`, decoded `bitmap`, and matrix operations in `getBitmapFromUri`.
  - Added `if (detector != null) detector.close()` in `onDestroy()`.

### 6. `app/src/main/java/com/example/facerecognitionimages/GroupPhotoActivity.java`
- **Root Cause**:
  - `MediaPlayer.create(...)` called `.start()` without null check.
  - `getEmbedding` and `getBitmapFromUri` lacked null and positive dimension checks before `Bitmap.createBitmap`.
  - Completely missing `onDestroy()` method, resulting in unreleased MLKit face detector, TFLite model, and executor threads.
- **Fix Applied**:
  - Added `if (mp != null)` before `mp.start()`.
  - Added null and dimension checks (`bitmap != null && width > 0 && height > 0`) before creating cropped/rotated bitmaps.
  - Implemented `onDestroy()` to shutdown `executor` and close `model` and `detector`.

### 7. `app/src/main/java/com/example/facerecognitionimages/utils/BackupUtils.java`
- **Root Cause**:
  - Zip Slip vulnerability in `importBackup`: `File outFile = new File(destinationDir, entry.getName())` extracted entries without canonical path validation.
  - Multi-byte UTF-8 character splitting when reading JSON chunks using fixed-size byte buffer.
  - UI callbacks (`onSuccess`, `onError`) were invoked directly on background executor threads (`databaseWriteExecutor`).
- **Fix Applied**:
  - Added Zip Slip path traversal check: `if (!outFile.getCanonicalPath().startsWith(destinationDir.getCanonicalPath())) throw new SecurityException(...)`.
  - Replaced String buffer chunking with `ByteArrayOutputStream` to safely process multi-byte UTF-8 JSON content.
  - Wrapped all `BackupCallback` calls (`onSuccess`, `onError`) with `new Handler(Looper.getMainLooper()).post(...)` to guarantee main thread execution.

### 8. `app/src/main/AndroidManifest.xml`
- **Root Cause**: `RecognitionActivity` and `RegisterActivity` were declared with `android:exported="true"` without intent filters, exposing internal activities to external unauthorized component invocation.
- **Fix Applied**: Updated both activity declarations to `android:exported="false"`.
