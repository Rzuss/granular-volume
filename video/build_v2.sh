#!/bin/bash
# Studio-grade rebuild of the promo (v2).
# Upgrades over v1: crossfade transitions instead of hard cuts, captions that
# fade+slide in and fade out, unified color grade + gentle vignette, eased
# Ken Burns on stills, slow drift motion on screen recordings, silent by design
# (store promos autoplay muted; captions carry the message).

set -e
cd "/d/Claude Projects/claude/Volume control/video/assets"
FF="/d/Claude Projects/claude/Volume control/ffmpeg-bin/bin/ffmpeg.exe"
FPB="/d/Claude Projects/claude/Volume control/ffmpeg-bin/bin/ffprobe.exe"
RAW="../raw"
OUT="../clips_v2"
FONT="cap.ttf"
mkdir -p "$OUT"

# Unified grade applied to every segment: slight contrast+saturation lift,
# gentle vignette. Keeps all shots living in the same visual world.
GRADE="eq=contrast=1.06:saturation=1.12:brightness=0.01,vignette=PI/5"

# Animated caption: slides up 26px while fading in over 0.45s, fades out in
# the last 0.4s of the segment. $1=text $2=segment duration $3=base y (default 200)
cap () {
  local Y=${3:-200}
  echo "drawtext=fontfile=${FONT}:text='$1':fontcolor=white:fontsize=52:box=1:boxcolor=0x0a0e1d@0.50:boxborderw=28:x=(w-text_w)/2:y=$Y+26*(1-min(t/0.45\,1)):line_spacing=8:alpha='if(lt(t,0.45),t/0.45,if(gt(t,$2-0.4),($2-t)/0.4,1))'"
}

# Eased Ken Burns for stills (slow start, gains momentum). $1=duration
kb () {
  echo "scale=2160:4800,zoompan=z='1.0+0.085*pow(on/($1*30-1),1.4)':d=$1*30:x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':fps=30,scale=1080:2400,setsar=1"
}

# Slow drift on screen recordings: scale up 5% then pan down slightly.
# fps=30 FIRST so -t (output side) cuts an exact frame count. $1=duration
drift () {
  echo "fps=30,scale=1134:2520,crop=1080:2400:x='(iw-ow)/2':y='(ih-oh)*0.5*(1+0.4*t/$1)',setsar=1"
}

ENC="-c:v libx264 -pix_fmt yuv420p -preset slow -crf 17 -r 30"

echo "seg1 title (3.4s)"
"$FF" -y -loop 1 -i title_card.png -vf "$(kb 3.4),$GRADE,fade=t=in:st=0:d=0.6,format=yuv420p" -t 3.4 $ENC "$OUT/seg1.mp4" 2>/dev/null

echo "seg2 onboarding (3.0s)"
"$FF" -y -loop 1 -i onboard.png -vf "$(kb 3.0),$(cap 'Set up in seconds' 3.0),$GRADE,format=yuv420p" -t 3.0 $ENC "$OUT/seg2.mp4" 2>/dev/null

echo "seg3 clip3 music (4.2s)"
"$FF" -y -ss 1.6 -i "$RAW/clip3.mp4" -vf "$(drift 4.2),$(cap 'Adjust volume in any app' 4.2),$GRADE,format=yuv420p" -an -t 4.2 $ENC "$OUT/seg3.mp4" 2>/dev/null

echo "seg4 clip2 drag (3.4s)"
"$FF" -y -ss 1.0 -i "$RAW/clip2.mp4" -vf "$(drift 3.4),$(cap 'Drag it anywhere you like' 3.4),$GRADE,format=yuv420p" -an -t 3.4 $ENC "$OUT/seg4.mp4" 2>/dev/null

echo "seg5 clip1 steps (3.4s)"
"$FF" -y -ss 1.5 -i "$RAW/clip1.mp4" -vf "$(drift 3.4),$(cap 'Quieter than your phone allows' 3.4),$GRADE,format=yuv420p" -an -t 3.4 $ENC "$OUT/seg5.mp4" 2>/dev/null

echo "seg6 clip4b notification (3.0s, clean take, caption in empty area)"
"$FF" -y -ss 1.6 -i "$RAW/clip4b.mp4" -vf "$(drift 3.0),$(cap 'Always ready in the background' 3.0 1180),$GRADE,format=yuv420p" -an -t 3.0 $ENC "$OUT/seg6.mp4" 2>/dev/null

echo "seg7 clip5 close (3.2s)"
"$FF" -y -ss 1.5 -i "$RAW/clip5.mp4" -vf "$(drift 3.2),$(cap 'Close it with one tap' 3.2),$GRADE,format=yuv420p" -an -t 3.2 $ENC "$OUT/seg7.mp4" 2>/dev/null

echo "seg8 outro (4.2s)"
"$FF" -y -loop 1 -i outro_card.png -vf "$(kb 4.2),$GRADE,fade=t=out:st=3.6:d=0.6,format=yuv420p" -t 4.2 $ENC "$OUT/seg8.mp4" 2>/dev/null

echo "=== measuring actual segment durations ==="
declare -a DUR
for n in 1 2 3 4 5 6 7 8; do
  DUR[$n]=$("$FPB" -v quiet -show_entries stream=duration -select_streams v -of csv=p=0 "$OUT/seg$n.mp4")
  echo "seg$n: ${DUR[$n]}"
done

echo "=== crossfade assembly (offsets computed from real durations) ==="
FADE=0.5
# offset[i] = total running length so far - FADE; one python call for all
read O1 O2 O3 O4 O5 O6 O7 <<< $("/c/Python314/python" -c "
d=[${DUR[1]},${DUR[2]},${DUR[3]},${DUR[4]},${DUR[5]},${DUR[6]},${DUR[7]},${DUR[8]}]
f=$FADE
offs=[]
total=d[0]
for i in range(1,8):
    off=total-f
    offs.append(round(off,3))
    total=off+d[i]
print(' '.join(str(o) for o in offs))
")
echo "offsets: $O1 $O2 $O3 $O4 $O5 $O6 $O7"

"$FF" -y \
  -i "$OUT/seg1.mp4" -i "$OUT/seg2.mp4" -i "$OUT/seg3.mp4" -i "$OUT/seg4.mp4" \
  -i "$OUT/seg5.mp4" -i "$OUT/seg6.mp4" -i "$OUT/seg7.mp4" -i "$OUT/seg8.mp4" \
  -filter_complex "\
[0:v][1:v]xfade=transition=fade:duration=$FADE:offset=$O1[x1];\
[x1][2:v]xfade=transition=fade:duration=$FADE:offset=$O2[x2];\
[x2][3:v]xfade=transition=fade:duration=$FADE:offset=$O3[x3];\
[x3][4:v]xfade=transition=fade:duration=$FADE:offset=$O4[x4];\
[x4][5:v]xfade=transition=fade:duration=$FADE:offset=$O5[x5];\
[x5][6:v]xfade=transition=fade:duration=$FADE:offset=$O6[x6];\
[x6][7:v]xfade=transition=fade:duration=$FADE:offset=$O7,format=yuv420p[v]" \
  -map "[v]" -c:v libx264 -pix_fmt yuv420p -preset slow -crf 17 -r 30 -movflags +faststart \
  "../GranularVolume-promo-v2.mp4" 2>&1 | tail -2

echo "=== done ==="
