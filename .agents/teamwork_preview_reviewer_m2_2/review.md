# Code Review & Security Audit Report

**Target**: Attendance-Android Bug Fixes & Hardening (Worker 1)  
**Date**: 2026-07-28  
**Reviewer**: teamwork_preview_reviewer_m2_2  
**Verdict**: **VETO** (REQUEST_CHANGES)

---

## Executive Summary

Worker 1 introduced several valuable hardening fixes including explicit activity export settings (`android:exported="false"`), closing `FaceDetector` and `Facenet` models in `onDestroy()`, `SystemClock.elapsedRealtime()` debouncing, UTF-8 `ByteArrayOutputStream` decoding in backup restoration, and basic null/bounds checks.

However, an in-depth adversarial code review revealed **two high-severity issues** that must be remediated before approval:
1. **Security Vulnerability (Zip Slip Bypass)** in `BackupUtils.java`: `startsWith` comparison against `destinationDir.getCanonicalPath()` lacks a trailing file separator (`File.separator`), allowing path traversal into sibling directories sharing the prefix (e.g., `files_evil`).
2. **Static Memory Leak** in `ClickUtils.java`: Storing raw `View` objects as keys in a static `ConcurrentHashMap` when `view.getId() == View.NO_ID` permanently leaks `View` -> `Context` -> `Activity` instances.
3. **Concurrency Flaw** in `CopyOnWriteArrayList` Usage: Reassigning a non-volatile static reference (`faceEmbeddingsList = new CopyOnWriteArrayList<>(list)`) does not safely publish updates across threads.

---

## Detailed Findings

### 1. [CRITICAL / SECURITY] Zip Slip Path Traversal Check Bypass in `BackupUtils.java`
- **Location**: `app/src/main/java/com/example/facerecognitionimages/utils/BackupUtils.java`, lines 94–97
- **Code snippet**:
  ```java
  File destinationDir = context.getFilesDir();
  ...
  File outFile = new File(destinationDir, entry.getName());
  if (!outFile.getCanonicalPath().startsWith(destinationDir.getCanonicalPath())) {
      throw new SecurityException("Zip Slip path traversal attempt: " + entry.getName());
  }
  ```
- **Why this is a problem**: `destinationDir.getCanonicalPath()` returns a path string such as `/data/user/0/com.example.facerecognitionimages/files` without a trailing slash. If a malicious Zip archive contains an entry with path `../files_evil/malicious.png`, `outFile.getCanonicalPath()` evaluates to `/data/user/0/com.example.facerecognitionimages/files_evil/malicious.png`.  
  Evaluating `"/data/.../files_evil/malicious.png".startsWith("/data/.../files")` returns `true` because `"files_evil"` starts with `"files"`. The check fails to catch path traversal into sibling directories.
- **Suggested Fix**: Append `File.separator` to the destination path before checking:
  ```java
  String destPath = destinationDir.getCanonicalPath();
  if (!destPath.endsWith(File.separator)) {
      destPath += File.separator;
  }
  if (!outFile.getCanonicalPath().startsWith(destPath)) {
      throw new SecurityException("Zip Slip path traversal attempt: " + entry.getName());
  }
  ```
  Or use Java 7 `Path` comparison:
  ```java
  if (!outFile.getCanonicalFile().toPath().startsWith(destinationDir.getCanonicalFile().toPath())) { ... }
  ```

---

### 2. [MAJOR / MEMORY LEAK] Activity & View Memory Leak in `ClickUtils.java`
- **Location**: `app/src/main/java/com/example/facerecognitionimages/utils/ClickUtils.java`, lines 10 & 36
- **Code snippet**:
  ```java
  private static final Map<Object, Long> lastClickMap = new ConcurrentHashMap<>();
  ...
  int id = view.getId();
  Object key = (id != View.NO_ID) ? id : view;
  return isFastDoubleClick(key, intervalMs);
  ```
- **Why this is a problem**: When `view.getId() == View.NO_ID` (unassigned ID), `view` is passed directly as the `key` and inserted into `lastClickMap` (a `static final ConcurrentHashMap`). Static maps hold strong references that survive Activity destruction. Since `View` holds a reference to its `Context`/`Activity`, the entire `Activity` instance is permanently leaked.
- **Suggested Fix**: Avoid holding strong `View` references in static data structures. Use `view.hashCode()` / `System.identityHashCode(view)` or a weak-reference-backed cache:
  ```java
  Object key = (id != View.NO_ID) ? id : System.identityHashCode(view);
  ```

---

### 3. [MEDIUM / CONCURRENCY] Unsafe Publication of `CopyOnWriteArrayList` Reassignments
- **Location**: `RecognitionActivity.java` (lines 95, 564) and `RegisterActivity.java` (lines 82, 595)
- **Code snippet**:
  ```java
  public static java.util.List<PersonEmbedding> faceEmbeddingsList = new java.util.concurrent.CopyOnWriteArrayList<>();
  ...
  faceEmbeddingsList = new java.util.concurrent.CopyOnWriteArrayList<>(list);
  ```
- **Why this is a problem**: `faceEmbeddingsList` is a non-volatile static field. Replacing the entire list reference (`faceEmbeddingsList = new CopyOnWriteArrayList<>(list)`) from a background thread without declaring the reference `volatile` or synchronizing access can result in thread visibility issues (other threads may read stale references or see partially constructed objects). Furthermore, `CopyOnWriteArrayList` is intended for thread-safe mutation/iteration of a single list instance, not repeated re-instantiation.
- **Suggested Fix**: Either make the static reference `volatile`:
  ```java
  public static volatile List<PersonEmbedding> faceEmbeddingsList = new CopyOnWriteArrayList<>();
  ```
  Or update the contents of the existing list:
  ```java
  faceEmbeddingsList.clear();
  faceEmbeddingsList.addAll(list);
  ```

---

## Positive Inspection Findings (Compliant Work)

1. **`AndroidManifest.xml` Export Restrictions**:
   - `RecognitionActivity`, `RegisterActivity`, `LogsActivity`, `GroupPhotoActivity`, `SettingsActivity`, and `MainActivity` are properly marked `android:exported="false"`.
   - `LoginActivity` remains `android:exported="true"` as the launch intent filter target.
   - `FileProvider` is correctly unexported (`android:exported="false"`).

2. **`LoginActivity.java` PIN Validation**:
   - Validation checks for non-null, non-empty, and digit-only regex (`^[0-9]+$`).
   - Standard length check (`length >= 4`) enforced upon creation.
   - PIN "0044" with leading zeros is handled properly as a String in SharedPreferences.
   - Login button debounced with `ClickUtils.isFastDoubleClick(v)`.

3. **`onDestroy()` Native Detector Cleanups**:
   - `GroupPhotoActivity.java`, `RecognitionActivity.java`, and `RegisterActivity.java` call `detector.close()` and `model.close()` in `onDestroy()`, preventing native memory leaks from ML Kit Face Detector and TFLite model instances.

4. **UI Safe Layout Params Handling**:
   - `UIHelper.java` checks `LayoutParams` types (`FrameLayout`, `CoordinatorLayout`, `MarginLayoutParams`) before casting, avoiding `ClassCastException` during Snackbar display.

---

## Build & Test Verification Results

| Command | Target / Device | Result | Log Summary |
|---------|-----------------|--------|-------------|
| `.\gradlew.bat installDebug` | `emulator-5554` | **PASS** | BUILD SUCCESSFUL in 16s. Installed on `emulator-5554`. |
| `.\gradlew.bat test` | Local JVM | **PASS** | BUILD SUCCESSFUL in 12s. Unit tests executed. |

---

## Final Verdict

**Verdict**: **VETO** (REQUEST_CHANGES)  
**Reason**: Critical security vulnerability in `BackupUtils.java` (Zip Slip path traversal bypass) and major memory leak in `ClickUtils.java` (static View reference).

---
