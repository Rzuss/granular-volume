#!/bin/bash
# ONE continuous take of the v1.4.0 FULL-RANGE story, on the emulator (1080x2400):
#   clean home -> Android's own slider walks to its floor -> still too loud
#   -> QS shade -> tile tap -> the full-range dial appears at ~70%
#   -> descent through the UPPER zone (normal volume, on screen)
#   -> crossing the ORANGE line into the quiet zone, down to deep dB
#   -> back UP through the line (two-way, replaces broken buttons)
#   -> tuck into the corner.
# The old dial never appears anywhere in this take.
set -e
export MSYS_NO_PATHCONV=1
ADB=/d/Android/sdk/platform-tools/adb.exe
OUT="/d/Claude Projects/claude/Volume control/video/short2/take_raw.mp4"

# --- verified coordinates (1080x2400, dial on LEFT edge) ---
TILE_X=285;  TILE_Y=207     # Quiet Dial tile, first slot in the QS strip
UP_X=114;    UP_Y=505       # up chevron
DOWN_X=114;  DOWN_Y=1078    # down chevron
HANDLE_X=114; HANDLE_Y=433  # drag handle

echo "== pre-roll: dial OFF via tile, keep saved level =="
$ADB shell cmd statusbar expand-settings; sleep 2
$ADB shell input tap $TILE_X $TILE_Y; sleep 1.5
$ADB shell cmd statusbar collapse; sleep 2

echo "== pre-roll: raise media volume to 12 so the walk to the floor is long =="
for i in $(seq 1 11); do $ADB shell input keyevent KEYCODE_VOLUME_UP; sleep 0.15; done
$ADB shell dumpsys audio | grep -A6 "STREAM_MUSIC:" | grep streamVolume
sleep 4.5   # let the native panel fade before rolling

echo "== rolling =="
$ADB shell screenrecord --bit-rate 16000000 --time-limit 60 //sdcard/take.mp4 &
REC=$!
sleep 2.5                                # beat 0: clean home

echo "-- beat 1: Android's own slider walks down to its floor"
for i in $(seq 1 12); do
  $ADB shell input keyevent KEYCODE_VOLUME_DOWN
  sleep 0.42
done
sleep 2.0                                # sit on the floor: still too loud
sleep 2.8                                # panel fades away

echo "-- beat 2: shade, tile, the dial appears"
$ADB shell cmd statusbar expand-settings
sleep 2.2
$ADB shell input tap $TILE_X $TILE_Y
sleep 1.6
$ADB shell cmd statusbar collapse
sleep 2.6                                # dial appears at its restored ~70%

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
sleep 2.2                                # deep in the quiet zone

echo "-- beat 5: back up through the line (two-way, on screen)"
for i in 1 2 3; do
  $ADB shell input tap $UP_X $UP_Y
  sleep 0.75
done
sleep 1.4

echo "-- beat 6: tuck into the corner"
$ADB shell input swipe $HANDLE_X $HANDLE_Y 62 1880 900
sleep 6.0                                # hold on the tucked, idle dial

echo "== cut =="
$ADB shell pkill -SIGINT screenrecord >/dev/null 2>&1 || true
sleep 3
wait $REC 2>/dev/null || true
$ADB pull //sdcard/take.mp4 "$OUT"
$ADB shell rm //sdcard/take.mp4
echo "saved: $OUT"
