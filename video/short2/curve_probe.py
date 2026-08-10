"""Read the media volume curve per output route, exactly the way VolumeCurve.read does.

VolumeCurve.read calls getStreamVolumeDb(STREAM_MUSIC, i, deviceType) for every index and
sets minAudibleIndex = max(1, getStreamMinVolume()). The open question from the 2026-08-10
field report is whether index 1 is actually audible on a Bluetooth route, or whether the
curve bottoms out so steeply that the lowest upper-zone rungs land on silence.

We cannot call getStreamVolumeDb from adb, but AudioFlinger's own dump exposes the same
per-index gain table it feeds that API, so we read it there instead.
"""
import re
import subprocess

ADB = r"D:\Android\sdk\platform-tools\adb.exe"


def sh(*args):
    return subprocess.run([ADB, "shell", *args], capture_output=True, text=True,
                          encoding="utf-8", errors="replace").stdout


def stream_block(dump, name):
    m = re.search(rf"- {name}:(.*?)(?=\n- STREAM_|\Z)", dump, re.S)
    return m.group(1) if m else ""


def main():
    audio = sh("dumpsys", "audio")
    music = stream_block(audio, "STREAM_MUSIC")

    mn = re.search(r"Min:\s*(\d+)", music)
    mx = re.search(r"Max:\s*(\d+)", music)
    cur = re.search(r"streamVolume:\s*(\d+)", music)
    devices = re.search(r"Devices:\s*(.+)", music)
    print("STREAM_MUSIC")
    print("  min index :", mn.group(1) if mn else "?")
    print("  max index :", mx.group(1) if mx else "?")
    print("  current   :", cur.group(1) if cur else "?")
    print("  route     :", devices.group(1).strip() if devices else "?")

    # Per-device volume table: "Current: 2 (speaker): 1, 80 (bt_a2dp): 2, ..."
    per_dev = re.search(r"Current:\s*(.+)", music)
    if per_dev:
        print("  per-route indices:", per_dev.group(1).strip())

    print()
    print("Volume curves AudioFlinger exposes (the source getStreamVolumeDb reads):")
    policy = sh("dumpsys", "audio_policy") or ""
    hits = re.findall(r"(?:curve|Curve|volume curve)[^\n]*\n(?:[^\n]*\n){0,6}", policy)
    if hits:
        for h in hits[:4]:
            print(h.rstrip())
    else:
        print("  (audio_policy exposes no curve table on this build)")


if __name__ == "__main__":
    main()
