## 2026-07-28T10:45:33Z

Task Objective:
Fix underlying Android source code bugs and harden the app against crashes, unexpected inputs, bad PIN entries, and rapid multi-tapping.

DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A Forensic Auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Root Cause & Fix Requirements:
1. `UIHelper.java`: Safe layout params casting / snackbar layout parameter handling without uncontrolled `(FrameLayout.LayoutParams)` cast.
2. `LoginActivity.java`: Input sanitization (numeric-only enforcement, graceful error reporting on invalid/empty/paste PIN entries), proper handling for PIN `0044` per requirements, and click debouncing.
3. `ClickUtils.java`: Refactor double-click prevention to use monotonic `SystemClock.elapsedRealtime()` and per-view/key tracking instead of single global wall-clock `lastClickTime`.
4. Concurrency: Synchronize or use thread-safe data structures (`CopyOnWriteArrayList` or synchronized blocks) for `faceEmbeddingsList` in `RegisterActivity.java` and `RecognitionActivity.java` to prevent `ConcurrentModificationException`.
5. Null Safety & Cleanups:
   - Null checks on `MediaPlayer.create(...)` in `RecognitionActivity` and `GroupPhotoActivity` before calling `.start()`.
   - Null and dimension checks on `BitmapFactory.decodeFileDescriptor` and `Bitmap.createBitmap`.
   - Proper lifecycle cleanup (`detector.close()`) in `onDestroy()` for `RegisterActivity`, `RecognitionActivity`, and add missing `onDestroy()` in `GroupPhotoActivity`.
6. `BackupUtils.java`: Add Zip Slip canonical path validation (`outFile.getCanonicalPath().startsWith(destinationDir.getCanonicalPath())`) and ensure callbacks run on main thread using `Handler(Looper.getMainLooper())`.
7. `AndroidManifest.xml`: Secure exported activities (`RecognitionActivity`, `RegisterActivity`) by setting `exported="false"` unless intent-filter requires exported.
8. Build & Test: Run `.\gradlew.bat installDebug` on the connected emulator (`emulator-5554`), verify compilation succeeds, app installs, and basic smoke tests pass.

Deliverables:
- Write `changes.md` (detailing modified files, root causes, and fixes applied) and `handoff.md` (5-component report with build/test command logs) in `c:\Users\abhis\Documents\Projects\Attendance-Android\.agents\teamwork_preview_worker_m2_1`.
- Include `progress.md` with liveness header.
- Send a message to parent when finished.
