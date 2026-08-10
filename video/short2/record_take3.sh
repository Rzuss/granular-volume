#!/bin/bash
# Take 2, with VERIFIED pre-roll gates: the dial must be OFF and the home screen
# clean before rolling. The tuck keeps the whole dial on screen.
set -e
export MSYS_NO_PATHCONV=1
ADB=/d/Android/sdk/platform-tools/adb.exe
OUT="/d/Claude Projects/claude/Volume control/video/short2/take_raw3.mp4"
SCRATCH="C:/Users/rotem/AppData/Local/Temp/claude/D--Claude-Projects-claude-Volume-control/660f28e4-0b02-41c3-8a03-4cef56c6a00f/scratchpad"

TILE_X=285;  TILE_Y=207
UP_X=114;    UP_Y=505
DOWN_X=114;  DOWN_Y=1078
HANDLE_X=114; HANDLE_Y=433

dial_on () {  # returns 0 if the dial overlay is visible (samples its close-button ring)
  $ADB exec-out screencap -p > "$SCRATCH/vid/gate.png"
  python - <<EOF
from PIL import Image
im = Image.open(r"$SCRATCH/vid/gate.png").convert("RGB")
# the dial body occupies x 25..190, y 340..1300 when docked top-left.
# sample a grid; the dial pill is a dark navy overlay ~ (30..60, 34..66, 60..95)
hits = 0
for y in range(340, 2360, 40):
    for x in range(40, 180, 20):
        r, g, b = im.getpixel((x, y))
        if 20 <= r <= 70 and 24 <= g <= 75 and 45 <= b <= 110 and b > r + 12:
            hits += 1
print(hits)
exit(0 if hits > 40 else 1)
EOF
}

echo "== gate 1: home, and make sure the dial is OFF =="
$ADB shell input keyevent KEYCODE_HOME; sleep 1.5
for attempt in 1 2 3; do
  if dial_on; then
    echo "   dial is ON, toggling via tile (attempt $attempt)"
    $ADB shell cmd statusbar expand-settings; sleep 3.0
    $ADB shell input tap $TILE_X $TILE_Y; sleep 1.5
    $ADB shell cmd statusbar collapse; sleep 2.5
  else
    echo "   dial is OFF"
    break
  fi
done
if dial_on; then echo "FATAL: dial still on"; exit 1; fi

echo "== gate 2: media volume to EXACTLY 11 =="
python volgate.py 11
sleep 5.5   # native panel fades fully

echo "== rolling =="
$ADB shell screenrecord --bit-rate 16000000 --time-limit 60 //sdcard/take.mp4 &
REC=$!
sleep 2.5                                # beat 0: clean home, nothing on screen

echo "-- beat 1: Android's own slider walks down to its floor"
for i in $(seq 1 11); do
  $ADB shell input keyevent KEYCODE_VOLUME_DOWN
  sleep 0.42
done
sleep 2.0                                # sit on the floor
sleep 2.8                                # panel fades

echo "-- beat 2: shade, tile ON, the dial appears"
$ADB shell cmd statusbar expand-settings
sleep 3.0
$ADB shell input tap $TILE_X $TILE_Y
sleep 1.6
$ADB shell cmd statusbar collapse
sleep 2.6

echo "-- beat 3: descent through the upper zone"
for i in 1 2 3; do
  $ADB shell input tap $DOWN_X $DOWN_Y
  sleep 0.85
done
sleep 1.4

echo "-- beat 4: crossing the orange line into the quiet zone"
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

echo "-- beat 6: tuck toward the corner, whole dial stays on screen"
$ADB shell input swipe $HANDLE_X $HANDLE_Y 60 1400 900
sleep 6.0

echo "== cut =="
$ADB shell pkill -SIGINT screenrecord >/dev/null 2>&1 || true
sleep 3
wait $REC 2>/dev/null || true
$ADB pull //sdcard/take.mp4 "D:\\Claude Projects\\claude\\Volume control\\video\\short2\\take_raw3.mp4"
$ADB shell rm //sdcard/take.mp4
echo "saved take_raw3.mp4"
