#!/bin/bash
# Stop. Compile Kotlin if android/ source changed since last green compile. No tests.
in=$(cat); cd "$(git -C "$(dirname "$0")" rev-parse --show-toplevel)" || exit 0
files=$( { git diff HEAD --name-only -- android; git ls-files -o --exclude-standard android; } | grep -E '\.(kt|kts|xml|toml)$')
[ -z "$files" ] && exit 0
sum=$(cat $files 2>/dev/null | md5 -q); stamp=.claude/.build-ok
[ "$sum" = "$(cat $stamp 2>/dev/null)" ] && exit 0
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
out=$(cd android && ./gradlew -q --console=plain compileDebugKotlin :domain:compileKotlin 2>&1)
[ $? -eq 0 ] && { echo "$sum" > $stamp; exit 0; }
[ "$(jq -r .stop_hook_active <<<"$in")" = true ] && { echo "compile still failing" >&2; exit 0; }
echo "COMPILE FAILED — fix before stopping:"$'\n'"$(grep -E '^e: |What went wrong' -A2 <<<"$out" | head -40)" >&2; exit 2
