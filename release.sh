#!/usr/bin/env bash
# Build the mod and publish it as a GitHub Release (the APK is NEVER committed to git — it is
# attached to the release as a download).
#
#   ./release.sh <version> [path/to/SpeechAssistant.orig.apk]
#   e.g. ./release.sh 1.0.3 ./SpeechAssistant.orig.apk
#
# Needs: gh (GitHub CLI, logged in), JDK 17, Android SDK, the stock APK and the model files (MODELS.md).
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VER="${1:?version, e.g. 1.0.3}"; SRC="${2:-$HERE/SpeechAssistant.orig.apk}"
TAG="v$VER"
# The Russian APK is always published. The multilingual one (GigaAM-Multilingual) only with WITH_MULTI=1:
# it is still being debugged on the car (v1.1.0 shipped without it), so it must be an explicit decision.
WITH_MULTI="${WITH_MULTI:-0}"
NAME="speechassistant-ru-$TAG.apk";       OUT="$HERE/out/$NAME"
NAME_ML="speechassistant-multi-$TAG.apk"; OUT_ML="$HERE/out/$NAME_ML"

[ -f "$SRC" ] || { echo "stock SpeechAssistant.apk not found: $SRC (pull it from your car, see README)"; exit 1; }
command -v gh >/dev/null || { echo "gh (GitHub CLI) is required: brew install gh && gh auth login"; exit 1; }

echo "[release] building $NAME"
VARIANT=ru "$HERE/build.sh" "$SRC" "$OUT"
( cd "$HERE/out" && shasum -a 256 "$NAME" > "$NAME.sha256" )
echo "[release] $(du -h "$OUT" | cut -f1)  sha256: $(cut -d' ' -f1 "$OUT.sha256")"
ASSETS="$OUT $OUT.sha256"
if [ "$WITH_MULTI" = "1" ]; then
  echo "[release] building $NAME_ML"
  VARIANT=multi "$HERE/build.sh" "$SRC" "$OUT_ML"
  ( cd "$HERE/out" && shasum -a 256 "$NAME_ML" > "$NAME_ML.sha256" )
  echo "[release] $(du -h "$OUT_ML" | cut -f1)  sha256: $(cut -d' ' -f1 "$OUT_ML.sha256")"
  ASSETS="$ASSETS $OUT_ML $OUT_ML.sha256"
fi

echo "[release] tests"
sh "$HERE/ru2zh/translate-task/tests/run_tests.sh" | tail -1

if git -C "$HERE" rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
  echo "[release] tag $TAG exists"
else
  git -C "$HERE" tag -a "$TAG" -m "Release $TAG"
  git -C "$HERE" push origin "$TAG"
fi

NOTES="Russian voice assistant mod for Changan A06 (C390) — $TAG.

Install (no root):
\`\`\`
adb push $NAME /data/local/tmp/sa.apk
adb shell pm install -r -d -g -t /data/local/tmp/sa.apk
adb shell am force-stop com.incall.apps.speechassistant
\`\`\`
Wake with «нихао» or the steering-wheel key. Command list: COMMANDS.md. Revert: \`adb shell pm uninstall com.incall.apps.speechassistant\`.

SHA-256: $(cut -d' ' -f1 "$OUT.sha256")"

REPO="$(git -C "$HERE" remote get-url origin)"
gh release view "$TAG" -R "$REPO" >/dev/null 2>&1 \
  || gh release create "$TAG" -R "$REPO" --draft --verify-tag --title "$TAG" --notes "$NOTES"
# Assets one by one with retries: GitHub answers 5xx on ~900 MB uploads now and then, and
# `gh release create <files>` deletes its own draft when that happens (seen on v1.1.0).
for f in $ASSETS; do
  n=0
  until gh release upload "$TAG" "$f" -R "$REPO" --clobber; do
    n=$((n+1)); [ $n -ge 4 ] && { echo "[release] upload failed: $f"; exit 1; }
    echo "[release] retry $n: $(basename "$f")"; sleep 30
  done
done
gh release edit "$TAG" -R "$REPO" --draft=false --latest
echo "[release] done: $(gh release view "$TAG" --json url -q .url)"
