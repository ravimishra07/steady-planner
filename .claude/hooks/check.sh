#!/bin/bash
# PostToolUse on .kt edits. Flags ARCHITECTURE §1/§9 breaks. exit 2 = Claude must fix.
f=$(jq -r '.tool_input.file_path // ""')
case "$f" in *.kt) ;; *) exit 0 ;; esac
case "$f" in */build/*|*/src/test/*) exit 0 ;; esac
[ -f "$f" ] || exit 0
e=""
hit(){ local m; m=$(grep -nE "$1" "$f" | grep -vE '^[0-9]+:[[:space:]]*(//|\*|/\*)' | head -3); [ -n "$m" ] && e+="- $2"$'\n'"$m"$'\n'; }
case "$f" in
  */android/domain/*) hit '^import (android|androidx)\.' ":domain imports Android" ;;
esac
if [[ "$f" =~ /android/feature/([a-z]+)/ ]]; then
  me=${BASH_REMATCH[1]}
  m=$(grep -nE '^import com\.exam\.assistant\.feature\.' "$f" | grep -v "feature\.$me[.]")
  [ -n "$m" ] && e+="- feature imports another feature (wire via :app callback)"$'\n'"$m"$'\n'
fi
case "$f" in
  */android/feature/*|*/android/app/*)
    hit '[0-9]\.(dp|sp)\b|Color\(0x|RoundedCornerShape\([0-9]' "hardcoded size/colour/radius — use :core:design tokens" ;;
esac
case "$f" in
  */core/common/*AppDispatchers.kt) ;;
  *) hit 'Dispatchers\.(IO|Default|Main)\b' "inline Dispatchers — inject AppDispatchers" ;;
esac
hit '\bGlobalScope\b' "GlobalScope forbidden"
hit '\brunBlocking\b' "runBlocking forbidden"
hit '\b(LiveData|io\.reactivex|dagger\.hilt|org\.koin)\b' "LiveData/Rx/Hilt/Koin forbidden"
hit 'collectAsState\(\)' "use collectAsStateWithLifecycle()"
[ -z "$e" ] && exit 0
echo "ARCH VIOLATION in $f:"$'\n'"$e" >&2; exit 2
