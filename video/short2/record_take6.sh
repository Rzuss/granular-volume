#!/bin/bash
# Take 5: full state reset + assertions at every gate, including SILENT dumpsys
# checks mid-take (they do not touch the screen).
set -e
export MSYS_NO_PATHCONV=1
ADB=/d/Android/sdk/platform-tools/adb.exe
PKG=granularvolume.com
TILE=$PKG/com.granularvolume.service.GranularVolumeTileService
SCRATCH="C:/Users/rotem/AppData/Local/Temp/claude/D--Claude-Projects-claude-Volume-control/660f28e4-0b02-41c3-8a03-4cef56c6a00f/scratchpad"

UP_X=114;    UP_Y=505
DOWN_X=114;  DOWN_Y=1078
HANDLE_X=114; HANDLE_Y=433
CLOSE_X=117; CLOSE_Y=377

svc_running () {
  $ADB shell dumpsys activity services $PKG 2>/dev/null | grep -q "granularvolume.com/com.granularvolume.service.VolumeControlService"
}

dial_pixels () {
  $ADB exec-out screencap -p > "$SCRATCH/vid/gate5.png"
  python - <<'EOF'
from PIL import Image
im = Image.open(r"C:/Users/rotem/AppData/Local/Temp/claude/D--Claude-Projects-claude-Volume-control/660f28e4-0b02-41c3-8a03-4cef56c6a00f/scratchpad/vid/gate5.png").convert("RGB")
hits = 0
for y in range(330, 2390, 30):
    for x in range(2, 250, 12):
        r, g, b = im.getpixel((x, y))
        if 20 <= r <= 75 and 24 <= g <= 80 and 45 <= b <= 115 and b > r + 12:
            hits += 1
print(hits)
EOF
}

echo "== step 1: X tap: tooltip dismissed + service off, position untouched =="
$ADB shell input tap $CLOSE_X $CLOSE_Y
sleep 2

echo "== gate A: service must be OFF =="
if svc_running; then echo "FATAL: service still running"; exit 1; fi
echo "   service off"

echo "== gate B: volume to exactly 11 =="
python volgate.py 11
sleep 5.5

echo "== gate C: screen must be clean of the dial =="
PIX=$(dial_pixels)
echo "   dial pixels: $PIX"
if [ "$PIX" -gt 12 ]; then echo "FATAL: dial visible before roll"; exit 1; fi

echo "== gate D: tile responds to click-tile (toggle on, then off) =="
$ADB shell cmd statusbar add-tile $TILE
sleep 1
$ADB shell cmd statusbar click-tile $TILE; sleep 2
if ! svc_running; then echo "FATAL: tile does not start the service"; exit 1; fi
$ADB shell input tap $CLOSE_X $CLOSE_Y; sleep 2
if svc_running; then echo "FATAL: X did not stop the service"; exit 1; fi
echo "   tile verified, service off"

echo "== rolling =="
$ADB shell screenrecord --bit-rate 16000000 --time-limit 60 //sdcard/take.mp4 &
REC=$!
sleep 2.5

echo "-- beat 1: native slider walks to its floor"
for i in $(seq 1 11); do
  $ADB shell input keyevent KEYCODE_VOLUME_DOWN
  sleep 0.42
done
sleep 2.0
sleep 2.8

echo "-- beat 2: shade opens, tile turns on, dial appears"
$ADB shell cmd statusbar expand-settings
sleep 2.6
$ADB shell cmd statusbar click-tile $TILE
sleep 1.6
$ADB shell cmd statusbar collapse
sleep 2.6
if svc_running; then echo "   [mid-take assert] service ON"; else echo "FATAL: service did not start"; fi

echo "-- beat 3: descent through the upper zone"
for i in 1 2 3; do
  $ADB shell input tap $DOWN_X $DOWN_Y
  sleep 0.85
done
sleep 1.4

echo "-- beat 4: crossing the orange line"
for i in 1 2 3 4 5; do
  $ADB shell input tap $DOWN_X $DOWN_Y
  sleep 0.8
done
sleep 2.2

echo "-- beat 5: back up through the line"
for i in 1 2 3; do
  $ADB shell input tap $UP_X $UP_Y
  sleep 0.75
done
sleep 1.4

echo "-- beat 6: tuck down, fully on screen"
$ADB shell input swipe $HANDLE_X $HANDLE_Y 90 1250 900
sleep 6.0

echo "== cut =="
$ADB shell pkill -SIGINT screenrecord >/dev/null 2>&1 || true
sleep 3
wait $REC 2>/dev/null || true
$ADB pull //sdcard/take.mp4 "D:\\Claude Projects\\claude\\Volume control\\video\\short2\\take_raw6.mp4"
$ADB shell rm //sdcard/take.mp4
echo "saved take_raw6.mp4"
