#!/bin/bash
# Builds the Omalauncher+ system bridge, an Xposed module for LSPosed or Vector:
#   ./build.sh <version code> <version name> <keystore> <store password> <key alias> <android.jar> <d8.jar> <apksigner.jar>
# There's no aapt2 for arm64 Linux, so tools/Axml.java writes the binary manifest; stubs/ stand in for the Xposed API
# at compile time and aren't packaged.
set -e
cd "$(dirname "$0")"
VC=$1; VN=$2; KS=$3; KP=$4; KA=$5; AJ=$6; D8=$7; SIGNER=$8
rm -rf build && mkdir -p build/stubs build/classes build/dex build/apk/assets out
javac -nowarn --release 17 -cp "$AJ" -d build/stubs $(find stubs -name '*.java')
javac -nowarn --release 17 -cp "$AJ:build/stubs" -d build/classes $(find src -name '*.java')
java -cp "$D8" com.android.tools.r8.D8 --release --min-api 35 --lib "$AJ" --classpath build/stubs --output build/dex $(find build/classes -name '*.class')
java tools/Axml.java build/apk/AndroidManifest.xml dev.local.omaplusperm "Omalauncher+ system bridge" \
  "For Omalauncher+ (dev.omalauncher.plus): in System Framework it grants the permission its own launch animations need; in System UI it hands it the back gesture to home. Nothing is mounted. Scope: System Framework and System UI." "$VC" "$VN" >/dev/null
echo dev.local.omaplusperm.Module > build/apk/assets/xposed_init
cp build/dex/classes.dex build/apk/
(cd build/apk && zip -q -X -r ../unsigned.apk AndroidManifest.xml classes.dex assets)
java -jar "$SIGNER" sign --ks "$KS" --ks-pass "pass:$KP" --ks-key-alias "$KA" --min-sdk-version 35 --out out/omaplusperm-$VN.apk build/unsigned.apk
echo out/omaplusperm-$VN.apk
