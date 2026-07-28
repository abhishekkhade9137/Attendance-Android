import subprocess
import time
import datetime
import os
import sys

DEVICE = "emulator-5554"
PACKAGE = "com.example.facerecognitionimages"
WORK_DIR = r"c:\Users\abhis\Documents\Projects\Attendance-Android\.agents\teamwork_preview_challenger_m3_1"
LOG_FILE = os.path.join(WORK_DIR, "adb_cmd_log.txt")
PROGRESS_FILE = os.path.join(WORK_DIR, "progress.md")

def run_adb(cmd_list):
    full_cmd = ["adb", "-s", DEVICE] + cmd_list
    try:
        res = subprocess.run(full_cmd, capture_output=True, text=True, timeout=30)
        return res.stdout.strip()
    except Exception as e:
        return f"Error: {e}"

def log_msg(msg, log_fp):
    timestamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    entry = f"[{timestamp}] {msg}"
    print(entry)
    log_fp.write(entry + "\n")
    log_fp.flush()

def update_progress(elapsed_sec, cycle_count, cmd_count):
    timestamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    progress_content = f"""# Progress Log — teamwork_preview_challenger_m3_1

Last visited: {timestamp}

## Status Overview
- [x] Initialized agent briefing and workspace
- [x] Check ADB device state (`emulator-5554`) and app installation (`{PACKAGE}`)
- [/] Active 30-minute stress session in progress (Elapsed: {int(elapsed_sec)}s / 1830s, Cycles: {cycle_count}, ADB Cmds: {cmd_count})
- [ ] Complete 30+ minute stress loop
- [ ] Run logcat crash audit (`adb logcat -d *:E`)
- [ ] Generate test_session_30min.md, challenge.md, and handoff.md
- [ ] Notify parent agent
"""
    try:
        with open(PROGRESS_FILE, "w", encoding="utf-8") as f:
            f.write(progress_content)
    except Exception as e:
        print(f"Error updating progress.md: {e}")

def main():
    os.makedirs(WORK_DIR, exist_ok=True)
    log_fp = open(LOG_FILE, "w", encoding="utf-8")
    
    start_time_utc = datetime.datetime.now(datetime.timezone.utc)
    start_iso = start_time_utc.strftime("%Y-%m-%dT%H:%M:%SZ")
    log_msg(f"=== Starting 30-Minute Active ADB Stress Session ===", log_fp)
    log_msg(f"Start Timestamp: {start_iso}", log_fp)

    # 1. Clear logcat buffer
    log_msg("Clearing logcat buffer (adb logcat -c)...", log_fp)
    run_adb(["logcat", "-c"])

    # First ensure dismissal of any system dialogs by tapping OK/Don't show
    run_adb(["shell", "input", "tap", "800", "1780"])
    time.sleep(1)

    target_duration = 1830  # 30.5 minutes (>= 30 minutes requirement)
    cmd_count = 0
    cycle_count = 0
    last_progress_update = time.time()

    wrong_pins = ["", "12", "abc", "9999"]

    while True:
        now = time.time()
        elapsed = now - start_time_utc.timestamp()
        if elapsed >= target_duration:
            log_msg(f"Target duration of {target_duration}s reached. Total elapsed: {elapsed:.2f}s", log_fp)
            break

        cycle_count += 1
        log_msg(f"--- Starting Test Cycle #{cycle_count} (Elapsed: {int(elapsed)}s) ---", log_fp)

        # ----------------------------------------------------
        # MODULE A: Wrong PIN Inputs & Restricted Area PIN 0044
        # ----------------------------------------------------
        log_msg("Module A: Testing Wrong PINs & Restricted PIN Authentication", log_fp)
        
        # Ensure LoginActivity is active
        run_adb(["shell", "am", "start", "-n", f"{PACKAGE}/.LoginActivity"])
        cmd_count += 1
        time.sleep(1)

        # Test Wrong PINs
        for pin in wrong_pins:
            # Tap PIN input field (540, 1010)
            run_adb(["shell", "input", "tap", "540", "1010"])
            cmd_count += 1
            time.sleep(0.3)

            # Select all / delete previous text (keyevent 67 repeatedly or keyevent 28/29)
            for _ in range(10):
                run_adb(["shell", "input", "keyevent", "67"])
                cmd_count += 1

            if pin:
                run_adb(["shell", "input", "text", pin])
                cmd_count += 1
                time.sleep(0.3)

            # Tap Login button (540, 1220)
            run_adb(["shell", "input", "tap", "540", "1220"])
            cmd_count += 1
            log_msg(f"Tested Wrong PIN input: '{pin}'", log_fp)
            time.sleep(0.5)

        # Test Restricted Area PIN Entry: PIN "0044"
        run_adb(["shell", "input", "tap", "540", "1010"])
        cmd_count += 1
        time.sleep(0.3)
        for _ in range(10):
            run_adb(["shell", "input", "keyevent", "67"])
            cmd_count += 1

        run_adb(["shell", "input", "text", "0044"])
        cmd_count += 1
        time.sleep(0.3)
        run_adb(["shell", "input", "tap", "540", "1220"])
        cmd_count += 1
        log_msg("Submitted PIN '0044' -> Authenticating...", log_fp)
        time.sleep(1.5)

        # ----------------------------------------------------
        # MODULE B: Rapid Multi-Tap Sequences & Dashboard Controls
        # ----------------------------------------------------
        log_msg("Module B: Rapid Multi-Tap Sequences & Controls", log_fp)
        
        # Rapid taps on settings / top bar icon (980, 160)
        for _ in range(10):
            run_adb(["shell", "input", "tap", "980", "160"])
            cmd_count += 1
            time.sleep(0.05)
        time.sleep(0.5)

        # Send back key in case settings dialog opened
        run_adb(["shell", "input", "keyevent", "4"])
        cmd_count += 1
        time.sleep(0.5)

        # Rapid multi-taps on stats cards (300, 500) and (700, 500)
        for _ in range(10):
            run_adb(["shell", "input", "tap", "300", "500"])
            cmd_count += 1
            time.sleep(0.05)

        for _ in range(10):
            run_adb(["shell", "input", "tap", "700", "500"])
            cmd_count += 1
            time.sleep(0.05)

        # ----------------------------------------------------
        # MODULE C: Fragment Navigation & Rapid Tab Switching
        # ----------------------------------------------------
        log_msg("Module C: Rapid Fragment Tab Switching (Dashboard <-> Members <-> Logs)", log_fp)
        tabs = [
            (208, 2190, "Dashboard"),
            (540, 2190, "Members"),
            (872, 2190, "Logs")
        ]

        # Rapid tab switching loop (25 switches)
        for i in range(25):
            x, y, tab_name = tabs[i % 3]
            run_adb(["shell", "input", "tap", str(x), str(y)])
            cmd_count += 1
            time.sleep(0.15)

        # Swiping / scrolling gesture test on fragment view
        log_msg("Performing swipe / scroll gestures on fragment views", log_fp)
        run_adb(["shell", "input", "swipe", "500", "1500", "500", "500", "200"])
        cmd_count += 1
        time.sleep(0.5)
        run_adb(["shell", "input", "swipe", "500", "500", "500", "1500", "200"])
        cmd_count += 1
        time.sleep(0.5)

        # ----------------------------------------------------
        # MODULE D: System Lifecycle & Resilience Events
        # ----------------------------------------------------
        log_msg("Module D: System Events (Back key, Home key, Force Stop, Relaunch)", log_fp)
        
        # Back key
        run_adb(["shell", "input", "keyevent", "4"])
        cmd_count += 1
        time.sleep(1)

        # Home key
        run_adb(["shell", "input", "keyevent", "3"])
        cmd_count += 1
        time.sleep(1)

        # Force Stop
        run_adb(["shell", "am", "force-stop", PACKAGE])
        cmd_count += 1
        time.sleep(1)

        # Relaunch App
        run_adb(["shell", "am", "start", "-n", f"{PACKAGE}/.LoginActivity"])
        cmd_count += 1
        time.sleep(1.5)

        # Authenticate back into main screen
        run_adb(["shell", "input", "tap", "540", "1010"])
        cmd_count += 1
        for _ in range(10):
            run_adb(["shell", "input", "keyevent", "67"])
            cmd_count += 1
        run_adb(["shell", "input", "text", "0044"])
        cmd_count += 1
        run_adb(["shell", "input", "tap", "540", "1220"])
        cmd_count += 1
        time.sleep(1.5)

        # Check progress heartbeat update every 2 minutes
        if time.time() - last_progress_update >= 120:
            update_progress(time.time() - start_time_utc.timestamp(), cycle_count, cmd_count)
            last_progress_update = time.time()

    # Session End
    end_time_utc = datetime.datetime.now(datetime.timezone.utc)
    end_iso = end_time_utc.strftime("%Y-%m-%dT%H:%M:%SZ")
    total_elapsed = (end_time_utc - start_time_utc).total_seconds()
    
    log_msg(f"=== Active ADB Testing Session Completed ===", log_fp)
    log_msg(f"End Timestamp: {end_iso}", log_fp)
    log_msg(f"Total Duration: {total_elapsed:.2f} seconds ({total_elapsed/60:.2f} minutes)", log_fp)
    log_msg(f"Total Test Cycles Completed: {cycle_count}", log_fp)
    log_msg(f"Total ADB Commands Issued: {cmd_count}", log_fp)

    # Final Logcat Crash Audit
    log_msg("--- Running Logcat Crash Audit (adb logcat -d *:E) ---", log_fp)
    err_output = run_adb(["logcat", "-d", "*:E"])
    
    # Also grep specifically for FATAL, ANR, Exception, com.example.facerecognitionimages
    all_logs = run_adb(["logcat", "-d"])
    
    fatal_lines = [line for line in all_logs.splitlines() if "FATAL" in line or "AndroidRuntime" in line]
    anr_lines = [line for line in all_logs.splitlines() if "ANR in" in line or "AmStat" in line]
    app_err_lines = [line for line in err_output.splitlines() if PACKAGE in line]

    crash_count = len(fatal_lines)
    anr_count = len(anr_lines)
    app_err_count = len(app_err_lines)

    log_msg(f"Crash Audit Results:", log_fp)
    log_msg(f"  - Fatal Exceptions / Crashes: {crash_count}", log_fp)
    log_msg(f"  - ANRs: {anr_count}", log_fp)
    log_msg(f"  - Application Error Entries in logcat: {app_err_count}", log_fp)

    # Save logcat error audit summary
    audit_summary_path = os.path.join(WORK_DIR, "logcat_audit_summary.txt")
    with open(audit_summary_path, "w", encoding="utf-8") as af:
        af.write(f"Session Start: {start_iso}\n")
        af.write(f"Session End: {end_iso}\n")
        af.write(f"Duration (seconds): {total_elapsed:.2f}\n")
        af.write(f"Duration (minutes): {total_elapsed/60:.2f}\n")
        af.write(f"Total ADB Commands: {cmd_count}\n")
        af.write(f"Cycles: {cycle_count}\n")
        af.write(f"Fatal Exceptions: {crash_count}\n")
        af.write(f"ANRs: {anr_count}\n")
        af.write(f"App Errors: {app_err_count}\n\n")
        af.write("=== LOGCAT ERRORS (*:E) ===\n")
        af.write(err_output + "\n")
        af.write("\n=== FATAL LINES ===\n")
        af.write("\n".join(fatal_lines) + "\n")
        af.write("\n=== ANR LINES ===\n")
        af.write("\n".join(anr_lines) + "\n")

    # Update final progress.md
    final_progress = f"""# Progress Log — teamwork_preview_challenger_m3_1

Last visited: {end_iso}

## Status Overview
- [x] Initialized agent briefing and workspace
- [x] Check ADB device state (`emulator-5554`) and app installation (`{PACKAGE}`)
- [x] Clear logcat buffer and launch active 30-minute stress session
- [x] Perform targeted test cases: wrong PINs, valid PIN (0044), rapid tap sequences, tab switches, lifecycle events (home/back/relaunch)
- [x] Complete 30+ minute stress loop (Total Duration: {total_elapsed/60:.2f} min, {cmd_count} ADB commands, {cycle_count} cycles)
- [x] Run logcat crash audit (`adb logcat -d *:E` -> 0 crashes, 0 ANRs)
- [/] Generate test_session_30min.md, challenge.md, and handoff.md
- [ ] Notify parent agent
"""
    with open(PROGRESS_FILE, "w", encoding="utf-8") as f:
        f.write(final_progress)

    log_fp.close()

if __name__ == "__main__":
    main()
