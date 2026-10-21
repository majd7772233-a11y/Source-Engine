# Product-Quality Audit — Source Engine for Android

**Audit date:** 2026-10-21  
**Repository:** `majd7772233-a11y/Source-Engine`  
**Baseline:** `android-fixes` at `8b204a102437a20f621e42381c8f38b7b10119dc`  
**Scope:** User-visible logical defects, storage/permission behavior, feature truthfulness, UX/localization, missing capabilities and verification gaps. This is not a compilation-error review.

## Executive finding

**The APK build/CI passing is not a release-readiness signal.** The repository contains useful implementation and static/JVM quality gates, but source inspection reveals consequential user-facing gaps. The user's reported folder-picker failure is treated as a real failure observed on their device. This source audit does not claim to have reproduced it independently, because the exact selected provider and device logs are not available.

No application behavior was changed as part of this initial audit. Findings are recorded so remediation can be done as a coherent final Stage 12, not scattered across already-labelled “complete” implementation stages. Stage 12 is the final, open-ended stage; no ETA is set.

### How to read severity

- **P0 / Critical:** blocks an essential setup or launch journey for affected users.
- **P1 / High:** misleading or broken user-facing behavior, significant compatibility gap, or major acceptance blind spot.
- **P2 / Medium:** unavailable capability, inconsistency, or missing feedback that needs explicit scope/communication.
- **Unverified:** a claim that requires physical-device/game-runtime evidence; it is not labelled as a confirmed code defect unless the code supports that conclusion.

## 1. Critical/high findings

### PQ-01 — Storage picker rejects valid user expectations and reports several causes as one error
**Severity: P0 for users hitting the rejection.**  
**Evidence:** `src/me/nillerusr/StorageAccess.java` (especially `Api21.resolveFilesystemPath`, lines 114–148), `src/me/nillerusr/DirchActivity.java` (lines 74–102), and `res/values/strings.xml`.

The folder selection bridge permits only URI trees whose authority is exactly `com.android.externalstorage.documents`. It then converts the tree ID to a filesystem path and requires a directory that is both readable and writable. Other document/cloud providers are rejected because the native runtime expects a real path, not a generic `content://` URI. The caller maps a null path and a failed persistable grant to the same message, so it does not tell the user which condition failed. The message itself asks the user to ensure readability, while the implementation also rejects `canWrite()==false`.

The exact condition for the user's device cannot be identified from the displayed message alone: provider allow-list rejection, tree/document ID parsing, returned permission flags, persistable-grant failure, volume state, or filesystem checks can converge on that same error.

**User impact:** a user can pick a folder in Android's chooser and still be unable to use it, without knowing which folder/provider choice is compatible or why the selection failed. Read-only media may be rejected even if reading the game data would be enough; whether writable access is actually required must be established from the engine's true write destinations, rather than presumed.

**Required result:** distinguish error causes, validate the actual access requirements, tell the user which provider/location to choose, preserve retry/cancel behavior, and test real providers and volumes. Do not represent this as “fixed” based on the SAF intent or static source checks alone.

### PQ-02 — Requested “highest possible permission” is not implemented as an explicit Android storage-access design
**Severity: P1.**  
**Evidence:** `AndroidManifest.xml`; `LauncherActivity.applyPermissions` and `onRequestPermissionsResult` (around lines 65–124 and 617–618).

The manifest declares `WRITE_EXTERNAL_STORAGE` and `RECORD_AUDIO`; from API 23 the app requests both together. There is no user-facing special “all files access” request/settings-return flow for Android 11+. `requestLegacyExternalStorage=true` is not a substitute for an OS-aware permission flow across current Android versions.

The app also treats denial of either item as one generic storage/game-permission failure. Declining microphone permission therefore follows the same failure branch as declining storage permission, even though microphone use is a separate capability and may be optional for a particular game.

**Required result:** document a version-by-version permission plan, request only permissions needed for the action, separate optional audio permission from game storage, and evaluate `MANAGE_EXTERNAL_STORAGE` only if the engine genuinely requires broad file access and the use/policy permits it. Special access needs an explicit user-approved Settings flow. Android apps cannot grant themselves root access, cross into every other app's private directory, or bypass provider/OS restrictions.

### PQ-03 — Game-folder selection can derive a launch root outside the selected tree grant
**Severity: P1.**  
**Evidence:** `src/me/nillerusr/DirchActivity.java`, around lines 84–101; `src/me/nillerusr/LauncherActivity.java`, around lines 645–660.

If the selected folder itself contains `gameinfo.txt`, the picker stores its parent as `gamePath` and the selected folder name as `gamedir`. However, the persisted SAF tree grant is still for the selected child. The launcher’s “related to saved tree” check accepts ancestor/descendant relationships and does not require the path given to the engine to remain inside the granted tree. This is a scope mismatch: on scoped-storage devices, a readable selected child does not automatically prove access to its parent.

**Required result:** choose one documented folder model (select a game root, or select a game directory and keep every path/grant requirement consistent), reject scope escalation and test both folder layouts.

### PQ-04 — Scan limits and unreadable folders can appear to be a definitive empty library
**Severity: P1.**  
**Evidence:** `src/me/nillerusr/GameDiscovery.java`, `scan` / `scanDirectory` (roughly lines 63–132) and `LauncherActivity.scanLibrary`.

Discovery is bounded to depth 4 and 1,200 directories. When the count limit is reached the walk simply stops; it does not return a “partial/truncated” state. A null result from `listFiles()`, a denied nested directory, or an invalid saved root may be skipped without a visible per-root warning. The launcher can consequently show “No candidate game folders found” when the search was incomplete or inaccessible.

**Required result:** return structured scan outcomes: complete, partial/limit reached, unreadable root/child, invalidated media, cancelled, and no candidates. Show the number of roots checked and a recovery action. A partial scan must never silently replace the last known-good result.

### PQ-05 — “Ready to try” is a heuristic, not an actual compatibility test
**Severity: P1 (wording/expectation gap).**  
**Evidence:** `src/me/nillerusr/GameDiscovery.java`, especially the `launchable` predicate around lines 143–185 and `hasContentMarkers` around lines 285–301; `res/values/strings.xml`.

Readiness depends on a readable/parseable `gameinfo.txt`, a non-empty VPK or detected BSP map marker, the expected native libraries being present and supported ABI. These are useful preflight checks but do not prove that all mandatory assets are present, that the engine can load the selected game/mod, or that startup/gameplay will work. The UI does disclose that compatibility is not guaranteed, which is good; however, the strong “Ready” state can still be read as a compatibility verdict.

**Required result:** label each check precisely (metadata / asset marker / native package / ABI) and distinguish “passes preflight” from “launch confirmed.” Add actual game/mod runtime evidence before making compatibility claims.

### PQ-06 — Last-launch history can be updated before any successful launch
**Severity: P1.**  
**Evidence:** `src/me/nillerusr/LauncherActivity.java`, in `startSource` (around lines 680–689).

`GameDiscovery.recordLaunch` is called immediately before `startActivity(intent)`. The SDL activity can still fail to start or the native runtime can reject/crash during initialization. The library can then display a recent “Last launched” value although only an attempt was made.

**Required result:** distinguish “last attempted” from “last successful launch,” or report success only after a startup-ready signal from the runtime.

### PQ-07 — Arabic localization is materially incomplete and the current CI check misses it
**Severity: P1.**  
**Evidence:** `res/values/strings.xml`, `res/values-ar/strings.xml`, `scripts/verify-stage11.sh`.

A source-level count gives 132 default-locale string keys and 98 Arabic keys. **34 English keys have no Arabic translation.** Android falls back to the default resource; this is not a crash, but it means key parts of the app do not meet the Arabic-first experience. Stage 11’s check ensures Arabic keys do not exist without a default key; it does not require every default key to have an Arabic counterpart.

Missing Arabic keys:

`srceng_launcher_about_text`, `srceng_launcher_set`, `srceng_launcher_error_missing_obb_a`, `srceng_launcher_error_missing_obb_b`, `srceng_launcher_error_test_write`, `srceng_launcher_error_no_permission`, `srceng_launcher_error_find_gameinfo`, `srceng_launcher_game_menu`, `srceng_launcher_touch_controls`, `srceng_launcher_volume_buttons`, `srceng_launcher_volume_buttons_desc`, `srceng_launcher_check_updates`, `srceng_launcher_immersive_mode`, `srceng_launcher_screen_resolution`, `srceng_game_hl2`, `srceng_game_hl2ep1`, `srceng_game_hl2ep2`, `srceng_game_portal`, `srceng_dir_choice_btn`, `srceng_update`, `srceng_launcher_error_empty_path`, `srceng_launcher_error_invalid_path`, `srceng_launcher_error_start_failed`, `srceng_launcher_error_permission_result`, `srceng_library_use_help`, `srceng_storage_picker_explanation`, `srceng_storage_saf_info`, `srceng_storage_error_title`, `srceng_storage_picker_unavailable`, `srceng_storage_unsupported_provider`, `srceng_storage_unavailable`, `srceng_storage_revoked`, `srceng_storage_selection_cancelled`, `srceng_liquid_motion`.

**Required result:** complete Arabic coverage for user-facing strings, preserve format-placeholder parity, and make CI enforce complete locale coverage except for explicitly approved untranslated names.

## 2. High-priority feature/UX gaps

### PQ-08 — Touch controls are a fixed keyboard/mouse translation, not universal mobile controls
**Severity: P1 (scope and compatibility).**  
**Evidence:** `src/org/libsdl/app/TouchControlsOverlay.java`, `keyForControl`, `updateJoystick` and `onTouchEvent` (around lines 230–379); `scripts/verify-stage7.sh`.

The overlay simulates W/A/S/D for movement, Space for jump, E for use, R for reload and mouse button 1 for fire. The “joystick” is a directional keyboard simulator; it does not emit an analogue axis/gamepad stick. Bindings are not remappable per game/mod in the current UI, so nonstandard mods may behave incorrectly. The source comments and existing docs already qualify this limitation, but a running overlay should make the limitation obvious and runtime behavior still needs verification.

**Important correction:** I checked the hide/show path further. The gear menu is still drawn and its hit region is tested before the hidden-controls early return, so hiding the controls does **not** trap the user with no way to restore them. This is not listed as a defect.

### PQ-09 — Visual system does not yet demonstrate the full stated Liquid Glass target
**Severity: P1 (quality acceptance gap).**  
**Evidence:** `src/me/nillerusr/LiquidGlassTheme.java` and `res/layout/activity_directory_choice.xml`.

The theme builds animated radial gradients, translucent/tinted panels, borders, button states and elevation. Source inspection does not show background blur or refraction/distortion; the old directory picker still uses unrelated gray panels and small path text. This falls short of the repository’s stated goal of a consistent, depth-rich Liquid Glass experience. Whether the current screens look good, stay readable, animate smoothly or overheat on a physical device cannot be decided by source inspection alone.

**Required result:** review actual screenshots on devices, make picker/dialogs consistent, validate contrast and touch targets, and test older-API/low-end fallbacks and motion-off mode.

### PQ-10 — Update code is not a functioning in-app updater
**Severity: P2.**  
**Evidence:** `src/me/nillerusr/UpdateSystem.java`, `src/me/nillerusr/UpdateService.java`.

`UpdateSystem` checks a deployment version marker and only logs if it differs from the build’s marker. `UpdateService` explicitly discards legacy update notifications. No signed update manifest, verified release download, installation handoff or user-visible update flow is implemented. This is safer than installing unverified APKs, but the old update strings/service remnants must not be mistaken for a working update feature.

### PQ-11 — Server browser/join capability is absent, and should stay honestly labelled unavailable
**Severity: P2.**  
**Evidence:** `docs/ONLINE-FEATURES.md` and `LauncherActivity.showOnlineFeaturesStatus`.

The visible panel reports Android connectivity and explicitly says an in-app server browser is unavailable. There is no verified discovery/list/filter/join bridge. This is not a fake server list because the limitation is disclosed; it is still an unimplemented capability and must not be marked complete without a compatible-server end-to-end test.

### PQ-12 — Environment overrides can be partially ignored without clear user feedback
**Severity: P1.**  
**Evidence:** `src/com/valvesoftware/ValveActivity2.java`, `applyUserEnvironment`.

Malformed entries and invalid/reserved variable names are logged and skipped, but skipped entries do not always make validation fail or provide an actionable message in the launcher. A user can believe settings were applied when part of the configuration was ignored.

**Required result:** validate the entire override field before native launch; show which entry needs correction without leaking sensitive values into logs.

### PQ-13 — “Device compatibility diagnostics” is an informational report, not a runtime test
**Severity: P2 (capability/wording).**  
**Evidence:** `LauncherActivity.showCompatibilityDiagnostics`, `docs/COMPATIBILITY.md`.

The report surfaces device/build properties, advertised GLES level, display metrics, a memory snapshot, output-device count on supported Android versions and native-library presence. It does not create a GL context, benchmark a game, prove game audio is audible or measure thermal behavior. Existing documentation discloses these limitations; keep the UI accurately framed as a diagnostic snapshot and do not count it as compatibility certification.

## 3. Permission, privacy, UI and release reviews still needed

- **Permissions/privacy:** requesting microphone and storage together is not least-privilege; review `android:allowAudioPlaybackCapture="true"` as an intentional audio-capture/privacy choice rather than inherited configuration. Validate each permission path on Android 17–20, 21–29 and modern Android versions where available.
- **Design consistency/accessibility:** the main launcher, old directory-selection layout, advanced panel, alert dialogs and touch overlay need an actual end-to-end visual/accessibility pass (RTL/LTR, font scaling, small displays, keyboard/controller focus, TalkBack labels and cancel/back behavior).
- **Legal/attribution:** About text carries upstream credits/contact/donation details and a “Not for commercial use” statement. This is not enough to determine the license of every source/native binary. Review actual repository and dependency licenses and preserve required notices; do not infer permissive redistribution rights from the About text alone.
- **Release path:** prior roadmap explicitly leaves secret-backed signing validation, user setup/troubleshooting documentation, license review and final signed-install acceptance open.

## 4. What has and has not been verified

### Verified by source inspection
- The storage resolver and picker error branches described above are present.
- 34 default string keys have no Arabic resource counterpart.
- Discovery has silent limits/error-paths and readiness is a preflight heuristic.
- Touch controls have a fixed key/mouse mapping and no analogue stick output.
- The current update service is disabled, server browser is disclosed unavailable, and compatibility diagnostics are snapshots.
- The user-reported provider message exists in resources and is reachable when path resolution/persistence returns failure.

### Not established by this audit
- Which exact provider/permission condition failed on the user’s device; this needs a selected-URI authority/grant/volume diagnostic or log.
- That any lawful game or mod successfully enters gameplay on a physical device.
- Correct touch/gamepad mapping inside a real game, successful pause/resume, audio routing, frame pacing, heat/power use or crash-free long sessions.
- Visual quality of the currently installed APK, including actual blur/performance on this device.
- Successful secret-backed release signing, legal clearance, or release upgrade behavior.

Stage 11’s 73 JVM assertions and successful static/build workflow are useful signals, but they do not cover the missing Android/provider/native-runtime behaviors above.

## 5. Remediation order

1. Storage picker + permissions + tree-grant/path consistency (PQ-01–03).
2. Accurate scan outcomes, readiness and launch-history state (PQ-04–06).
3. Arabic localization and CI parity enforcement (PQ-07).
4. Input mapping/runtime validation and complete screen-by-screen UX review (PQ-08–09).
5. User-visible validation for settings and truthful update/server/diagnostic feature labels (PQ-10–13).
6. Physical-device acceptance, docs, license/attribution, privacy review and signed-release verification.

All remediations belong to the final **Stage 12.x**. This audit sets no delivery date and does not claim the fixes are implemented.
