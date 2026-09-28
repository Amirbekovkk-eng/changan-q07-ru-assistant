#!/usr/bin/env bash
# Compile com/stand/** (RuBridge + Ru2Zh + GigaAsr + TeraTts) + sherpa-onnx java-api into
# build/dex7/classes.dex (=> classes7.dex inside the patched SpeechAssistant.apk).
set -euo pipefail
D="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AJAR="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}/platforms/android-34/android.jar"
BT="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}/build-tools/34.0.0"
[ -x "${JAVA_HOME:-}/bin/javac" ] || export JAVA_HOME=/opt/homebrew/opt/openjdk@17; export PATH="$JAVA_HOME/bin:$PATH"
[ -x "$BT/d8" ] || { echo "d8 not found: $BT/d8"; exit 1; }
[ -f "$AJAR" ] || { echo "android.jar not found: $AJAR"; exit 1; }
rm -rf "$D/build/dex7" "$D/build/stubs"; mkdir -p "$D/build/dex7" "$D/build/stubs"
mapfile -d '' APP_SRC < <(find "$D/src/com/stand" -name '*.java' -print0)
[ "${#APP_SRC[@]}" -gt 0 ] || { echo "No com/stand Java sources found"; exit 1; }
# sherpa-onnx java sources (GigaAM ASR runs on sherpa-onnx); include if present.
SHERPA_SRC=()
if [ -d "$D/sherpa-src" ]; then
  while IFS= read -r -d '' f; do SHERPA_SRC+=("$f"); done < <(find "$D/sherpa-src" -name '*.java' -print0)
fi
# COMPILE-ONLY stubs of the app's own interfaces (ICaTts/ICaStreamTts/ICaTtsCallback for PiperCaTts).
# Compiled to build/stubs and put ONLY on the classpath — NOT fed to d8 (would duplicate app classes).
STUB_CP=""
if [ -d "$D/src-stubs" ]; then
  mapfile -d '' STUB_SRC < <(find "$D/src-stubs" -name '*.java' -print0)
  javac -source 17 -target 17 -d "$D/build/stubs" -classpath "$AJAR" "${STUB_SRC[@]}"
  STUB_CP=":$D/build/stubs"
fi
# onnxruntime-android Java API (ai.onnxruntime.*) for TeraTTS — classes only; libonnxruntime.so is the
# stock one, we bundle just libonnxruntime4j_jni.so (build_sa PIPER block).
ORT_JAR="$D/libs/ort-android-classes.jar"; [ -f "$ORT_JAR" ] || ORT_JAR=""
javac -source 17 -target 17 -encoding UTF-8 -d "$D/build/dex7" \
  -classpath "$AJAR:$ORT_JAR$STUB_CP" \
  "${APP_SRC[@]}" "${SHERPA_SRC[@]}"
mapfile -d '' CLASS_FILES < <(find "$D/build/dex7" -name '*.class' -print0)
[ "${#CLASS_FILES[@]}" -gt 0 ] || { echo "javac produced no class files"; exit 1; }
D8_ARGS=(--min-api 29 --lib "$AJAR" --output "$D/build/dex7" "${CLASS_FILES[@]}")
[ -z "$ORT_JAR" ] || D8_ARGS+=("$ORT_JAR")
"$BT/d8" "${D8_ARGS[@]}"
echo "classes7.dex: $(ls -la "$D/build/dex7/classes.dex" | awk '{print $5}') bytes"
