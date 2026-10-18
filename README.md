# Pantser (Pant Size Labeler)

Pantser generates apparel size label PDFs on US Letter pages: type a size,
get a bold, centered grid PDF ready to print. Two tracks, one behavior spec:

- `android/` : the native Android track (package com.pantser.landc). Open
  `android/` in Android Studio and the APK builds exactly as before.
- `web/` : the Progressive Web App track. Frozen behavior spec at
  `web/SPEC.md`; NOT-PORTED.md records anything that could not port.

## Spec of truth

`web/SPEC.md` is the frozen behavior contract. APK and web must never drift
from it. 1:1 behavior parity with the APK is the bar; verification is done
by running every APK flow against the web build and comparing the PDFs side
by side.

## Building the APK

1. Open `android/` in Android Studio.
2. Wait for Gradle sync to complete.
3. Choose Build -> Build App Bundle(s) / APK(s) -> Build APK(s).
4. The debug APK will be generated at
   `android/app/build/outputs/apk/debug/app-debug.apk`.

Minimum Android version: Android 10 (API 29). No external storage
permissions required.
