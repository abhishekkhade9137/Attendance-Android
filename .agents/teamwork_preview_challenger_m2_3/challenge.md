# Empirical Challenge Report — Milestone m2_3

## Challenge Summary

**Overall risk assessment**: LOW

Empirical re-challenge of Attendance-Android (`com.example.facerecognitionimages`) was executed on connected Android emulator `emulator-5554`. All PIN login stress scenarios (wrong PIN, short PIN, non-numeric input, valid PIN) and rapid UI control multi-tap stress tests passed cleanly with **0 crashes** and **0 fatal exceptions** detected in `adb logcat`.

---

## Challenges & Stress Scenarios Tested

### 1. Wrong PIN Input (`9999`)
- **Target**: `LoginActivity` (`pinInput` field)
- **Input**: `"9999"`
- **Expected Behavior**: Reject login, set error message ("Incorrect PIN"), remain on `LoginActivity`, zero crashes.
- **Observed Behavior**: `pinInput` set error "Incorrect PIN", app remained resumed on `LoginActivity`. Zero logcat exceptions.
- **Status**: PASSED

### 2. Short PIN Input (`12`)
- **Target**: `LoginActivity` (`pinInput` field)
- **Input**: `"12"`
- **Expected Behavior**: Reject login, set error message, remain on `LoginActivity`, zero crashes.
- **Observed Behavior**: App set error "Incorrect PIN", remained on `LoginActivity`. Zero logcat exceptions.
- **Status**: PASSED

### 3. Non-numeric Input (`abc`)
- **Target**: `LoginActivity` (`pinInput` field)
- **Input**: `"abc"`
- **Expected Behavior**: Handle invalid non-digit input gracefully, set error message ("PIN must contain only digits"), remain on `LoginActivity`, zero crashes.
- **Observed Behavior**: `pinInput` error set to "PIN must contain only digits". App remained stable on `LoginActivity`. Zero logcat exceptions.
- **Status**: PASSED

### 4. Rapid Multi-Tap on Login Control
- **Target**: `LoginActivity` (`btnLogin`)
- **Attack Scenario**: 25 rapid taps fired at `btnLogin` with invalid input (`9999`) to test double-click debounce (`ClickUtils.isFastDoubleClick`) and handler concurrency.
- **Expected Behavior**: Debounce logic ignores fast subsequent clicks, no multi-triggering, no ANR, no crash.
- **Observed Behavior**: `ClickUtils` debounced rapid clicks. `LoginActivity` remained stable. Zero logcat exceptions.
- **Status**: PASSED

### 5. Valid PIN Login (`0044`)
- **Target**: `LoginActivity` (`pinInput` field & `btnLogin`)
- **Input**: `"0044"`
- **Expected Behavior**: Validate PIN against stored `AdminPin` in `AppPrefs.xml`, launch `MainActivity`, finish `LoginActivity`.
- **Observed Behavior**: Successfully authenticated and navigated to `MainActivity` (`topResumedActivity=.../MainActivity`).
- **Status**: PASSED

### 6. Rapid Multi-Tap on UI Controls & Navigation Tabs
- **Target**: `MainActivity` UI controls (Bottom navigation tabs: Attendance, Members, Logs, Settings; Add Member button)
- **Attack Scenario**: Fired 80 rapid tab switches in quick succession (20 cycles across 4 tabs), followed by 30 rapid taps on content floating action button.
- **Expected Behavior**: Smooth fragment transitions without state corruption, zero crashes, zero ANR.
- **Observed Behavior**: All tab transitions completed cleanly. Button tap navigated to `RegisterActivity` as expected. Zero logcat exceptions.
- **Status**: PASSED

---

## Stress Test Results Matrix

| Scenario | Input / Action | Expected Result | Actual Result | Status |
|---|---|---|---|---|
| Wrong PIN | `"9999"` | Show error, stay on Login | Set error "Incorrect PIN", stayed on LoginActivity | PASS |
| Short PIN | `"12"` | Show error, stay on Login | Set error "Incorrect PIN", stayed on LoginActivity | PASS |
| Non-numeric PIN | `"abc"` | Show error "PIN must contain only digits" | Set error "PIN must contain only digits" | PASS |
| Rapid Login Button Taps | 25 fast taps | Debounce clicks, no crash | Clicks debounced by `ClickUtils`, stayed stable | PASS |
| Valid PIN | `"0044"` | Navigate to `MainActivity` | Navigated to `MainActivity` | PASS |
| Rapid Tab Switches | 80 fast tab taps | Handle fragment transitions without crash | Handled cleanly, switched tabs | PASS |
| Rapid Action Button Taps | 30 fast FAB taps | Handle activity launch cleanly | Opened `RegisterActivity` without crash | PASS |
| Logcat Crash Audit | Full test run audit | 0 crashes / FATAL exceptions | 0 FATAL EXCEPTION, 0 ANR, 0 Crash | PASS |

---

## Unchallenged / Out-of-Scope Areas
- Physical camera hardware capture during face recognition (emulator camera fallback used).
