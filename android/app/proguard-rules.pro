# R8 rules for the release build.
#
# What needs no rule here, and why:
# - Room: room-runtime ships consumer rules that keep every RoomDatabase subclass and the
#   generated *_Impl it loads by name. Entities and DAOs are reached only through generated code.
# - Play Billing, Firebase Analytics/Crashlytics, Glance, WorkManager, AndroidX Startup: each
#   library ships its own consumer rules.
# - Activities, receivers and the Application named in AndroidManifest.xml are kept by AAPT2's
#   generated rules.
# - org.json is part of the Android framework, so it is never shrunk.
# - Enums: proguard-android-optimize.txt keeps values()/valueOf() for every enum.
# - No kotlinx.serialization, Gson, Moshi or DataStore-proto models are compiled into the app.

# Readable Crashlytics stack traces: keep line numbers, hide the original file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
