import subprocess
import time
import xml.etree.ElementTree as ET
import sys
import re

ADB_DEVICE = "emulator-5554"
PACKAGE = "com.example.facerecognitionimages"

def run_adb(cmd):
    full_cmd = ["adb", "-s", ADB_DEVICE] + cmd
    res = subprocess.run(full_cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    return res.stdout.strip()

def clear_logcat():
    run_adb(["logcat", "-c"])

def get_logcat():
    return run_adb(["logcat", "-d"])

def dump_ui():
    run_adb(["shell", "uiautomator", "dump", "/sdcard/ui_test.xml"])
    xml_str = run_adb(["shell", "cat", "/sdcard/ui_test.xml"])
    return xml_str

def force_stop_and_start():
    run_adb(["shell", "am", "force-stop", PACKAGE])
    time.sleep(1)
    run_adb(["shell", "am", "start", "-n", f"{PACKAGE}/.LoginActivity"])
    time.sleep(2)

def clear_pin_input():
    # Tap input field
    run_adb(["shell", "input", "tap", "540", "1000"])
    time.sleep(0.3)
    # Select all or send backspaces
    for _ in range(15):
        run_adb(["shell", "input", "keyevent", "KEYCODE_DEL"])

def enter_pin(pin_text):
    clear_pin_input()
    run_adb(["shell", "input", "text", pin_text])
    time.sleep(0.3)

def click_login():
    run_adb(["shell", "input", "tap", "540", "1200"])
    time.sleep(0.5)

def check_crashes():
    logs = get_logcat()
    crash_patterns = [
        r"FATAL EXCEPTION",
        r"AndroidRuntime: FATAL",
        r"Process: com.example.facerecognitionimages.*Crash",
        r"ANR in com.example.facerecognitionimages"
    ]
    crashes = []
    for line in logs.splitlines():
        for pat in crash_patterns:
            if re.search(pat, line, re.IGNORECASE):
                crashes.append(line)
    return crashes

def get_current_activity():
    output = run_adb(["shell", "dumpsys", "activity", "activities"])
    for line in output.splitlines():
        if "mResumedActivity" in line or "topResumedActivity" in line:
            return line
    return ""

def run_tests():
    print("=== STARTING EMPIRICAL STRESS TESTS ===")
    clear_logcat()
    
    print("\n--- Test 1: PIN Login Stress Tests ---")
    force_stop_and_start()
    
    # 1a. Wrong PIN (9999)
    print("Subtest 1a: Testing Wrong PIN (9999)...")
    enter_pin("9999")
    click_login()
    xml1 = dump_ui()
    act1 = get_current_activity()
    print(f"Current Activity: {act1}")
    assert "LoginActivity" in act1, "Expected to stay on LoginActivity for wrong PIN"
    crashes = check_crashes()
    print(f"Crashes so far: {len(crashes)}")
    assert len(crashes) == 0, f"Crashes detected during wrong PIN test: {crashes}"
    print("Subtest 1a PASSED: Wrong PIN handling verified without crash.")

    # 1b. Short PIN (12)
    print("Subtest 1b: Testing Short PIN (12)...")
    enter_pin("12")
    click_login()
    xml2 = dump_ui()
    act2 = get_current_activity()
    print(f"Current Activity: {act2}")
    assert "LoginActivity" in act2, "Expected to stay on LoginActivity for short PIN"
    crashes = check_crashes()
    print(f"Crashes so far: {len(crashes)}")
    assert len(crashes) == 0, f"Crashes detected during short PIN test: {crashes}"
    print("Subtest 1b PASSED: Short PIN handling verified without crash.")

    # 1c. Non-numeric input (abc)
    print("Subtest 1c: Testing Non-numeric input (abc)...")
    enter_pin("abc")
    click_login()
    xml3 = dump_ui()
    act3 = get_current_activity()
    print(f"Current Activity: {act3}")
    assert "LoginActivity" in act3, "Expected to stay on LoginActivity for non-numeric PIN"
    crashes = check_crashes()
    print(f"Crashes so far: {len(crashes)}")
    assert len(crashes) == 0, f"Crashes detected during non-numeric PIN test: {crashes}"
    print("Subtest 1c PASSED: Non-numeric PIN handling verified without crash.")

    # 1d. Rapid multi-tap on Login button with invalid pin
    print("Subtest 1d: Testing Rapid multi-tap on Login button...")
    enter_pin("9999")
    for i in range(25):
        run_adb(["shell", "input", "tap", "540", "1200"])
    time.sleep(1)
    act4 = get_current_activity()
    print(f"Current Activity after rapid login taps: {act4}")
    crashes = check_crashes()
    print(f"Crashes so far: {len(crashes)}")
    assert len(crashes) == 0, f"Crashes detected during rapid login taps: {crashes}"
    print("Subtest 1d PASSED: Rapid multi-tap on Login button handled cleanly.")

    # 1e. Valid PIN (0044)
    print("Subtest 1e: Testing Valid PIN (0044)...")
    enter_pin("0044")
    click_login()
    time.sleep(2)
    act5 = get_current_activity()
    print(f"Current Activity after valid login: {act5}")
    assert "MainActivity" in act5, f"Expected MainActivity after valid login, got: {act5}"
    crashes = check_crashes()
    print(f"Crashes so far: {len(crashes)}")
    assert len(crashes) == 0, f"Crashes detected during valid PIN login: {crashes}"
    print("Subtest 1e PASSED: Valid PIN (0044) logged in successfully to MainActivity.")

    print("\n--- Test 2: Rapid Multi-Tap Stress Tests on UI Controls (MainActivity) ---")
    xml_main = dump_ui()
    # Bottom tabs approximate coordinates on 1080x2400 screen:
    # Attendance: (135, 2260)
    # Members: (405, 2260)
    # Logs: (675, 2260)
    # Settings: (945, 2260)
    # FAB / Action buttons: (950, 2000), (540, 1500)
    tab_coords = [
        (135, 2260),
        (405, 2260),
        (675, 2260),
        (945, 2260)
    ]

    print("Performing 80 rapid tab switches...")
    for cycle in range(20):
        for x, y in tab_coords:
            run_adb(["shell", "input", "tap", str(x), str(y)])

    time.sleep(1)
    act6 = get_current_activity()
    print(f"Current Activity after rapid tab switching: {act6}")
    crashes = check_crashes()
    print(f"Crashes so far: {len(crashes)}")
    assert len(crashes) == 0, f"Crashes detected during rapid tab switching: {crashes}"

    print("Performing rapid multi-taps on content buttons and interactive controls...")
    # Click members tab, then rapid tap on list items / add member button
    run_adb(["shell", "input", "tap", "405", "2260"]) # Members tab
    time.sleep(0.5)
    for _ in range(30):
        run_adb(["shell", "input", "tap", "950", "2000"]) # Add member FAB / button area
    time.sleep(1)
    
    # Send BACK key in case a dialog or new activity opened
    run_adb(["shell", "input", "keyevent", "KEYCODE_BACK"])
    time.sleep(0.5)

    act7 = get_current_activity()
    print(f"Current Activity after multi-tap on controls: {act7}")

    print("\n--- Test 3: Final adb logcat Crash Verification ---")
    final_crashes = check_crashes()
    print(f"Total Crashes Found in Logcat: {len(final_crashes)}")
    if final_crashes:
        print("CRASH LOGS DETECTED:")
        for c in final_crashes:
            print(c)
        sys.exit(1)
    else:
        print("SUCCESS: 0 crashes found in adb logcat across all stress tests!")

if __name__ == "__main__":
    run_tests()
