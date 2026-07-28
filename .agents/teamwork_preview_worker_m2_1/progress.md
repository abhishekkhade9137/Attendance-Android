# Progress Log

Last visited: 2026-07-28T10:54:00Z

- [x] Initialized workspace documentation (`ORIGINAL_REQUEST.md`, `BRIEFING.md`, `progress.md`).
- [x] Investigate files: UIHelper.java, LoginActivity.java, ClickUtils.java, RegisterActivity.java, RecognitionActivity.java, GroupPhotoActivity.java, BackupUtils.java, AndroidManifest.xml.
- [x] Implement fixes for all 7 items:
  - [x] `UIHelper.java`: Safe layout params casting (`instanceof` check for FrameLayout, CoordinatorLayout, MarginLayoutParams) and null safety for snackbar text.
  - [x] `LoginActivity.java`: Input sanitization (numeric-only regex `^[0-9]+$`), string-preserved PIN matching for `0044`, and click debouncing on `btnLogin`.
  - [x] `ClickUtils.java`: Refactored to monotonic `SystemClock.elapsedRealtime()` with per-view/key tracking via `ConcurrentHashMap`.
  - [x] Concurrency: `faceEmbeddingsList` thread-safety using `CopyOnWriteArrayList` in `RegisterActivity.java` and `RecognitionActivity.java`.
  - [x] Null Safety & Cleanups:
    - [x] `MediaPlayer.create(...)` null checks before `.start()` in `RecognitionActivity` and `GroupPhotoActivity`.
    - [x] Null and dimension checks on `ParcelFileDescriptor`, `BitmapFactory.decodeFileDescriptor`, and `Bitmap.createBitmap`.
    - [x] Lifecycle cleanup: `detector.close()` in `onDestroy()` for `RegisterActivity` & `RecognitionActivity`, and added missing `onDestroy()` in `GroupPhotoActivity`.
  - [x] `BackupUtils.java`: Zip Slip canonical path validation and main thread callback delivery using `Handler(Looper.getMainLooper())`.
  - [x] `AndroidManifest.xml`: Set `exported="false"` for `RecognitionActivity` and `RegisterActivity`.
- [x] Verify build & install on `emulator-5554` (`.\gradlew.bat installDebug`).
- [x] Write `changes.md` and `handoff.md`.
- [x] Send message to parent.
