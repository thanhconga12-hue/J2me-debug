# FPS Booster / NST source rebuild — validation report

## Automated checks passed

- XML parse: all 104 XML resource files under `app/src/main/res` parsed successfully.
- View-binding resource IDs for the four new FPS controls exist in `activity_config.xml` and are referenced by `ConfigActivity`.
- Source-level migration check: the game scanner accepts the FPS cache, legacy NST cache names, or a source `res.jar` plus descriptor file, so it does not delete an existing NST game solely because the new cache is absent.
- Isolated JDK test for `NstFpsBytecodeCache`: versioned SHA-256 stamp creation, reuse check and automatic invalidation when `res.jar` bytes change.
- NST asset comparison: `system.props` values match the extracted APK and `Roboto-Regular.ttf` is byte-identical.
- `tools/run-game-time-smoke-test.sh` passed for 1x real-time behavior, virtual clock, yield/interrupt semantics, sleep, timed monitor wait, timed join, `TimeUnit.sleep`, `LockSupport` parking, 2x/4x scaling, reset and argument validation.

## Build attempt

Command attempted:

```sh
./gradlew :app:assembleEmulatorDebug --no-daemon --stacktrace
```

It stopped before Gradle configuration because the wrapper could not resolve `services.gradle.org` (`UnknownHostException`). Android SDK Platform 34, Android Build Tools and NDK `22.1.7171670` are also not installed in this workspace. The TinySoundFont source submodule is empty in the GitHub ZIP and must be fetched before native build.

## Not tested

- Full Gradle/AGP/NDK compilation.
- APK signing, installation, launch and runtime crash behavior.
- Real device FPS measurements or game-by-game compatibility.
- Every NST-only compiled feature identified in `NST_INTEGRATION_NOTES.md`.

No APK was generated. All results above are source/static checks, not an assertion that an APK is ready to install.
