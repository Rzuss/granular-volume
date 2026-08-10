"""Locate the Quiet Dial QS tile center via uiautomator. Prints 'X Y' or exits 1."""
import re, subprocess, sys

ADB = r"D:\Android\sdk\platform-tools\adb.exe"

subprocess.run([ADB, "shell", "uiautomator", "dump", "/sdcard/ui.xml"],
               capture_output=True)
xml = subprocess.run([ADB, "shell", "cat", "/sdcard/ui.xml"],
                     capture_output=True, text=True, encoding="utf-8",
                     errors="replace").stdout
m = (re.search(r'content-desc="Quiet Dial[^"]*"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
     or re.search(r'text="Quiet Dial"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml))
if not m:
    sys.exit(1)
x1, y1, x2, y2 = map(int, m.groups())
print((x1 + x2) // 2, (y1 + y2) // 2)
