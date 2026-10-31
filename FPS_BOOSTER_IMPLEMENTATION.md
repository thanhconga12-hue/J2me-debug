# FPS Booster + Logic Speed (experimental source patch)

This source branch is based on the user-provided `JL-Mod-dev` repository. It uses a new package ID for side-by-side installation and imports selected compatibility assets/settings from the supplied NST APK. It is **not an APK build** and it does not reconstruct every NST-only compiled feature; see `NST_INTEGRATION_NOTES.md`.

## Features added to each game profile

- FPS renderer target: unlimited, 30, 60, 90 or 120 FPS.
- Optional game-logic multiplier: 1.5x, 2x, 3x or 4x.
- Both features default to disabled, so pre-existing profiles retain their original behavior.
- Existing `FpsLimit` remains the fallback when the new renderer target is disabled.
- Settings are stored in the existing per-game profile model; no RMS/game save format is modified.

## Bytecode timing hooks

The JAR-to-DEX visitor redirects common game timing calls to `javax.microedition.shell.GameTime`:

- `Thread.sleep(long)` and `Thread.sleep(long, int)`
- `Thread.join()`, `Thread.join(long)` and `Thread.join(long, int)`
- `Object.wait()`, `Object.wait(long)` and `Object.wait(long, int)`
- `System.currentTimeMillis()` and `System.nanoTime()`
- `TimeUnit.sleep(long)`
- `LockSupport.parkNanos(...)` and `LockSupport.parkUntil(...)`
- Emulator-remapped J2ME `Timer` relative waits and clock comparisons

When Logic Speed is disabled, the game clock methods return the real platform clocks and sleep/wait helpers preserve 1x timing. When enabled, the bridge uses a virtual game clock and scales supported relative waits. The `GameTime` class is explicitly kept from R8/ProGuard shrinking/renaming because converted game DEX calls it by its fixed class name.

## FPS behavior and limitations

The FPS option sets the renderer's frame-rate ceiling. It can remove a restrictive cap or allow an available faster render cadence, but it does **not** synthesize/interpolate frames and cannot make a game that only calls repaint 15 times per second produce 60 unique frames. Actual FPS remains limited by the game loop, rendering workload, CPU/GPU, Android display refresh rate and existing emulator scheduling.

Logic Speed accelerates common timing APIs, not every possible game timer. It cannot fully virtualize `Date`/`Calendar` constructors, native/JNI clocks, Android-specific clocks, custom busy loops, audio hardware timing, or every game/vendor API. Date-based absolute `Timer` scheduling may be inconsistent when a game mixes wall-clock `Date` values with the virtual clock. Networked, competitive and timing-sensitive titles may desynchronize or run too fast. Keep the feature off by default and test titles individually.

The bytecode hooks are installed when a JAR is converted by this build. Existing converted DEX caches need to be regenerated for the hooks to apply; back up game data before clearing caches.

## Build

Open the project in Android Studio with Android SDK 34 and NDK 22.1.7171670 installed, then build the `emulatorDebug` variant. The Gradle wrapper targets Gradle 8.13. Release builds require a valid signing configuration. See `NST_INTEGRATION_NOTES.md` before attempting to use this branch as an NST replacement.
