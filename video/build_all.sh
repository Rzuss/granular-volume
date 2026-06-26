#!/bin/bash

cd "/d/Claude Projects/claude/Volume control/video/assets"
FF="/d/Claude Projects/claude/Volume control/ffmpeg-bin/bin/ffmpeg.exe"
RAW="../raw"
OUT="../clips"
FONT="cap.ttf"

# Caption drawtext builder (white Segoe Semibold, dark chip box, near top)
cap () {
  echo "drawtext=fontfile=${FONT}:text='$1':fontcolor=white:fontsize=50:box=1:boxcolor=0x0a0e1d@0.55:boxborderw=26:x=(w-text_w)/2:y=196:line_spacing=8"
}

kenburns () { # $1 dur
  echo "scale=2160:4800,zoompan=z='1.0+0.07*on/($1*30-1)':d=$1*30:x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':fps=30:s=1080x2400"
}

enc () { echo "-c:v libx264 -pix_fmt yuv420p -preset medium -crf 18 -r 30"; }

echo "seg1 title"
"$FF" -y -loop 1 -i title_card.png -t 3.3 -vf "$(kenburns 3.3),fade=t=in:st=0:d=0.5,format=yuv420p" $(enc) "$OUT/seg1.mp4" 2>/dev/null

echo "seg2 onboarding"
"$FF" -y -loop 1 -i onboard.png -t 2.8 -vf "$(kenburns 2.8),$(cap 'Set up in seconds'),fade=t=in:st=0:d=0.4,format=yuv420p" $(enc) "$OUT/seg2.mp4" 2>/dev/null

echo "seg3 clip3 music"
"$FF" -y -ss 1.6 -t 4.2 -i "$RAW/clip3.mp4" -vf "scale=1080:2400,setsar=1,$(cap 'Adjust volume in any app'),format=yuv420p" -an $(enc) "$OUT/seg3.mp4" 2>/dev/null

echo "seg4 clip2 drag"
"$FF" -y -ss 1.0 -t 3.4 -i "$RAW/clip2.mp4" -vf "scale=1080:2400,setsar=1,$(cap 'Drag it anywhere you like'),format=yuv420p" -an $(enc) "$OUT/seg4.mp4" 2>/dev/null

echo "seg5 clip1 steps"
"$FF" -y -ss 1.5 -t 3.4 -i "$RAW/clip1.mp4" -vf "scale=1080:2400,setsar=1,$(cap 'Finer than the volume keys'),format=yuv420p" -an $(enc) "$OUT/seg5.mp4" 2>/dev/null

echo "seg6 clip4 notification"
"$FF" -y -ss 1.6 -t 2.8 -i "$RAW/clip4.mp4" -vf "scale=1080:2400,setsar=1,$(cap 'Always ready in the background'),format=yuv420p" -an $(enc) "$OUT/seg6.mp4" 2>/dev/null

echo "seg7 clip5 close"
"$FF" -y -ss 1.5 -t 3.0 -i "$RAW/clip5.mp4" -vf "scale=1080:2400,setsar=1,$(cap 'Close it with one tap'),format=yuv420p" -an $(enc) "$OUT/seg7.mp4" 2>/dev/null

echo "seg8 outro"
"$FF" -y -loop 1 -i outro_card.png -t 4.0 -vf "$(kenburns 4.0),fade=t=in:st=0:d=0.5,fade=t=out:st=3.5:d=0.5,format=yuv420p" $(enc) "$OUT/seg8.mp4" 2>/dev/null

echo "=== all segments ==="
for n in 1 2 3 4 5 6 7 8; do d=$("$FF" -i "$OUT/seg$n.mp4" 2>&1 | grep Duration | grep -oE '[0-9]+:[0-9]+:[0-9.]+' | head -1); echo "seg$n: $d"; done
