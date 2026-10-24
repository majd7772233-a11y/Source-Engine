# Changelog

All notable user-visible changes are recorded here. Versioned releases are produced only from explicit semantic-version tags or an opt-in manual GitHub Actions run; a successful debug build is not a release.

## Unreleased

- Library-first launcher with game discovery, readiness explanations, per-game details, scanning, rescan and recovery states.
- SAF-backed local folder selection with persisted access revalidation; unsupported cloud/document providers and unavailable/non-writable paths are rejected.
- Layered Liquid Glass-inspired launcher surfaces with an animated multicolor backdrop and a user-controlled motion fallback.
- Configurable per-game on-screen movement/fire/jump/use/reload controls with per-profile persistence and reset.
- Advanced system compatibility diagnostics and an honest disclosure that a working in-app server browser is unavailable until a verified native query/join interface exists.
- Hardened startup input validation, internal component exposure, runtime asset replacement, and updater behavior.
- Native runtime packaged for ARMv7 (armeabi-v7a) only. Lawfully obtained compatible game content must be supplied separately.

## Release artifact policy

- source-engine-android-debug is a debug build for development and diagnostics.
- source-engine-android-release-* is generated only through the explicit release path, built with the Ant release target, aligned, signed using GitHub Actions secrets, and verified after signing.
- Signed artifacts include a SHA-256 report and certificate/signature verification report. A successful CI build does not prove real-device or game compatibility.


## Stage 13 — Storage reliability

- Hardened recovery from corrupt folder-selection preferences, including Android's wrong-type `SharedPreferences.getString()` exception case.
- Added regression coverage for fully and partially corrupted selection records and stale-grant cleanup. Physical-device provider verification remains pending.
