#!/bin/bash
set -e
export MSYS_NO_PATHCONV=1
ADB=/d/Android/sdk/platform-tools/adb.exe
PKG=granularvolume.com
TILE=$PKG/com.granularvolume.service.GranularVolumeTileService
UP_X=114;    UP_Y=505
DOWN_X=114;  DOWN_Y=1078
HANDLE_X=114; HANDLE_Y=433

echo "== gate: volume exactly 11, panel faded =="
$ADB shell input keyevent KEYCODE_HOME; sleep 1.5
python volgate.py 11
sleep 5.5

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
$ADB pull //sdcard/take.mp4 "D:\Claude Projects\claude\Volume control\video\short2\take_raw4.mp4"
$ADB shell rm //sdcard/take.mp4
echo "saved take_raw4.mp4"
