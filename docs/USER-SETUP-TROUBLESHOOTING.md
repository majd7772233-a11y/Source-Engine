# Source Engine for Android — Setup and Troubleshooting

This application is a launcher/native-runtime integration. It does **not** contain Half-Life 2, Portal, or licensed game assets. Supply only game files you are entitled to use.

## Before choosing a game folder

- The APK built by the current workflow contains a **32-bit ARM (armeabi-v7a)** native runtime. The manifest declares Android API 17 as the minimum. A device that lacks 32-bit ARM compatibility cannot load this native payload.
- Put each compatible game/mod in a normal folder on shared Internal storage or on a mounted, writable SD/USB volume. The folder the launcher scans should be the **parent that contains the game's folder**. For example:

  ```text
  Internal storage/
    Games/
      hl2/
        gameinfo.txt
        pak01_dir.vpk
  ```

  Choose Games, not hl2 (the folder that itself contains gameinfo.txt). The launcher stores the granted tree scope and rejects paths that escape it. Because the native engine uses ordinary filesystem paths and may write configuration/cache data, this build requires both read and write folder grants plus a currently readable and writable local directory.
- The game needs valid, readable gameinfo.txt and content files. A detected file/ABI preflight is not a guarantee that every game or mod starts.

## Which storage location should I select?

The native engine interface consumes **filesystem paths**. A generic Android content URI from a cloud/virtual document provider cannot be passed directly to this native engine. Choose the volume's ordinary local folder in the system folder picker (often labelled **Internal storage**, the SD-card volume name, or USB storage). “Recent”, a cloud drive, or a virtual document-provider folder may not map to a filesystem path.

Selection errors now distinguish common cases. A read-only result or a persisted grant missing write access is rejected rather than trusted based on `File.canWrite()` alone. If the saved URI is revoked, its volume disappears, its canonical mapping changes, or the volume becomes read-only, stale URI/path state is cleared and the folder must be selected again:


- **Unsupported provider/location:** select a normal local folder on Internal storage or a mounted SD/USB volume.
- **Read grant missing / grant could not be saved:** reopen the picker and confirm the selection.
- **Volume unavailable:** reconnect/unlock the volume and select its folder again.
- **Folder is not writable:** this version's native runtime path uses the selected game root for content and may write game-side files; choose a writable folder/volume. Read-only media is not accepted as a launch root.
- **Game folder itself selected:** choose its parent folder, then choose the game from the library.
- **Path outside granted folder:** select the parent folder containing the game directory again. The launcher deliberately refuses to use an ancestor that Android did not grant.

On Android 5.0/API 21 and newer the application requests a persistable folder grant and maps supported external-storage trees to a real path. Older Android versions use a constrained legacy shared-storage browser; removable-volume discovery varies by device.

## Permissions: what the app can and cannot do

The launcher requests the legacy shared-storage permission where Android requires it for this build, and the system folder picker separately grants access to the selected tree. Microphone permission is not required to open the launcher and is no longer requested together with storage at first launch; a native feature may request it when needed.

Android permissions are **not root access**. They do not unlock every other app's private directory or override a volume that is disconnected, unsupported, or read-only. The project currently uses a legacy Ant build targeting API 29 SDK tools and a manifest target SDK of 24; it does not request Android 11+ “all files access”. Introducing that special permission requires an intentional target-SDK, compatibility, privacy and distribution-policy review; adding a permission declaration alone would not solve a provider that cannot supply the native engine with a filesystem path.

## The library says no games were found

A complete scan with no candidate folders is different from an incomplete scan. The launcher now reports inaccessible folders and when its safety limits are reached, and preserves the previous successful library snapshot rather than replacing it with a partial scan. Try selecting a narrower parent folder and scanning again. Check that files are present and the volume is mounted.

The “ready to try” state is a preflight based on metadata/content markers, required native libraries and ABI—not certified gameplay compatibility. Not every Source game or mod is supported.

## Controls, updates and online features

- The touch overlay simulates keyboard/mouse inputs (W/A/S/D, Space, E, R and mouse fire). It is not a true analogue gamepad and nonstandard mods may use different key bindings.
- This build has no in-app update checker, APK download, or automatic installer. Do not expect an in-app update notification; install only a candidate distributed after the repository's provenance/redistribution gate has been explicitly cleared.
- The online status panel reports Android connectivity only. A working in-app server browser and join flow are not implemented.
- Compatibility diagnostics are an informational snapshot, not an actual game benchmark, GL-context test, audio test, or thermal-stability guarantee.

## Capturing a useful failure log

If the app still fails, record the exact error and the tested APK's SHA-256 from its build report. With Android Debug Bridge (ADB) connected, capture:

```sh
adb logcat -c
adb logcat -v threadtime -s SRCAPK SRCAPK_STORAGE ExtractAssets SDL AndroidRuntime
```

Reproduce the failure while the second command is running, then save the output. Include the device model, Android version/API, whether the folder was Internal/SD/USB, the provider shown in Android's picker, and whether the selected folder itself contained gameinfo.txt. The `SRCAPK_STORAGE` line records provider authority, returned/persisted read-write flags, volume state, read/write probes and error codes; it deliberately omits full URI/document ID/absolute path. Avoid posting personal files or credentials.

## Data safety

Folder permission state is persisted by Android and can become invalid when a removable volume is disconnected or access is revoked. Re-select the folder when asked. Do not clear app data as a first troubleshooting step; doing so removes launcher preferences and saved library state. Keep backups of game data and saves before testing unfamiliar builds.


## Gameplay audio and screen recording

The manifest explicitly opts into Android playback-audio capture with `android:allowAudioPlaybackCapture="true"`. This is intended to let users choose an external Android screen-recording/streaming app that captures eligible game audio. **Source Engine does not start recording or streaming audio itself.** On Android 10+ the external recorder must use the playback-capture API, hold `RECORD_AUDIO`, and obtain the user's approval for a `MediaProjection` session; actual capture also depends on playback usage and capture policy. See [Android's official playback-capture guide](https://developer.android.com/media/platform/av-capture).

There is no in-app switch for this manifest opt-in. It is a product/privacy choice that still needs testing with the system recorder and a trusted external recorder on supported devices. A successful build does not establish that capture works for a particular game or Android-vendor build.
