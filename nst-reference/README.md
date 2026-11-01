# NST APK reference integration

The supplied NST APK was used as a behavioral/asset reference, not copied wholesale into the new application.

Identified and carried into this source tree:
- app identity for a side-by-side build: `com.nst.j2me1.fpsboost` (the old package `com.nst.j2me1` remains untouched)
- default external working directory: `/J2ME-NST`
- `assets/defaults/system.props` keys and values as extracted from the supplied NST APK
- `assets/Roboto-Regular.ttf`
- the existing J2ME Loader/JL-Mod emulator engine, MIDP/M3G/Micro3D APIs and native modules are retained from the source project and rebuilt from source

Important limits:
- The APK is compiled code. Without the exact NST source/repository, every proprietary or undocumented behavior cannot be reconstructed with certainty from the APK alone.
- The older NST `.so` binaries were deliberately NOT copied into this newer source project: they are a different build set and replacing current native libraries could create JNI/ABI mismatches. Native modules in this project must be built from its own C/C++ source and pinned submodule.
- Both old NST and this new app use `/J2ME-NST` by default. Do not run both apps simultaneously; they may access the same game/RMS files. Back up that directory before first launch.
