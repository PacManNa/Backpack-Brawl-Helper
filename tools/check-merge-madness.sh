#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."

sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$sdk_root" ]]; then
    echo 'Set ANDROID_HOME or ANDROID_SDK_ROOT to the Android SDK directory.' >&2
    exit 1
fi
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}java"
javac_bin="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
android_jar="$sdk_root/platforms/android-35/android.jar"
classes='app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes'
if [[ ! -f "$classes/ua/borlock/autotap/MergeMadnessObjects.class" ]]; then
    echo 'Run ./gradlew testDebugUnitTest first to compile the production classes.' >&2
    exit 1
fi
check_dir="$(mktemp -d)"
trap 'rm -rf -- "$check_dir"' EXIT
"$javac_bin" -encoding UTF-8 -cp "$classes:$android_jar" -d "$check_dir" \
    tools/MergeMadnessScreenshotCheck.java tools/MergeMadnessObjectsCheck.java
check_classpath="$classes:$check_dir:app/src/test/resources:$android_jar"
"$java_bin" -Djava.awt.headless=true -cp "$check_classpath" \
    ua.borlock.autotap.MergeMadnessScreenshotCheck \
    app/src/test/resources/merge_madness app/src/main/res/raw
"$java_bin" -Djava.awt.headless=true -cp "$check_classpath" \
    ua.borlock.autotap.MergeMadnessObjectsCheck
