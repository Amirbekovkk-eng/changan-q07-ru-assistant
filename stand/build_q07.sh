#!/usr/bin/env bash
# Q07 builder: first-stage adaptation of the A06 Russian Assistant to Changan Q07.
#
# IMPORTANT:
# - Input is the STOCK Q07 SpeechAssistant.apk pulled from the user's head unit.
# - The stock APK is never modified in place.
# - Q07 is Android 11 (API 30), package com.incall.apps.speechassistant.
# - Q07's SpeechAssistant application classes are in classes5.dex (verified on stock APK).
# - We intentionally do NOT hook SrBaseSession here: its presence/signature on Q07 has not been proven from the stock Q07 DEX.
#   Native Q07 ASR hook is a separate work item.
#
# First-stage goal:
#   Russian text -> RuBridge scenario/ru2zh -> Q07 NluManager.onFinalAsrResult
#   -> stock Q07 NLU/arbitration/vehicle pipeline.
#
# Test trigger:
#   adb shell am start -n com.incall.apps.speechassistant/.ui.MainActivity
#   adb shell am broadcast -a com.stand.NLU -p com.incall.apps.speechassistant --es cmd 'открой окно водителя'
#
# Build:
#   ./stand/build_q07.sh ./SpeechAssistant.apk ./out/speechassistant-q07-stage1.apk
#
# Optional:
#   TTS_HOOK=1   route TtsPlayer.start through RuBridge.onTtsText
#   TTS_REWRITE=1 rewrite stock CJK TTS text through RuBridge.ttsRewrite
#   WAKE_CHIME=1 keep the steering-key chime patch (default 0 for stage 1)
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
SRC="${1:?usage: build_q07.sh <stock SpeechAssistant.apk> <out.apk>}"
OUT="${2:?usage: build_q07.sh <stock SpeechAssistant.apk> <out.apk>}"
TTS_HOOK="${TTS_HOOK:-0}"
TTS_REWRITE="${TTS_REWRITE:-0}"

# Load the shared Android/JDK toolchain when the caller has not sourced env.sh.
if [ -f "$HERE/env.sh" ]; then
  # shellcheck disable=SC1091
  source "$HERE/env.sh"
fi

case "$SRC" in /*) ;; *) SRC="$(pwd)/$SRC";; esac
case "$OUT" in /*) ;; *) OUT="$(pwd)/$OUT";; esac
[ -f "$SRC" ] || { echo "Stock APK not found: $SRC"; exit 1; }
python3 - "$SRC" <<'PY'
import hashlib,sys,zipfile
p=sys.argv[1]
h=hashlib.sha256(open(p,"rb").read()).hexdigest()
want="261c3d9f042f2d66851a82364663ff5962267a719b6412e3518670240ca616f4"
if h != want:
    raise SystemExit(f"[q07] WRONG STOCK APK SHA-256: {h} (expected Q07 reference {want})")
with zipfile.ZipFile(p) as z:
    assert z.testzip() is None, "[q07] stock ZIP integrity failure"
    for n in ("AndroidManifest.xml","classes.dex","classes2.dex","classes3.dex","classes4.dex","classes5.dex","classes6.dex"):
        if n not in z.namelist(): raise SystemExit(f"[q07] stock missing {n}")
print("[q07] stock preflight: PASS")
PY
mkdir -p "$(dirname "$OUT")"

echo "[q07] compiling bridge -> classes7.dex"
bash "$HERE/asr-android/build_dex.sh"

WD="$HERE/work/q07_build"
rm -rf "$WD"
mkdir -p "$WD"
unzip -o -j "$SRC" classes5.dex -d "$WD" >/dev/null
java -jar "$ROOT/tools/baksmali.jar" d --api 34 "$WD/classes5.dex" -o "$WD/smali5"

SM="$WD/smali5"
APP="com/incall/apps/speechassistant"
BRIDGE="Lcom/stand/bridge/RuBridge;"

python3 - "$SM" "$TTS_HOOK" "$TTS_REWRITE" <<'PY'
import os, re, sys

SM=sys.argv[1]
TTS_HOOK=sys.argv[2]=="1"
TTS_REWRITE=sys.argv[3]=="1"

def repl(path, sig, body):
    with open(path, encoding="utf-8") as f: s=f.read()
    pat=re.compile(r'(\.method [^\n]*'+re.escape(sig)+r'\n).*?(\n\.end method)', re.S)
    if not pat.search(s):
        raise RuntimeError(f"method {sig} not found: {path}")
    s=pat.sub(lambda m:m.group(1)+body+m.group(2), s, count=1)
    with open(path,"w",encoding="utf-8") as f: f.write(s)

# Q07-specific fact: VoiceApp is in classes5.dex, not classes6.dex.
va=f"{SM}/com/incall/apps/speechassistant/application/VoiceApp.smali"
with open(va,encoding="utf-8") as f: s=f.read()

# Android 11: use the 2-argument registerReceiver() overload.
# Keep the stock Q07 register count unchanged.
m=re.search(r'(\.method public onCreate\(\)V\n\s*\.registers \d+\n.*?invoke-super \{p0\}, Landroid/app/Application;->onCreate\(\)V\n)',s,re.S)
if not m:
    raise RuntimeError("Q07 VoiceApp.onCreate super call not found")

inj=r'''
    invoke-static {p0}, Lcom/stand/bridge/RuBridge;->initTextOnly(Landroid/content/Context;)V
    invoke-static {p0}, Lcom/stand/bridge/RuBridge;->registerStandNluReceiver(Landroid/content/Context;)V
'''
s=s[:m.end()] + inj + s[m.end():]
with open(va,"w",encoding="utf-8") as f: f.write(s)

# Stage 1 deliberately leaves stock NLU arbitration untouched.
# The trigger calls RuBridge.handlePhraseZh(), which maps Russian text through the
# scenario engine and then calls NluManager.onFinalAsrResult() with the Q07-compatible
# signature confirmed from stock classes5.dex:
# (String requestId, int direction, String asrText, boolean confident).

if TTS_HOOK:
    for name in ("TtsPlayer.smali","TtsPlayer2.smali"):
        tp=f"{SM}/com/incall/apps/speechassistant/tts/{name}"
        if not os.path.exists(tp):
            continue
        with open(tp,encoding="utf-8") as f: ts=f.read()
        hook=r'''
    move-object/16 v0, p2
    move-object/16 v1, p5
    invoke-static {v0, v1}, Lcom/stand/bridge/RuBridge;->onTtsText(Ljava/lang/String;Ljava/lang/Object;)Z
    move-result v0
    if-eqz v0, :q07_tts_orig
    const/4 v0, 0x0
    return v0
    :q07_tts_orig
'''
        mm=re.search(r'(\.method public start\(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZLcom/incall/apps/voiceserver/tts/IPlayerListener;\)I\n\s*\.registers \d+\n)',ts)
        if not mm:
            raise RuntimeError(f"TtsPlayer.start not found: {tp}")
        ts=ts[:mm.end()]+hook+ts[mm.end():]
        with open(tp,"w",encoding="utf-8") as f: f.write(ts)

if TTS_REWRITE:
    for name in ("TtsPlayer.smali","TtsPlayer2.smali"):
        tp=f"{SM}/com/incall/apps/speechassistant/tts/{name}"
        if not os.path.exists(tp):
            continue
        with open(tp,encoding="utf-8") as f: ts=f.read()
        hook=r'''
    move-object/16 v0, p2
    invoke-static {v0}, Lcom/stand/bridge/RuBridge;->ttsRewrite(Ljava/lang/String;)Ljava/lang/String;
    move-result-object v0
    move-object/16 p2, v0
'''
        mm=re.search(r'(\.method public start\(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZLcom/incall/apps/voiceserver/tts/IPlayerListener;\)I\n\s*\.registers \d+\n)',ts)
        if not mm:
            raise RuntimeError(f"TtsPlayer.start not found: {tp}")
        ts=ts[:mm.end()]+hook+ts[mm.end():]
        with open(tp,"w",encoding="utf-8") as f: f.write(ts)

print("[q07] VoiceApp trigger installed; TTS_HOOK=%s TTS_REWRITE=%s" % (TTS_HOOK,TTS_REWRITE))
PY

java -jar "$ROOT/tools/smali.jar" a --api 34 "$WD/smali5" -o "$WD/classes5.dex"

cp "$SRC" "$WD/out.apk"
(cd "$WD" && zip -q -d out.apk classes5.dex >/dev/null 2>&1 || true && zip -j -q out.apk classes5.dex)

# Add our bridge as classes7.dex.
cp "$HERE/asr-android/build/dex7/classes.dex" "$WD/classes7.dex"
(cd "$WD" && zip -j -q out.apk classes7.dex)

"$ZIPALIGN" -p -f 4 "$WD/out.apk" "$WD/aligned.apk"
"$APKSIGNER" sign --key "$ROOT/tools/platform-key/platform.pk8" --cert "$ROOT/tools/platform-key/platform.x509.pem" --out "$OUT" "$WD/aligned.apk"

echo
echo "[q07] built: $OUT"
echo "[q07] running 100-cycle static regression gate..."
APKSIGNER="$APKSIGNER" ZIPALIGN="$ZIPALIGN" python3 "$ROOT/tools/q07_validate.py" "$SRC" "$OUT" "${Q07_VALIDATE_CYCLES:-100}"
echo "[q07] stock APK was used as input and was not modified."
echo "[q07] stage 1 test: launch SpeechAssistant, then broadcast --es cmd '<Russian phrase>'"
