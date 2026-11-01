# Original NST feature port — integration branch

Source APK: `J2ME_NST (1).apk` from branch `upload-original-nst-apk`.
Package: `com.nst.j2me1`; version: `3.0-nst-open` (versionCode 14).
Target application remains `com.nst.j2me1.fpsboost`; FPS Booster implementation and cache isolation are retained.

## Phase 1 — manifest permission parity (committed)

The FPS app manifest now declares the original APK's permission set, retaining existing declarations and adding the missing ones:
- CAMERA, POST_NOTIFICATIONS
- ACCESS_COARSE_LOCATION
- SYSTEM_ALERT_WINDOW
- FOREGROUND_SERVICE and FOREGROUND_SERVICE_DATA_SYNC
- RECEIVE_BOOT_COMPLETED
- REQUEST_IGNORE_BATTERY_OPTIMIZATIONS

The optional camera hardware feature is declared with `required=false`, matching the original's optional hardware posture. Existing Wi-Fi, WakeLock, Bluetooth, storage, microphone, network and launcher-shortcut permissions remain.

Important: manifest declaration is not runtime authorization. Camera, notifications, location, Bluetooth and all-files access still need appropriate runtime/settings flows before use. Overlay and battery-optimization exemptions require explicit user action in Android Settings. Permissions are declared for compatibility, not silently granted.

## Phase 2.1 — Headless render core (committed; bubble action pending)

The original NST DEX shows that Headless is a render-flush gate, not a MIDlet pause/background scheduler:
- `Canvas.requestFlushToScreen()` returns early only when Headless is enabled and the force-flush flag is false.
- `Canvas.forceFlushToScreen()` temporarily sets the force flag, performs one flush, then restores the previous value even if rendering throws.
- `MicroActivity.onCreate()` resets Headless to disabled.
- `MicroActivity.onResume()` attempts a forced redraw when the current displayable is a Canvas; redraw failures are swallowed so they do not block Activity resume.
- The original APK's game menu does not expose an active Headless item. The working toggle is among the floating-bubble actions. That action path and the bubble UI are not ported yet, so this is the render core only, not a user-complete Headless feature.

## Phase 2 — feature implementation still required

The original APK's manifest/DEX contains these NST-specific features that are not present as source implementations in the FPS repository yet:
- `LauncherRouterActivity` and `LauncherAlias`
- `bubble.BubbleService`, `BubbleSettingsActivity`, `BubbleSettingsFragment` and bubble helpers
- `keepalive.TreoOnlineService`, `TreoBootReceiver`, `TreoTileService`
- `storage.DocumentProvider`
- NST adaptive/background frame scheduler (`backgroundFps`, `effectiveFpsLimit`, `allowFrame`, `acceptedFrames`, `droppedFrames`)

These components are intentionally not declared in the FPS manifest yet: the corresponding classes/resources are not in this source tree. Declaring absent classes would cause component instantiation failures. The original compiled DEX cannot safely be copied wholesale because the app's source generation, dependencies, resource IDs, package identity and JNI/native library versions differ.

## Porting constraints

- Keep `applicationId = com.nst.j2me1.fpsboost`; do not change it to the original package.
- Keep the unique `converted_nstfps.*` cache names and existing FPS/logic-speed controls.
- Do not copy original `.so` files; rebuild native code from this repository's matching C/C++ sources.
- Recreate source-level implementations and resource dependencies for each feature, then add its manifest component only after the class exists.
- Use app-specific provider authorities (never `com.nst.j2me1.*`) to avoid collisions with the installed original app.
- Validate on Android 14+ as well as older supported versions. Foreground-service type and background-start restrictions must be respected.

## Current verification

Phase 1 is a manifest-only change. Android Gradle build and merged-manifest validation are pending on this branch. No claim is made that the missing feature implementations are already ported.
