# J2ME NST FPS — scope and integration notes

## What this project is

This is a new side-by-side Android emulator project based on the uploaded `JL-Mod-dev` source. It includes an experimental FPS/logic-speed feature and selected NST compatibility data copied from the uploaded `J2ME_NST.apk`. It is **not** a byte-for-byte or feature-complete reconstruction of NST, and it is **not** an APK build.

APK identity observed from the supplied file:
- package: `com.nst.j2me1`
- version name: `3.0-nst-open`
- version code: `14`

The new project uses `com.nst.j2me1.fpsboost`, so it can be installed side-by-side and will not replace the original package or require the original signing key.

## NST details carried into this source project

- New display name: `J2ME NST FPS`.
- New application ID: `com.nst.j2me1.fpsboost`.
- Default shared external work directory: `/J2ME-NST`.
- The full `assets/defaults/system.props` values from the provided NST APK.
- `assets/Roboto-Regular.ttf` copied byte-for-byte from the supplied APK.
- The upstream JL-Mod Java/Kotlin and C/C++ emulator engine retained as the implementation base.
- FPS Booster settings stored in each game's profile: render FPS cap (30/60/90/120/unlimited), optional experimental logic speed (1.5x/2x/3x/4x), logic speed off by default.

## Legacy game-folder and cache compatibility

The old NST build uses the `converted.zip` / `converted.dex` cache names. The new build uses `converted_nstfps.zip` / `converted_nstfps.dex` so the old NST process will not load DEX that references the new `GameTime` helper.

The game-list scanner now recognizes either the new cache or the old NST cache. It also preserves an old game folder when its `res.jar` and descriptor metadata are present, so an existing game does not get deleted merely because the new cache has not yet been generated.

When a game is launched, the new build hashes `res.jar` with SHA-256 and includes the bytecode-hook version in the stamp. It reuses the FPS DEX cache only when both match; if an existing NST installation changed/replaced its JAR or the instrumentation version changes, the new build reconverts that JAR into its own cache. The old `converted.zip` / `converted.dex` files are not overwritten.

Both apps use `/J2ME-NST` by default and keep the database filename `J2ME-apps.db`. The NST APK's Room DEX strings show an `apps` table with the same `AppItem` fields and unique `path` index used by this JL-Mod source model; however, the database has not been opened by the rebuilt app, so migration compatibility is still unverified. Back up that directory before testing. Do not run both apps at the same time: they can write the same game folders, database, and RMS files. If the old NST app used a custom work directory rather than `/J2ME-NST`, set the same directory in the new app's settings; app-private preferences are not shared across different package IDs.

## FPS Booster behavior and limitations

- Render FPS target is a ceiling/limiter, not a frame generator. Choosing 120 does not guarantee 120 real frames; the game, CPU/GPU, display refresh and emulator rendering path still decide the achievable rate.
- Logic speed is experimental and defaults off. For converted game bytecode, the bridge redirects common `Thread.sleep`, timed `join`, timed `Object.wait`, `TimeUnit.sleep`, `LockSupport.parkNanos/parkUntil`, `System.currentTimeMillis`, `System.nanoTime`, and Java `Timer` calls.
- `Thread.yield()` preserves its scheduler-yield semantics; it is not changed into a timed sleep.
- The hooks do not virtualize every time source (for example, all `Date`/calendar paths, native/JNI timers, custom busy loops, game-server timing, audio clocks, or arbitrary game-specific clocks). Some games may not speed up or may become unstable. Network games may desynchronize. Keep Logic Speed off unless testing a game deliberately.

## NST-only features found in the APK but not ported from source

The APK contains compiled classes that do not have source equivalents in the uploaded `JL-Mod-dev` tree. They are not claimed as implemented in this rebuild:

- `LauncherRouterActivity`.
- `bubble.BubbleService`, `BubbleSettingsActivity`, `BubbleSettingsFragment`, and helper classes under `bubble`.
- `keepalive.TreoBootReceiver`, `TreoOnlineService`, and `TreoTileService`.
- `storage.DocumentProvider`.
- NST-specific adaptive/background frame scheduling code visible in the DEX (including symbols such as `backgroundFps`, `effectiveFpsLimit`, `allowFrame`, `acceptedFrames`, and `droppedFrames`). The current source keeps the JL-Mod Canvas pipeline and adds per-profile frame-cap controls; it is not a line-for-line port of NST's scheduler.
- The source keeps the JL-Mod launcher icon and most of its existing settings UI; the NST icon and any other branded resources were not confidently mapped from the APK's obfuscated compiled resource table.

The original APK's `.so` libraries were deliberately not copied into this project. The JNI/native modules are retained from JL-Mod source and must be rebuilt from their matching C/C++ source. Mixing a different-generation `.so` set with this source risks JNI and ABI mismatches.

## Build requirements and command

Required toolchain:
- JDK 17.
- Android SDK Platform 34 and Android Build Tools 34.0.0.
- Android NDK `22.1.7171670`.
- Gradle Wrapper 8.13 (downloads on first build).
- Network access to Google Maven / Maven Central and the Gradle distribution host.

The GitHub ZIP does not include the TinySoundFont submodule. Run:

```sh
./scripts/fetch-tinysoundfont.sh
```

Then configure `local.properties` with your Android SDK path and build:

```sh
./gradlew :app:assembleEmulatorDebug
```

Expected output:

```text
app/build/outputs/apk/emulator/debug/J2ME-NST-FPS_3.0-nst-fps-emulator-debug.apk
```

A manual GitHub Actions debug-build workflow is included at `.github/workflows/nst-fps-debug.yml`.

## Verification status in this workspace

Passed:
- JDK smoke test for `GameTime`: default 1x clocks, virtual clock, interrupt/yield semantics, sleep, wait, join, TimeUnit, LockSupport, 2x/4x scaling and argument validation.
- XML parsing for edited Android resources.
- Source-level checks for the unique DEX cache names and old-cache recognition.
- Exact byte comparison for the copied NST `Roboto-Regular.ttf`; `system.props` values match the APK (line-ending format differs only).

Not completed:
- Android Gradle build, APK signing, install, launch, and real-game compatibility tests.
- Porting the NST-only compiled classes listed above.

A Gradle build was attempted, but the wrapper could not resolve `services.gradle.org` (`UnknownHostException`). This workspace also has no Android SDK or NDK. No installable APK was produced. Do not treat this source bundle as a tested APK.
