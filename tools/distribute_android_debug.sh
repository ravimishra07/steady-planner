#!/bin/sh
set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
release_notes=${1:-"Latest Steadyline Android debug build"}
apk_path="$repo_root/android/app/build/outputs/apk/debug/app-debug.apk"

export JAVA_HOME=$(/usr/libexec/java_home -v 21)

cd "$repo_root/android"
./gradlew :domain:test :app:assembleDebug

cd "$repo_root"
firebase appdistribution:distribute "$apk_path" \
  --app "1:512302699182:android:cb25bb8800c99034db133f" \
  --testers "projectsam07@gmail.com" \
  --release-notes "$release_notes"
