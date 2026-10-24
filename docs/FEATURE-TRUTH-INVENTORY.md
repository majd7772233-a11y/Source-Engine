# Source Engine Android — Feature-to-effect inventory

**Review date:** 2026-10-24  
**Scope:** launcher controls, saved settings, online/update disclosures, and permission/privacy behavior. This source trace is not a substitute for device acceptance.

## Launcher and library controls

| Control/state | Consumer | Observable effect or persistence | Acceptance boundary |
|---|---|---|---|
| Select folder | `button_gamedir` → `DirchActivity` → `StorageAccess.selectTree` | Opens SAF or the constrained legacy browser; validates local path, read/write access and granted scope; saves the selection | Generic cloud/virtual providers cannot be passed to native code as filesystem paths. Real provider behavior remains in Stage 18. |
| Scan current / Rescan / Cancel | `LauncherActivity.scanLibrary`, `scanCurrent`, `rescan`, `scanCancelButton` | Bounded scan, cooperative cancellation, distinct complete-empty/incomplete/failed results | Incomplete scans must not be presented as a definitive empty library. |
| Game card / Details | `showGameDetails` | Shows folder/content markers, preflight reason and last attempt | “Ready to try” is not successful gameplay; “Last attempt” is not verified launch success. |
| Use / Play / Remove | `selectGame`, `startSource`, `GameDiscovery.savePaths` | Saves selection, validates again before native launch, or removes a saved library root | Supplied game content must be appropriate for the user to use; compatibility is not guaranteed by source/JVM tests. |
| Footer Launch | `LauncherActivity.startSource` → `SDLActivity` / `ValveActivity2.initNatives` | Revalidates game root and any saved SAF tree, saves settings, forwards arguments and environment | Native startup and in-game behavior require Stage 18 physical-device acceptance. |
| About | `button_about` | Opens informational dialog | Does not fetch or install updates. |
| Advanced open/close | `button_advanced` / `advancedToggle` | Shows/hides panel, changes label and moves accessibility focus; Back closes panel first | Advanced fields do not guarantee every option works for every game/mod. |

## Saved advanced settings

| Setting | Consumer and persistence | Guardrails |
|---|---|---|
| Command line (`argv`) | `edit_cmdline` → `saveSettings` → `mod/argv` → SDL intent | Capped at 4096 characters; NUL rejected; supplied to native arguments rather than a shell. |
| Environment overrides (`env`) | `edit_env` → `mod/env` → `EnvironmentOverrides` → native `setenv` | Whole list parsed before mutation. Invalid syntax, duplicates, launcher-owned paths, Android runtime variables and dynamic-linker variables fail. Input/count/entry limits remain enforced; setter failures abort launch and identify the failed variable. |
| Game root (`gamepath`) | `edit_gamepath`, folder picker, `mod/gamepath` | Normalized and checked before launch; stored tree scope revalidated when present. |
| Game directory (`gamedir`) | Folder picker/profile selection and launch validation | Defaults to `hl2`; selected directory is forwarded and checked by discovery. |
| Liquid Glass motion (`liquid_motion`) | Advanced checkbox → SharedPreferences → `applyGlassTheme` | Persisted and reapplied; visual/performance behavior remains a physical test item. |
| Compatibility diagnostics | Advanced action → `showCompatibilityDiagnostics` | Informational snapshot only, not a benchmark, GL-context test, audio test or compatibility certification. |

## Online, updates and privacy

- **Connectivity:** online dialog reads Android's active-network state. It does not probe an actual game server or guarantee DNS, reachability, authentication, compatibility or multiplayer.
- **Server browser/join:** unavailable. No verified native list/filter/query/join path is wired; the UI must not fabricate servers or imply a successful join.
- **Application updates:** there is no in-app checker, APK download or automatic installer. The unused marker-checker and disabled service were removed. The release/provenance gate must not be bypassed.
- **Microphone:** `RECORD_AUDIO` remains for optional SDL/native features; it is not bundled with startup storage permission. Native code may request it only when a relevant feature needs it; microphone denial is not a storage failure.
- **Playback capture:** the manifest intentionally opts in via `android:allowAudioPlaybackCapture="true"` so an eligible external recorder selected by the user may capture eligible game playback where Android policy/consent allows. The app itself does not create a screen-recording/streaming session. Actual recorder behavior remains untested and is deferred to Stage 18.
- **Distribution:** CI builds/verifies an APK internally, but distribution and signing steps remain blocked until accountable source-provenance and redistribution clearance.

## Acceptance boundary

The Stage 17 static verifier checks this inventory against source wiring and localization; the pure-Java tests cover parser behavior. Neither proves Android-provider behavior, native gameplay, server joining, microphone output, external recording, or performance.
