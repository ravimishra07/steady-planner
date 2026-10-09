---
name: ship-apk
description: Build debug APK and reveal in Finder. The only delivery path for any Android change.
---
```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
cd android && ./gradlew -q assembleDebug && open -R app/build/outputs/apk/debug/app-debug.apk
ls -lh app/build/outputs/apk/debug/app-debug.apk
```
Report: green/red, APK size. Claim only what build proves — never "screen works".
Never: emulator, adb, Developer options, wireless debugging, tests.
