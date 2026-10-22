# Source Engine Android — Physical-Device Smoke-Test Checklist

This checklist is separate from CI. A successful JVM test or APK build is not evidence that Android storage providers, the renderer, audio, a controller, or a real game works.

## Test record

- Date/time:
- Tester/device label:
- Device manufacturer/model:
- Android version/API:
- CPU ABI(s) and GPU/driver:
- APK workflow run and artifact ID:
- APK SHA-256 (from build-reports/SHA256SUMS):
- Game/mod name and version (only content the tester is entitled to use):
- Outcome: Not run / Pass / Fail / Blocked
- Log filename and issue link:

## Before testing

- [ ] Confirm that the device supports the packaged 32-bit ARM ABI (armeabi-v7a) and meets the manifest minimum Android API 17.
- [ ] Record the APK SHA-256; use the exact build under test for all results.
- [ ] Keep a backup of any existing launcher settings and user game files. Never delete user game content as part of this test.
- [ ] Clear Android logcat, start capture, and record the test start time:
  - adb logcat -c
  - adb logcat -v threadtime -s SRCAPK ExtractAssets SDL AndroidRuntime

## Install and settings migration

- [ ] Install on a clean profile; verify first-run guidance and the empty-library state.
- [ ] Upgrade over a build with saved argv, gamepath, env, gamedir, and visual preferences; confirm user values remain unchanged.
- [ ] Force-stop and reopen the launcher; verify settings and library entries persist.
- [ ] Deny a requested permission, reopen the affected action, grant permission later, and confirm there is a clear recovery message without a stuck progress indicator or disabled Launch button.

## Storage and game discovery

- [ ] Open the system folder picker and cancel; confirm the selection is not silently changed.
- [ ] Select a local Internal-storage folder; confirm Android returns both read and write grants and the launcher successfully persists both.
- [ ] Select a local SD/USB volume where the device exposes a filesystem path; verify the library after process restart.
- [ ] Try a tree selection that contains a game folder with spaces/non-ASCII names; verify it succeeds.
- [ ] Where a malformed URI/path can be injected in a test build, verify `Games/../Sibling`, `../outside`, and explicit `./` segments are rejected rather than remapped to a sibling directory.
- [ ] Try a read-only volume or a selection missing write access when the device permits it; the launcher must display the specific reason and must not save false-success state.
- [ ] Revoke the grant, disconnect a removable volume, and reconnect it; verify stale URI/path state is cleared and the folder must be selected again.
- [ ] Capture `SRCAPK_STORAGE` diagnostics and confirm they show provider/grant/volume/access/reason metadata but never a full URI or absolute path.
- [ ] Disconnect a removable volume and reconnect it; verify stale entries are not shown as launch-ready and an actionable message appears.
- [ ] Rescan after adding/removing a game folder; verify the count and entries update and hidden/removed folders follow the documented behavior.
- [ ] Test valid gameinfo.txt and required assets, malformed/missing metadata, missing VPK/BSP content, unsupported ABI/native-library state (where reproducible), and long paths/names.
- [ ] Open game details for ready and not-ready entries; verify the reason, folder, launch root, game directory, optional content, and last-launch value.
- [ ] If a scan fails due to a provider/storage exception, verify the prior successful library snapshot is not replaced by partial results.

## Launcher UI and advanced settings

- [ ] Test portrait and landscape, scrolling, keyboard focus, and all action target sizes.
- [ ] Toggle Liquid Glass motion off/on; inspect scrolling, cards, buttons, dialogs and readability.
- [ ] Change launch arguments, environment overrides, and selected game; close/reopen the app and confirm each value persists.
- [ ] Verify invalid/too-long arguments and invalid game-directory names fail with an actionable message, without a crash or phantom launch progress.
- [ ] Verify scan/rescan progress always clears after success, cancellation, and a recoverable error.

## Real game launch and lifecycle

- [ ] Launch only a legally owned, compatible test installation; note exact game/mod version and content layout.
- [ ] Confirm that native startup either succeeds or displays a useful error for missing/incompatible content.
- [ ] Enter gameplay, move, fire, jump, use, reload, and test simultaneous movement + fire touches.
- [ ] Drag/reposition/resize controls, change opacity/sensitivity/preset, hide/show and reset; confirm values persist per game.
- [ ] Tap outside overlays and test hardware keyboard/mouse, controller connect/disconnect, and volume keys where hardware is available.
- [ ] Background the game, lock/unlock the device, rotate if supported, return to the app, and exit normally; confirm no stuck input, frozen camera, stale progress, or broken repeated launch.
- [ ] Verify speaker/headphone/Bluetooth audio where available and check pause/resume.
- [ ] On Android 10+ where supported, test external gameplay screen recording after explicit system consent; record whether eligible game audio is captured and whether stopping/revoking capture ends the session. Record the recorder/API/device. Source Engine itself does not start recording.
- [ ] Play for at least 10 minutes; note frame pacing, available memory, heat/throttling, battery drain and any crash.
- [ ] Repeat at least three cold starts and five warm starts on each representative device; attach measured times instead of estimates.
- [ ] Save logs and mark each item Pass/Fail/Blocked with reproducible steps. Do not mark an unrun test as passing.

## Required device matrix

Use at least two representative physical devices with different Android/API or GPU combinations where available. If only one device is available, record that limitation explicitly.

| Device | Android/API | ABI/GPU | Game/version | Install/scan | Launch/lifecycle | Input/audio | Performance/thermal | Logs/issue |
|---|---|---|---|---|---|---|---|---|
| Device A | Not tested | Not tested | Not tested | Pending | Pending | Pending | Pending | Pending |
| Device B | Not tested | Not tested | Not tested | Pending | Pending | Pending | Pending | Pending |
