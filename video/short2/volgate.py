"""Drive STREAM_MUSIC to an exact index with per-press verification."""
import subprocess, sys, time

ADB = r"D:\Android\sdk\platform-tools\adb.exe"
TARGET = int(sys.argv[1]) if len(sys.argv) > 1 else 11

def vol():
    out = subprocess.run([ADB, "shell", "dumpsys", "audio"],
                         capture_output=True, text=True).stdout
    seen = False
    for ln in out.splitlines():
        if "STREAM_MUSIC" in ln:
            seen = True
        elif seen and "streamVolume" in ln:
            return int(ln.split(":")[1].strip())
    return None

for _ in range(40):
    v = vol()
    if v == TARGET:
        break
    key = "KEYCODE_VOLUME_UP" if v < TARGET else "KEYCODE_VOLUME_DOWN"
    subprocess.run([ADB, "shell", "input", "keyevent", key], capture_output=True)
    time.sleep(0.45)

final = vol()
print("volume locked at", final)
sys.exit(0 if final == TARGET else 1)
