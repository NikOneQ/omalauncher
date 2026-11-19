#!/bin/bash
# Builds the KernelSU / Magisk module that makes Omalauncher+ a privileged system app, from a signed APK:
#   ./build.sh omalauncher-plus-1.7.0.3.apk  ->  omalauncher-plus-system-1.7.0.3.zip
# The APK goes to /system/priv-app with its native libraries unpacked, as system apps need; the installed app stays
# the update over it. Before adding a privileged permission to the app, add it to the privapp-permissions file.
set -e
apk=$(realpath "$1")
here=$(cd "$(dirname "$0")" && pwd)
name=$(basename "$apk" .apk); version=${name#omalauncher-plus-}
code=$(echo "$version" | tr -d . | sed 's/^0*//')
work=$(mktemp -d); trap 'rm -rf "$work"' EXIT
cp -r "$here/template/." "$work/"
sed -e "s/@VERSION@/$version/" -e "s/@CODE@/${code:-1}/" "$work/module.prop.in" > "$work/module.prop" && rm "$work/module.prop.in"
app="$work/system/priv-app/OmalauncherPlus"
mkdir -p "$app/lib/arm64"
cp "$apk" "$app/OmalauncherPlus.apk"
(cd "$app/lib/arm64" && bsdtar -xf "$apk" --strip-components 2 'lib/arm64-v8a/*')
out="$(dirname "$apk")/omalauncher-plus-system-$version.zip"
rm -f "$out"
(cd "$work" && zip -q -r -X "$out" META-INF module.prop customize.sh system)
echo "$out"
