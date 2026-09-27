#!/usr/bin/env bash
# Legacy generic build entry point. For Q07 use stand/build_q07.sh.
#
#   ./build.sh [path/to/SpeechAssistant.orig.apk] [out.apk]
#   VARIANT=multi ./build.sh ...   — multilingual ASR (GigaAM-Multilingual: ru/kk/ky/uz/en) instead of
#                                    the Russian-only GigaAM-v3. Needs stand/asr-android/gigaam-ml/model.int8.onnx.
#
# The STOCK SpeechAssistant.apk is proprietary (Changan) and is NOT shipped here — pull it from
# your own head unit:
#   SER=$(adb devices | awk '/device$/{print $1; exit}')
#   P=$(adb -s "$SER" shell pm path com.incall.apps.speechassistant | head -1 | sed 's/package://' | tr -d '\r')
#   adb -s "$SER" pull "$P" ./SpeechAssistant.orig.apk
#
# Requirements: JDK 17, Android SDK (platforms;android-34 + build-tools;34.0.0). Set ANDROID_HOME
# and JAVA_HOME if they aren't auto-detected.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC="${1:-$HERE/SpeechAssistant.orig.apk}"
VARIANT="${VARIANT:-ru}"   # ru | multi
case "$VARIANT" in ru) GIGAAM_ML=0;; multi) GIGAAM_ML=1;; *) echo "VARIANT must be ru or multi"; exit 1;; esac
OUT="${2:-$HERE/out/speechassistant-$VARIANT.apk}"

if [ ! -f "$SRC" ]; then
  echo "Stock SpeechAssistant.apk not found: $SRC"
  echo "Pull it from your car first (see the comment block at the top of this script)."
  exit 1
fi
mkdir -p "$(dirname "$OUT")"

echo "[1/2] compiling com/stand/** -> classes7.dex"
bash "$HERE/stand/asr-android/build_dex.sh"

echo "[2/2] patching + repacking + signing (platform test-keys) — variant: $VARIANT"
# Flags = the shipped profile: GigaAM ASR + TeraTTS + ru2zh->stock NLU (offline first), free-form
# questions / unknown commands go to the assistant backend configured in RuBridge (BACKEND).
# WAKE_CHIME=1 (default): steering-key wake plays a chime instead of the spoken greeting.
PROFILE=car HOST=127.0.0.1:8080 RUSSIAN_ASR=1 NO_CN_SR=1 NO_CN_ENGINE=0 \
  BRIDGE=1 PIPER=1 TERA=1 GIGAAM=1 GIGAAM_ML=$GIGAAM_ML TTS_HOOK=0 TTS_REWRITE=1 \
  bash "$HERE/stand/build_sa.sh" "$SRC" "$OUT"

echo
echo "built: $OUT"
echo "install:  adb push \"$OUT\" /data/local/tmp/sa.apk && adb shell pm install -r -d -g -t /data/local/tmp/sa.apk"
