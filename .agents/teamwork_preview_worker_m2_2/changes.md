# Changes Report — Reviewer 2 VETO Remediation

## Summary of Remediation Changes

### 1. `app/src/main/java/com/example/facerecognitionimages/utils/BackupUtils.java`
- **Issue**: Zip Slip Sibling Directory Bypass vulnerability in `importBackup()`.
- **Remediation**:
  - Derived `canonicalDest` from `destinationDir.getCanonicalPath()`.
  - Ensured `canonicalDest` ends with `File.separator` before checking entry prefixes.
  - Checked `canonicalOut` against `canonicalDest` using `.startsWith(canonicalDest)` and exact match against `destinationDir.getCanonicalPath()`.
  - Threw `IOException("Zip entry is outside target directory: " + entry.getName())` when an out-of-bounds entry is detected.

### 2. `app/src/main/java/com/example/facerecognitionimages/utils/ClickUtils.java`
- **Issue**: Static View Memory Leak in `isFastDoubleClick(View view, long intervalMs)` where strong `View` references were stored as keys in static `lastClickMap`.
- **Remediation**:
  - Replaced storing the strong `View` reference with an `Integer` key: `Object key = (view.getId() != View.NO_ID) ? view.getId() : System.identityHashCode(view);`.
  - Stored integer keys instead of `View` instances in `lastClickMap`, eliminating Activity Context memory leaks.

### 3. Concurrency Safety: `RegisterActivity.java` & `RecognitionActivity.java`
- **Issue**: Unsafe Reference Publication of `faceEmbeddingsList`.
- **Remediation**:
  - Marked `public static volatile java.util.List<RecognitionActivity.PersonEmbedding> faceEmbeddingsList` in `RegisterActivity.java`.
  - Marked `public static volatile java.util.List<PersonEmbedding> faceEmbeddingsList` in `RecognitionActivity.java`.
  - In `loadEmbeddings()` for both activities, updated the mutation logic to call `faceEmbeddingsList.clear(); faceEmbeddingsList.addAll(list);` on the thread-safe `CopyOnWriteArrayList` instance instead of un-synchronized re-assignment.

### 4. Build & Verification
- Ran `.\gradlew.bat test` — All unit tests passed successfully.
- Ran `.\gradlew.bat installDebug` — Build compiled, packaged APK `app-debug.apk`, and successfully installed to target emulator (`Medium_Phone(AVD) - 17`).
