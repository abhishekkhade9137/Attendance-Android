## 2026-07-28T11:05:32Z

<USER_REQUEST>
You are teamwork_preview_worker_m2_2.
Your working directory is: c:\Users\abhis\Documents\Projects\Attendance-Android\.agents\teamwork_preview_worker_m2_2

Task Objective:
Remediate the 3 specific issues identified in Reviewer 2's VETO report for Attendance-Android:

DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A Forensic Auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Remediation Requirements:
1. `BackupUtils.java` [Zip Slip Sibling Directory Bypass]:
   - Fix canonical path comparison in `unzip`: Ensure `canonicalDest` ends with `File.separator` before calling `.startsWith()`, e.g.:
     `String canonicalDest = destinationDir.getCanonicalPath();`
     `if (!canonicalDest.endsWith(File.separator)) { canonicalDest += File.separator; }`
     `String canonicalOut = outFile.getCanonicalPath();`
     `if (!canonicalOut.startsWith(canonicalDest) && !canonicalOut.equals(destinationDir.getCanonicalPath())) { throw new IOException("Zip entry is outside target directory: " + entry.getName()); }`

2. `ClickUtils.java` [Static View Memory Leak]:
   - Fix key generation in `isFastDoubleClick(View view, long intervalMillis)`. Instead of storing strong `View` references as keys in static `Map<Object, Long>`, derive a key that does not retain the `View` instance. Use `Integer` key:
     `Object key = (view.getId() != View.NO_ID) ? view.getId() : System.identityHashCode(view);`
   - Store `key` (Integer) in `lastClickMap` instead of `view`. This prevents static memory leaks of Activity context.

3. Concurrency Safety [Unsafe Reference Publication]:
   - Mark `public static volatile List<PersonEmbedding> faceEmbeddingsList` in `RegisterActivity.java` and `RecognitionActivity.java`.
   - Alternatively, instead of re-assigning the static field `faceEmbeddingsList = new CopyOnWriteArrayList<>(list)`, call `faceEmbeddingsList.clear(); faceEmbeddingsList.addAll(list);` on the existing `CopyOnWriteArrayList` instance to ensure thread-safe mutation and safe publication across `databaseWriteExecutor` and `recognitionExecutor`.

4. Build & Verify:
   - Run `.\gradlew.bat installDebug` and `.\gradlew.bat test` on `emulator-5554` to verify build succeeds and unit tests pass.

Deliverables:
- Write `changes.md` and `handoff.md` in `c:\Users\abhis\Documents\Projects\Attendance-Android\.agents\teamwork_preview_worker_m2_2`.
- Include `progress.md` with liveness header.
- Send a message to parent when finished.
</USER_REQUEST>
