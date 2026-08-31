# Microbes rebuild plan

The rebuild uses one deterministic scene and one GPU renderer in two Android hosts:

- `MainActivity` and `MicrobesPreviewView` provide the app-drawer experience.
- `MicrobesWallpaperService` provides the system live wallpaper.
- `MicrobesRenderController` owns surface and thread lifecycle for both hosts.
- `MicrobesRenderer` owns OpenGL resources and draw calls.
- `simulation` contains Android-independent scene behavior that can be unit tested.

## Milestones and acceptance gates

### 1. Testable vertical slice

- Both hosts use the same renderer and simulation.
- The app has a launcher entry, interactive preview, and system wallpaper action.
- Renderer shutdown completes before Android destroys or reuses its surface.
- Touch delivery, shader failures, and EGL failures are observable and testable.

Gate:

```text
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -n com.paladin173.microbes/.MainActivity
```

Confirm the launcher preview animates, touch changes the scene, **Set live wallpaper**
opens the system preview, and applying it returns an animated home screen.

### 2. Original simulation

- Use a persistent pixel-space world independent of the current viewport.
- Restore 300 microbe slots, 600 food slots, 80 corpses, 60 decorations, and 15
  motion attractors.
- Restore wandering, collision avoidance, feeding, energy, pulse, breeding, death,
  corpse sinking, replacement, tap food, drag attraction, and multi-pointer input.
- Preserve deterministic seeds in tests while using a variable production seed.

Gate:

- RED/GREEN unit tests cover every state transition and population invariant.
- Resize tests prove fold/unfold does not reset the scene.
- Recorded reference scenarios compare counts and positions over fixed time steps.

### 3. Original rendering

- Use separate GPU programs for decoration, corpses, food, and live microbes.
- Match original draw order, additive blending, point sizes, palette, pulses, and
  world-to-viewport transform.
- Query device point-size limits and provide a mesh fallback where needed.

Gate:

- Golden screenshots cover phone portrait, cover display, unfolded portrait, and
  unfolded landscape at fixed seeds and timestamps.
- A reviewer compares each image with the reference APK and records accepted deltas.

### 4. Fold and lifecycle hardening

- Preserve scene state through surface recreation, resize, preview transitions,
  screen off/on, wallpaper picker, and cover/inner display switching.
- Keep an expanded persistent world when folding. The cover display uses a centered crop;
  off-screen organisms remain in place and ordinary movement gradually repopulates the
  visible region instead of squeezing the whole world into the cover viewport.
- Pause all rendering while hidden and resume without a time-step spike.
- Frame pacing targets the display without an unbounded busy loop.

Gate:

- Automated stress loop performs at least 100 surface/visibility/resize cycles with
  no EGL errors, leaked render threads, black frames, or simulation resets.
- Profile frame time, allocations, GPU load, and battery on Galaxy Z Fold7 and a
  Pixel Fold-class device.

### 5. Release candidate

- Add adaptive launcher artwork, backup/data extraction rules, accessibility labels,
  privacy metadata, release signing, and store assets.
- Test home and lock screens, multiple Engine instances, preview plus active wallpaper,
  process death, Android 12 through the latest supported Android release, and vendor
  launchers from Samsung and Google.

Gate:

- Release build, lint, unit tests, instrumentation tests, Play pre-launch report,
  and physical-device matrix are green.
- The installed release artifact is read back for package, version, ABI, SDK, and
  live-wallpaper service metadata.
