# Microbes Live Wallpaper

Modern rebuild of the legacy Microbes live wallpaper for current Android and foldable devices such as the Galaxy Z Fold7.

## Current status

Version 0.6.4 runs the same Java-hosted OpenGL ES 2.0 renderer in both a launcher activity and the live wallpaper service. The activity appears in the app drawer, provides an interactive preview and settings, and opens Android's live-wallpaper preview.

Taps deposit food, drags attract microbes, and microbes wander, avoid one another, consume food, grow while well-fed, split into small offspring, and leave sinking remains when they die. The GPU draws separate decoration, corpse, food, and living-microbe layers using the original APK's decoded four-type palette, size hierarchy, energy-dependent shape, food scale, and fog scale. Movement and lifecycle speeds are configurable, and the background fog can be disabled.

## Build

Open this project in Android Studio with Android SDK Platform 37 installed, or run:

```text
./gradlew :app:assembleDebug
```

Install the resulting debug APK and launch **Microbes Live Wallpaper** from the app drawer. Open the cogwheel settings and tap **Set live wallpaper** to open the system wallpaper preview. The settings screen also links to this repository for updates.

## Rebuild notes

The supplied reference APK is a 2017 package containing DEX 035 code and a single `armeabi` JNI library. It remains a behavioral and visual reference only; the rebuilt application does not package or load that library.
