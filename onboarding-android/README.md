# Steadyline Onboarding

Standalone Android app containing only the approved onboarding flow. It has its
own application id (`com.steadyline.onboarding`) and its own local preferences.
It does not depend on or modify the shipping app under `android/`.

Build from this directory:

```sh
../android/gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`
