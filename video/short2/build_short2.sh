#!/bin/bash
# The 1.4.0 full-range short (1080x1920), same premium frame as the published cut:
# phone in rounded mask + glow on the brand bg, burned captions, punch-in on the
# descent payoff, brand music with a duck at the crossing, loudnorm to -14 LUFS.
#
# Beats (source = take_raw9.mp4, 1080x2400 emulator take, new dial ONLY):
#   A  1.5-11.5  @1.20  hook: Android's own slider walks to its floor      capA+capB
#   B 16.6-23.4  @1.35  the turn: shade, tile tap                          capC
#   C 23.8-33.6  @1.35  rise: -15 dB up through the line to 57%            capD+capF
#   D 33.8-48.0  @1.40  the payoff: dive through the line to -30 dB        capE (punch-in)
#   CTA 3.6s
set -e
cd "/d/Claude Projects/claude/Volume control/video/short2"
FF="/d/Claude Projects/claude/Volume control/ffmpeg-bin/bin/ffmpeg.exe"
FPB="/d/Claude Projects/claude/Volume control/ffmpeg-bin/bin/ffprobe.exe"
TAKE="take_raw9.mp4"
MUSIC="../assets/music_v3.wav"
A="assets"; C="seg"
mkdir -p "$C"
ENC="-c:v libx264 -pix_fmt yuv420p -preset slow -crf 17 -r 30"
PX=184; PY=286

phone_static () {  # $1 speed
cat <<EOF
[1:v]fps=30,setpts=(PTS-STARTPTS)/$1,scale=713:1584:flags=lanczos,unsharp=5:5:0.7:5:5:0.0,setsar=1[pv];
[2:v]format=gray[mk];
[pv][mk]alphamerge[ph];
[0:v][3:v]overlay=0:0[bgg];
[bgg][ph]overlay=$PX:$PY[stage];
EOF
}
punch () {  # $1 speed $2 zoom-expr  (focus on the dial, left side of the phone)
cat <<EOF
[1:v]fps=30,setpts=(PTS-STARTPTS)/$1,scale=713:1584:flags=lanczos,unsharp=5:5:0.7:5:5:0.0,setsar=1[pv];
[2:v]format=gray[mk];
[pv][mk]alphamerge[ph];
[0:v][3:v]overlay=0:0[bgg];
[bgg][ph]overlay=$PX:$PY,scale=2160:3840:flags=lanczos,
zoompan=z='$2':x='(iw-iw/zoom)*0.20':y='(ih-ih/zoom)*0.46':d=1:s=2160x3840:fps=30,
scale=1080:1920:flags=lanczos[stage];
EOF
}

echo "== A: hook, slider walks to the floor (src 1.5-11.5 @1.2 = 8.33s) =="
{ phone_static 1.2; cat <<'EOF'
[4:v]format=rgba,fade=t=in:st=0.2:d=0.4:alpha=1,fade=t=out:st=4.2:d=0.4:alpha=1[ca];
[5:v]format=rgba,fade=t=in:st=4.9:d=0.4:alpha=1[cb];
[stage][ca]overlay=0:0[o1];
[o1][cb]overlay=0:0,format=yuv420p[v]
EOF
} > /tmp/sa.txt
"$FF" -y -loop 1 -t 8.33 -i "$A/bg.png" -ss 1.5 -t 10.0 -i "$TAKE" \
  -loop 1 -t 8.33 -i "$A/phone_mask.png" -loop 1 -t 8.33 -i "$A/phone_glow.png" \
  -loop 1 -t 8.33 -i "$A/capA.png" -loop 1 -t 8.33 -i "$A/capB.png" \
  -filter_complex_script /tmp/sa.txt -map "[v]" -t 8.33 $ENC -an "$C/a.mp4" 2>&1 | tail -1

echo "== B: the turn, tile tap (src 16.6-23.4 @1.35 = 5.04s) =="
{ phone_static 1.35; cat <<'EOF'
[4:v]format=rgba,fade=t=in:st=0.4:d=0.4:alpha=1,fade=t=out:st=4.3:d=0.35:alpha=1[c];
[stage][c]overlay=0:0,format=yuv420p[v]
EOF
} > /tmp/sb.txt
"$FF" -y -loop 1 -t 5.04 -i "$A/bg.png" -ss 16.6 -t 6.8 -i "$TAKE" \
  -loop 1 -t 5.04 -i "$A/phone_mask.png" -loop 1 -t 5.04 -i "$A/phone_glow.png" \
  -loop 1 -t 5.04 -i "$A/capC.png" \
  -filter_complex_script /tmp/sb.txt -map "[v]" -t 5.04 $ENC -an "$C/b.mp4" 2>&1 | tail -1

echo "== Cr: rise through the whole range (src 23.8-33.6 @1.35 = 7.26s) =="
{ phone_static 1.35; cat <<'EOF'
[4:v]format=rgba,fade=t=in:st=0.3:d=0.4:alpha=1,fade=t=out:st=3.2:d=0.4:alpha=1[cd];
[5:v]format=rgba,fade=t=in:st=3.8:d=0.4:alpha=1,fade=t=out:st=6.7:d=0.4:alpha=1[cf];
[stage][cd]overlay=0:0[o1];
[o1][cf]overlay=0:0,format=yuv420p[v]
EOF
} > /tmp/sc.txt
"$FF" -y -loop 1 -t 7.26 -i "$A/bg.png" -ss 23.8 -t 9.8 -i "$TAKE" \
  -loop 1 -t 7.26 -i "$A/phone_mask.png" -loop 1 -t 7.26 -i "$A/phone_glow.png" \
  -loop 1 -t 7.26 -i "$A/capD.png" -loop 1 -t 7.26 -i "$A/capF.png" \
  -filter_complex_script /tmp/sc.txt -map "[v]" -t 7.26 $ENC -an "$C/c.mp4" 2>&1 | tail -1

echo "== D: the dive to -30 dB, punch-IN (src 33.8-48.0 @1.4 = 10.14s) =="
{ punch 1.4 "min(1.20,1+0.20*on/210)"; cat <<'EOF'
[4:v]format=rgba,fade=t=in:st=0.8:d=0.5:alpha=1,fade=t=out:st=8.9:d=0.45:alpha=1[c];
[stage][c]overlay=0:0,format=yuv420p[v]
EOF
} > /tmp/sd.txt
"$FF" -y -loop 1 -t 10.14 -i "$A/bg.png" -ss 33.8 -t 14.2 -i "$TAKE" \
  -loop 1 -t 10.14 -i "$A/phone_mask.png" -loop 1 -t 10.14 -i "$A/phone_glow.png" \
  -loop 1 -t 10.14 -i "$A/capE.png" \
  -filter_complex_script /tmp/sd.txt -map "[v]" -t 10.14 $ENC -an "$C/d.mp4" 2>&1 | tail -1

echo "== CTA =="
"$FF" -y -loop 1 -t 3.6 -i "$A/cta.png" \
  -vf "fps=30,scale=2160:3840:flags=lanczos,setsar=1,zoompan=z='min(1.06,1+0.06*on/108)':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d=1:s=2160x3840:fps=30,scale=1080:1920:flags=lanczos,format=yuv420p" \
  -t 3.6 $ENC -an "$C/cta.mp4" 2>&1 | tail -1

echo "== durations =="
AD=$("$FPB" -v error -show_entries format=duration -of csv=p=0 "$C/a.mp4")
BD=$("$FPB" -v error -show_entries format=duration -of csv=p=0 "$C/b.mp4")
CD=$("$FPB" -v error -show_entries format=duration -of csv=p=0 "$C/c.mp4")
DD=$("$FPB" -v error -show_entries format=duration -of csv=p=0 "$C/d.mp4")
echo "a=$AD b=$BD c=$CD d=$DD"

echo "== assemble: A -.25- B -.25- C -.2- D -.4- CTA =="
O1=$(python -c "print(round($AD-0.25,3))")
O2=$(python -c "print(round($AD+$BD-0.25-0.25,3))")
O3=$(python -c "print(round($AD+$BD+$CD-0.25-0.25-0.2,3))")
O4=$(python -c "print(round($AD+$BD+$CD+$DD-0.25-0.25-0.2-0.4,3))")
TOTAL=$(python -c "print(round($AD+$BD+$CD+$DD+3.6-0.25-0.25-0.2-0.4,3))")
echo "offsets: $O1 $O2 $O3 $O4 total=$TOTAL"
cat > /tmp/sv.txt <<EOF
[0:v][1:v]xfade=transition=fade:duration=0.25:offset=$O1[x1];
[x1][2:v]xfade=transition=fade:duration=0.25:offset=$O2[x2];
[x2][3:v]xfade=transition=fade:duration=0.2:offset=$O3[x3];
[x3][4:v]xfade=transition=fade:duration=0.4:offset=$O4,format=yuv420p[v]
EOF
# duck: the line-crossing lands around O3+4.5; drop to 0.32 there, recover for the CTA
DUCK0=$(python -c "print(round($O3+2.5,2))")
DUCK1=$(python -c "print(round($O3+4.6,2))")
DUCK2=$(python -c "print(round($O3+8.2,2))")
DUCK3=$(python -c "print(round($O3+9.6,2))")
cat > /tmp/sa2.txt <<EOF
[5:a]atrim=0:$TOTAL,asetpts=PTS-STARTPTS,
volume='if(lt(t,$DUCK0),1.0,
 if(lt(t,$DUCK1),1.0+(0.32-1.0)*(t-$DUCK0)/($DUCK1-$DUCK0),
 if(lt(t,$DUCK2),0.32,
 if(lt(t,$DUCK3),0.32+(0.9-0.32)*(t-$DUCK2)/($DUCK3-$DUCK2),
 0.9))))':eval=frame,
afade=t=in:st=0:d=0.7,afade=t=out:st=$(python -c "print(round($TOTAL-0.9,3))"):d=0.9[a]
EOF
"$FF" -y -i "$C/a.mp4" -i "$C/b.mp4" -i "$C/c.mp4" -i "$C/d.mp4" -i "$C/cta.mp4" -i "$MUSIC" \
  -filter_complex_script /tmp/sv.txt -filter_complex_script /tmp/sa2.txt \
  -map "[v]" -map "[a]" \
  -c:v libx264 -pix_fmt yuv420p -preset slow -crf 17 -r 30 \
  -c:a aac -b:a 192k -ar 48000 -movflags +faststart -t "$TOTAL" \
  "/tmp/short2_premix.mp4" 2>&1 | tail -2

echo "== loudnorm -14 =="
"$FF" -y -i "/tmp/short2_premix.mp4" -af "loudnorm=I=-14:TP=-1.5:LRA=11" \
  -c:v copy -c:a aac -b:a 192k -ar 48000 -movflags +faststart \
  "../../GranularVolume-FullRange-Short.mp4" 2>&1 | tail -1

echo "== verify =="
"$FPB" -v error -show_entries format=duration:stream=codec_type,codec_name,width,height -of default=nw=1 "../../GranularVolume-FullRange-Short.mp4"
