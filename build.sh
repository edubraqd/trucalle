#!/usr/bin/env bash
# Build sem Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}"
BT="$SDK/build-tools/36.0.0"
JAR="$SDK/platforms/android-33/android.jar"
OUT=build
KS=debug.keystore

rm -rf "$OUT"; mkdir -p "$OUT/obj" "$OUT/res"

"$BT/aapt2.exe" compile --dir res -o "$OUT/res.zip"
"$BT/aapt2.exe" link -o "$OUT/unsigned.apk" -I "$JAR" --manifest AndroidManifest.xml "$OUT/res.zip" --java "$OUT/res"

javac --release 17 -classpath "$JAR" -d "$OUT/obj" $(find src "$OUT/res" -name '*.java')

"$BT/d8.bat" --release --min-api 24 --lib "$JAR" --output "$OUT" $(find "$OUT/obj" -name '*.class')
python -c "import zipfile,sys;z=zipfile.ZipFile(sys.argv[1],'a',zipfile.ZIP_DEFLATED);z.write(sys.argv[2],'classes.dex');z.close()" "$OUT/unsigned.apk" "$OUT/classes.dex"

"$BT/zipalign.exe" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -storepass android -keypass android \
    -alias debug -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Trucalle debug" >/dev/null 2>&1

"$BT/apksigner.bat" sign --ks "$KS" --ks-pass pass:android --ks-key-alias debug \
    --out "$OUT/trucalle.apk" "$OUT/aligned.apk"

ls -l "$OUT/trucalle.apk"
