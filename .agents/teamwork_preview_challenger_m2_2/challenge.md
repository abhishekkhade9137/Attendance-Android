# Challenge Report — Milestone M2.2 Empirical Security & Stability Challenge

## Challenge Summary

**Overall risk assessment**: LOW

Empirical testing on device `emulator-5554` confirmed that security hardening (`exported="false"`), PIN authentication validation, and multi-tap UI debouncing (`ClickUtils`) are functioning correctly and robustly.

---

## Challenges

### [Low] Challenge 1: Lack of Brute-Force Rate Limiting / Account Lockout on PIN Entry
- **Assumption challenged**: The PIN entry system prevents rapid automated brute-force attempts on `LoginActivity`.
- **Attack scenario**: An attacker with physical access to the device or local input capabilities could submit rapid automated PIN inputs via shell input without being temporarily locked out or delayed.
- **Blast radius**: Low (PIN authentication is required locally; app data is local on device).
- **Mitigation**: Implement exponential backoff or temporary lockout (e.g., 30-second delay after 5 failed PIN attempts).

### [Low] Challenge 2: Plaintext SharedPreference Storage for Admin PIN
- **Assumption challenged**: Admin PIN is stored securely against local root or backup inspection.
- **Attack scenario**: On rooted devices or debug builds, `AppPrefs.xml` stores `AdminPin` in plaintext (`prefs.edit().putString(KEY_PIN, enteredPin).apply()`).
- **Blast radius**: Low (Requires root access or backup privilege).
- **Mitigation**: Hash the PIN using SHA-256 or store via Android KeyStore / EncryptedSharedPreferences.

---

## Stress Test Results

| Test Scenario | Executed Command / Action | Expected Behavior | Actual Behavior | Result |
|---|---|---|---|---|
| Direct Launch `.RecognitionActivity` | `adb shell am start -n com.example.facerecognitionimages/.RecognitionActivity` | Block launch with SecurityException | `java.lang.SecurityException: Permission Denial: ... not exported` | **PASS** |
| Direct Launch `.RegisterActivity` | `adb shell am start -n com.example.facerecognitionimages/.RegisterActivity` | Block launch with SecurityException | `java.lang.SecurityException: Permission Denial: ... not exported` | **PASS** |
| Invalid PIN (`9999`) | Enter `9999` on `LoginActivity` | Reject PIN, show error, clear text | `pinInput.setError("Incorrect PIN")`, input cleared | **PASS** |
| Valid PIN (`0044`) | Enter `0044` on `LoginActivity` | Accept PIN, launch `MainActivity` | Authenticated, navigated to `MainActivity` | **PASS** |
| `btnLogin` Multi-Tap Debounce | 4 rapid tap events to `btnLogin` (<100ms) | Process 1st click, drop duplicates | Single login trigger, 800ms debounce enforced by `ClickUtils` | **PASS** |
| Navigation Bar Tab Multi-Tap | Rapid consecutive taps across `nav_dashboard`, `nav_members`, `nav_logs` | Debounce fast switches, prevent tab glitching | `ClickUtils.isFastDoubleClick()` returned `true` for rapid clicks, UI remained stable | **PASS** |
| Logcat Exception Check | `adb logcat -d *:E` | Zero uncaught exceptions / app crashes | Zero `FATAL EXCEPTION` or `NullPointerException` | **PASS** |

---

## Unchallenged Areas

- **Biometric / Camera Tensor Processing**: Camera frame hardware integration was not stress-tested with artificial camera injection.
- **SQLite Database Encryption**: Local SQLite database storage security was out of scope for this milestone.
