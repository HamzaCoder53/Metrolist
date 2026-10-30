#!/bin/zsh
# Publish Wavelet to Google Play's Wear OS internal testing track from the terminal.
#   wear/tools/publish.sh          # bump the version if needed, build, upload, show what's live
#   wear/tools/publish.sh --status # only show what's live on Play
set -e
cd "$(dirname "$0")/../.." || exit 1
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
BUILD=wear/build.gradle.kts

if [ "$1" = "--status" ]; then
  python3 -I wear/tools/play_tracks.py
  exit 0
fi

[ -z "$(git status --porcelain -- wear streaming innertube)" ] || {
  echo "Uncommitted changes in wear/streaming/innertube; commit them first."; exit 1
}

PUBLISHED=$(python3 -I wear/tools/play_tracks.py | awk '/track: wear:internal/{f=1;next} f&&/versionCodes=/{gsub(/.*\[.|.\].*/,"");print;exit}')
CODE=$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' $BUILD)
NAME=$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' $BUILD)
if [ -n "$PUBLISHED" ] && [ "$CODE" -le "$PUBLISHED" ]; then
  CODE=$((PUBLISHED + 1)); NAME=$(echo "$NAME" | awk -F. '{printf "%d.%d.%d", $1, $2, $3 + 1}')
  sed -i '' -e "s/versionCode = [0-9]*/versionCode = $CODE/" -e "s/versionName = \"[^\"]*\"/versionName = \"$NAME\"/" $BUILD
  git commit -q -m "chore(wear): release $NAME (versionCode $CODE)" -- $BUILD
fi

echo "Publishing Wavelet $NAME (versionCode $CODE) to the Wear OS internal track…"
./gradlew :streaming:testDebugUnitTest :wear:publishReleaseBundle --no-configuration-cache --console=plain -q
python3 -I wear/tools/play_tracks.py | grep -A1 "wear:internal"
echo "Done. Update Wavelet from the Play Store on your watch."
