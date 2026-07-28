# Project: Attendance-Android Testing and Bug-Fixing

## Architecture & Scope
Android app (Attendance-Android) testing on connected emulator via `adb shell` UI commands, crash log analysis (`adb logcat`), root-cause identification, and source code hardening against bad PIN inputs (PIN 0044 for restricted area) and rapid tapping.

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | M1: Exploration & Initial ADB UI Testing | Codebase structure analysis, connected device check, initial UI interaction & crash scanning | none | DONE |
| 2 | M2: Bug Root Cause & Source Code Hardening | Fix identified crashes/bugs in Android source code, rebuild app, reinstall | M1 | DONE |
| 3 | M3: 30-Min Active ADB Testing & Final Verification | Execute 30-min active ADB UI testing session, verify zero logcat crashes, verify bad PIN / rapid tap handling | M2 | DONE |

## Code Layout
- Android project root: `c:\Users\abhis\Documents\Projects\Attendance-Android\`
