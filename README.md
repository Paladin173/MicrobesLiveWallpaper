# Microbes Live Wallpaper

Modern rebuild of the legacy Microbes live wallpaper for Android 16 and foldable devices such as the Galaxy Z Fold7.

## Current status

The repository contains a pure-Java animated wallpaper baseline. It intentionally has no native libraries, which avoids the original APK's ARM32-only and 4 KB ELF-page constraints. The original wallpaper thumbnail and launcher artwork are preserved in `app/src/main/res/drawable-nodpi/`, and the renderer now follows the original black/blue atmosphere, neon color palette, glowing halos, and ring-like microbe forms. Fold-specific behavior and final visual fidelity remain to be tested and refined on hardware.

## Build

Open this project in Android Studio with Android SDK Platform 36 installed, or run:

```text
./gradlew :app:assembleDebug
```

Install the resulting debug APK, then select **Microbes Live Wallpaper** from the system wallpaper picker.

## Rebuild notes

The supplied reference APK was `Microbes-1.apk`, a 2017 package containing DEX 035 code and a single `armeabi` JNI library. It is not included in this repository because it is a binary reference, not the source implementation.
