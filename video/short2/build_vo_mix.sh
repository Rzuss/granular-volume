#!/bin/bash
# Adds the voice-over to the finished full-range short.
# Video stream is reused untouched; the audio is rebuilt: music bed with a
# piecewise envelope (low under VO, deep duck at the payoff, swells in gaps),
# five VO lines placed on the beat windows, mastered back to -14 LUFS.
set -e
cd "/d/Claude Projects/claude/Volume control/video/short2"
FF="../../ffmpeg-bin/bin/ffmpeg.exe"
FPB="../../ffmpeg-bin/bin/ffprobe.exe"
IN="../../GranularVolume-FullRange-Short.mp4"
MUSIC="../assets/music_v3.wav"
OUT="../../GranularVolume-FullRange-Short-VO.mp4"
TOTAL=33.267

# ---- music envelope: piecewise linear between breakpoints (t, gain) ----
python - <<'EOF' > /tmp/env_expr.txt
pts = [(0.0,0.0),(0.7,0.78),(1.1,0.40),(6.8,0.40),(7.2,0.62),(8.4,0.62),(8.8,0.40),
       (11.9,0.40),(12.3,0.62),(13.1,0.62),(13.5,0.40),(18.9,0.40),(19.3,0.58),
       (20.2,0.58),(20.6,0.38),(22.2,0.38),(22.6,0.26),(28.2,0.26),(28.6,0.52),
       (29.5,0.52),(29.9,0.38),(32.3,0.38),(33.2,0.0)]
expr = f"{pts[-1][1]}"
for (t0,v0),(t1,v1) in reversed(list(zip(pts, pts[1:]))):
    seg = f"{v0}+({v1}-{v0})*(t-{t0})/({t1}-{t0})" if v1 != v0 else f"{v0}"
    expr = f"if(lt(t,{t1}),{seg},{expr})"
print(expr)
EOF
ENV=$(cat /tmp/env_expr.txt)

cat > /tmp/vo_mix.txt <<EOF
[1:a]atrim=0:$TOTAL,asetpts=PTS-STARTPTS,aformat=sample_rates=48000:channel_layouts=stereo,
volume='$ENV':eval=frame[mus];
[2:a]aformat=sample_rates=48000:channel_layouts=stereo,volume=1.35,adelay=800|800[v1];
[3:a]aformat=sample_rates=48000:channel_layouts=stereo,volume=1.35,adelay=8700|8700[v2];
[4:a]aformat=sample_rates=48000:channel_layouts=stereo,volume=1.35,adelay=13400|13400[v3];
[5:a]aformat=sample_rates=48000:channel_layouts=stereo,volume=1.35,adelay=20500|20500[v4];
[6:a]aformat=sample_rates=48000:channel_layouts=stereo,volume=1.35,adelay=29700|29700[v5];
[mus][v1][v2][v3][v4][v5]amix=inputs=6:normalize=0:duration=first[a]
EOF

"$FF" -y -i "$IN" -i "$MUSIC" \
  -i vo/l1.mp3 -i vo/l2.mp3 -i vo/l3.mp3 -i vo/l4.mp3 -i vo/l5.mp3 \
  -filter_complex_script /tmp/vo_mix.txt \
  -map 0:v -map "[a]" -c:v copy \
  -c:a aac -b:a 192k -ar 48000 -movflags +faststart -t "$TOTAL" \
  "/tmp/vo_premix.mp4" 2>&1 | tail -2

echo "== loudnorm -14 =="
"$FF" -y -i "/tmp/vo_premix.mp4" -af "loudnorm=I=-14:TP=-1.5:LRA=11" \
  -c:v copy -c:a aac -b:a 192k -ar 48000 -movflags +faststart \
  "$OUT" 2>&1 | tail -1

echo "== verify =="
"$FPB" -v error -show_entries format=duration:stream=codec_type,codec_name -of default=nw=1 "$OUT"
"$FF" -i "$OUT" -af "loudnorm=I=-14:TP=-1.5:print_format=summary" -f null - 2>&1 | grep -E "Input Integrated|Input True Peak"
