# Adversarial Challenge Report — Attendance-Android

## Challenge Summary

**Overall risk assessment**: MEDIUM

Empirical stress testing was conducted on Attendance-Android (`com.example.facerecognitionimages`) running on connected Android emulator `emulator-5554`. The testing included ADB shell rapid input tap sequences, malformed/bad PIN entries, rapid navigation tab switches, rapid camera capture/flip operations, and ADB Monkey stress testing (`adb shell monkey -p com.example.facerecognitionimages -v 1000`).

The application demonstrated good crash resilience overall — avoiding `NullPointerException`, `ClassCastException`, `ConcurrentModificationException`, or ANRs during 1000 Monkey events. However, two operational failure vectors were identified during stress testing.

---

## Challenges

### [Medium] Challenge 1: Camera SurfaceView Screenshot Timeout & Bitmap Acquisition Failure
- **Assumption challenged**: Assumed `PreviewView.SurfaceViewImplementation.getBitmap()` synchronously returns valid bitmaps under rapid capture and camera flip commands.
- **Attack scenario**: Rapidly tapping `btnFlipCamera` and `btnReady` (capture button) on `RegisterActivity` in quick succession.
- **Blast radius**: `PreviewView` logs `SurfaceViewImpl: Timed out while trying to acquire screenshot` and `PreviewView.SurfaceViewImplementation.getBitmap() failed with error 3`. Image capture operations fail or produce blank/null frames during rapid user interaction.
- **Mitigation**: Implement click debouncing (disable `btnReady` and `btnFlipCamera` during camera initialization and capture processing) and handle bitmap acquisition error callbacks gracefully in `RegisterActivity`.

### [Low] Challenge 2: Unrecoverable Input Channel Disposition under Rapid Window Transitions
- **Assumption challenged**: Assumed window transition channels remain intact when rapid UI tap events occur simultaneously with system dialog presentations (such as 16 KB page-alignment warning popups).
- **Attack scenario**: Intersecting background activity focus changes with rapid input tap sequences.
- **Blast radius**: `InputDispatcher` reports `channel '...MainActivity' ~ Channel is unrecoverably broken and will be disposed!`, destroying the `MainActivity` window and resetting the application process back to `LoginActivity`.
- **Mitigation**: Ensure proper lifecycle handling and guard against dual intent dispatch on login button tap to prevent concurrent activity launches.

---

## Stress Test Results

| Scenario / Test Case | Target / Inputs | Expected Behavior | Actual Behavior | Pass / Fail |
|----------------------|-----------------|-------------------|-----------------|-------------|
| **Empty PIN Submission** | `LoginActivity: pinInput` = `""` | Show error toast, prevent auth | Showed validation hint, no crash | **PASS** |
| **Rapid Login Button Tap** | 10 rapid ADB taps on `btnLogin` | Debounce taps, no crash/double launch | Handled without crash | **PASS** |
| **Letters & Symbols PIN** | `pinInput` = `"abcXYZ"`, `"!@#$%"` | Filter or reject invalid chars | IME inputType `numberPassword` restricts text input; no crash | **PASS** |
| **Malformed PIN Lengths** | `pinInput` = `"12"`, `"12345678901234567890"` | Reject invalid lengths without crash | Non-matching PIN handled safely | **PASS** |
| **Invalid PIN Entry** | `pinInput` = `"9999"` | Deny access, keep focus on login | Authentication rejected gracefully | **PASS** |
| **Valid PIN Login** | `pinInput` = `"0044"` | Authenticate & navigate to `MainActivity` | Authenticated and loaded `MainActivity` | **PASS** |
| **Rapid Tab Switch** | 30 rapid switches between Dashboard/Members/Logs | Smooth Fragment transitions, no memory/state errors | Fragment transitions executed cleanly without logcat errors | **PASS** |
| **Rapid Camera Flip & Capture** | Rapid taps on `btnFlipCamera` & `btnReady` in `RegisterActivity` | Smooth camera frame capture & flip | `SurfaceViewImpl` timed out acquiring screenshot (`error 3`) | **FAIL (Non-fatal operational error)** |
| **ADB Monkey Stress** | `adb shell monkey -p com.example.facerecognitionimages -v 1000` | 1000 random events without crash/ANR | 1000 events completed without crash/ANR/NPE | **PASS** |

---

## Unchallenged Areas

- **Biometric / Real Face Model Inference Precision**: Out-of-scope for ADB synthetic input stress testing; requires real camera face feed validation.
- **SQLite Database Persistence Corruption**: Long-term database disk full / corruption scenarios were not tested.
