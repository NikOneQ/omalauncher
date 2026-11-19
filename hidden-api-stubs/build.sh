#!/bin/bash
# Builds hidden-api-stubs.jar: compile-only stand-ins for framework classes the public SDK hides (the
# remote transitions behind Omalauncher+'s launch animations). Never packaged; the device's own classes run.
# Needs an android.jar of API 37: ./build.sh /path/to/android.jar
set -e
cd "$(dirname "$0")"
ANDROID_JAR=${1:-${ANDROID_HOME:-$HOME/Android/Sdk}/platforms/android-37/android.jar}
out=$(mktemp -d)
javac -nowarn --release 17 -cp "$ANDROID_JAR" -d "$out" $(find src -name '*.java')

(cd "$out" && jar --create --date=2026-01-01T00:00:00Z --file "$OLDPWD/hidden-api-stubs.jar" .)
rm -rf "$out"
echo hidden-api-stubs.jar
