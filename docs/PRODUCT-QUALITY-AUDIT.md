# Product-Quality Audit — Source Engine for Android

> **Roadmap model correction (2026-10-22):** Earlier sections were written when Stage 12 was mistakenly treated as the terminal remediation gate. The current model makes Stage 12 the permanent QA/feedback/defect-intake lane, maps larger work to Stages 13–19, and uses an unnumbered **Final Acceptance** gate. Older stage-12 references below are historical audit/CI records, not the current stage plan.

**Audit date:** 2026-10-21  
**Repository:** `majd7772233-a11y/Source-Engine`  
**Baseline:** `android-fixes` at `8b204a102437a20f621e42381c8f38b7b10119dc`  
**Scope:** User-visible logical defects, storage/permission behavior, feature truthfulness, UX/localization, missing capabilities and verification gaps. This is not a compilation-error review.

## Executive finding

**The APK build/CI passing is not a release-readiness signal.** The repository contains useful implementation and static/JVM quality gates, but source inspection reveals consequential user-facing gaps. The user's reported folder-picker failure is treated as a real failure observed on their device. This source audit does not claim to have reproduced it independently, because the exact selected provider and device logs are not available.

No application behavior was changed as part of this initial audit. Findings were originally collected under what was then called the final Stage 12; that interpretation has been superseded. Use Stage 12 for ongoing feedback/triage and route implementation work to the numbered milestones in ROADMAP.md. No ETA is set.

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

At the 2026-10-21 baseline, the manifest declared `WRITE_EXTERNAL_STORAGE` and `RECORD_AUDIO`, and the launcher requested both together. PR #18 split the initial request so it asks for shared-storage permission only; optional microphone permission is handled later by SDL/native functionality. The manifest still has no user-facing Android 11+ special “all files access” request/settings-return flow. `requestLegacyExternalStorage=true` is not an OS-aware storage strategy for current Android versions.

**Implemented in source:** the startup shared-storage request is separated from optional microphone use. **Still required:** document and test an OS-version-by-version permission plan and evaluate `MANAGE_EXTERNAL_STORAGE` only if broad file access is demonstrably necessary and permitted by policy. Any special access requires a clear user-approved Settings flow and verified return state. Android apps cannot grant themselves root access, cross into every other app's private directory, or bypass provider/OS restrictions.

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

The historical Stage 11 run had 73 JVM assertions; the later canonical regression suite reached 80 assertions. Both are useful signals, but neither covers the missing Android/provider/native-runtime behaviors above.

## 5. Remediation order

1. Storage picker + permissions + tree-grant/path consistency (PQ-01–03).
2. Accurate scan outcomes, readiness and launch-history state (PQ-04–06).
3. Arabic localization and CI parity enforcement (PQ-07).
4. Input mapping/runtime validation and complete screen-by-screen UX review (PQ-08–09).
5. User-visible validation for settings and truthful update/server/diagnostic feature labels (PQ-10–13).
6. Physical-device acceptance, docs, license/attribution, privacy review and signed-release verification.

Newly observed bugs first enter the continuous **Stage 12 feedback lane**. Current mapping: PQ-01–03 → Stage 13; PQ-04–06 → Stage 14; PQ-08 → Stage 15; PQ-07/PQ-09 → Stage 16; PQ-10–12 → Stage 17; runtime/compatibility coverage → Stage 18; provenance/privacy/release → Stage 19. This audit sets no delivery date and does not claim all remaining acceptance is complete.


## 6. Historical Stage 12 remediation snapshot — 2026-10-22 (reclassified under the corrected roadmap)

This section captures the initial candidate-branch snapshot before PR #18 was merged. The changes listed below were subsequently covered by the candidate and canonical CI runs in Sections 7–9; those results still do not constitute physical-device validation:

- Replaced one generic SAF failure with specific results for unsupported provider, malformed document ID, disconnected/unmounted volume, missing read grant, failure to persist the grant, unreadable/unwritable path and scope mismatch. The UI explains that the parent containing game folders must be selected; selecting the game folder itself is rejected before widening access to its parent.
- Added canonical grant-root containment on selection and launch. A sibling with a shared textual prefix is not accepted as a descendant.
- Added detailed scan results that flag unreadable folders and depth/directory limits. The UI now preserves the last successful library snapshot and explains incomplete scans rather than presenting them as an empty definitive scan.
- Changed history text to “Last attempt” until a runtime-ready signal exists.
- Startup storage permission is no longer bundled with microphone permission; later optional SDL permission results are returned to native code.
- Environment override strings are validated as a whole before applying entries, and invalid/reserved settings return a user-facing message instead of being silently skipped.
- All 151 default resource keys now have Arabic translations; the verifier checks key parity and format-placeholder parity. Touch-control settings explain the current fixed keyboard/mouse mappings and their limitation. The directory picker now uses shared visual styling.
- Added `docs/USER-SETUP-TROUBLESHOOTING.md`.

**Still open at that snapshot:** provider behavior on the affected physical device, Android-version/removable-volume matrix, launch/gameplay/input/audio/visual/performance acceptance, license/privacy review, and secret-backed signed-release/upgrade acceptance. Canonical CI evidence was subsequently updated. Under the corrected roadmap these items are assigned to Stages 13–19; new findings continue to enter Stage 12.


## 7. Candidate CI evidence — 2026-10-22 (runtime acceptance still pending)

The Stage 12 initial-remediation branch completed candidate workflow [39679419842](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39679419842) successfully at commit `59f03a4c2013fe36f42ab9d417ebfb07e261d10d`. The run passed the Stage 0–12 verifiers, 80 JVM assertions, Arabic locale-key and placeholder parity (151 keys in each locale), YAML/workflow checks, ShellCheck/actionlint, locked native dependency fetch/build, Java/Ant packaging, APK identity/ABI/native dependency checks (35 ARMv7 ELF libraries), checksum recording and artifact upload.

- APK package: `com.magd.source.engine`; ABI: `armeabi-v7a`; APK size: 56,589,391 bytes.
- APK SHA-256: `aae99b750b88030a0ff0cc5d7bf293b260c369c942e0b61439fa77af2707e295`.
- Debug artifact: [12383626531](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39679419842/artifacts/12383626531); ZIP digest `a1c770735469be9b585caaa22286ec3c9c7c75437129efb572869434850bdb5e`; expires 2026-11-04.
- Release build/signing was not executed in this candidate workflow. No physical-device storage-provider, gameplay, input, audio, accessibility, performance/thermal or signed-release validation is inferred from these results.

The code changes are a remediation tranche, not a declaration that all defects are fixed. PR #18 merged as `f530574869f51091b5dccf0365d12f582c737ce2`; canonical post-merge workflow [39680488677](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39680488677) passed. The original provider/device scenario still needs direct validation. That issue is now scheduled in Stage 13, while new findings return to the continuous Stage 12 lane.

## 8. Canonical post-merge CI — 2026-10-22 (runtime acceptance pending)

PR [#18](https://github.com/majd7772233-a11y/Source-Engine/pull/18) was merged into `android-fixes` as `f530574869f51091b5dccf0365d12f582c737ce2`. Canonical workflow [39680488677](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39680488677) completed successfully, passing all source-stage verifiers, 80 JVM regression assertions, workflow/lint checks, pinned native dependency retrieval/build, Java/Ant packaging, and APK integrity verification (35 ARMv7 ELF libraries).

- Canonical debug APK: 56,589,387 bytes; SHA-256 `d8d2721257bd129a4cd43aed2f740c8e5dd76bad1b350b30b54b9f750de826ef`.
- [Artifact 12383377856](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39680488677/artifacts/12383377856); ZIP digest `ba388bc3b1c9fe70ebe983c9259f4758f49bd761e87e636c6530a85375faab5d`; expires 2026-11-04.
- Release signing was not run. The actual user's SAF provider/volume behavior, compatible-game startup/gameplay, input/audio/accessibility/performance, legal/privacy review and signed release/upgrade remain unverified. A green CI build is not a device acceptance test.


## 9. Canonical Stage 12 license-gate acceptance — 2026-10-22

PR [#19](https://github.com/majd7772233-a11y/Source-Engine/pull/19) merged as `cbb4dfedc255c48100abc648e9a04805dc6a9213`. Canonical workflow [39832326942](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39832326942) completed successfully on that exact commit:

- Stage 1 static checks passed; Stage 12 reported 30 assertions including blocked/incomplete/complete synthetic review-record behavior.
- JVM regressions passed 80 assertions; workflow/YAML/action validation passed.
- Native build and APK verification passed for package `com.magd.source.engine`, ARMv7, 35 ELF libraries, 56,589,387 bytes, SHA-256 `8a16a300d4b4c5ee7a3333cb657dfcb996bbf4172c6adbfed2753fd91212b98b`.
- The audit returned `RELEASE BLOCKED`; debug-artifact upload was skipped, signed-release steps were skipped, and the workflow reported no artifacts. This is the expected protective behavior, not a public release or device-acceptance result.

Legal review has not been completed and no claim is made that the listed binaries are unlawful. Source provenance, license/notice obligations and redistribution rights need accountable review. Until that gate clears, approved downloadable APK artifacts remain blocked; this release prerequisite is tracked under Stage 19 and Final Acceptance.


## Stage 13 initial storage reliability changes — 2026-10-22

The source audit confirmed that the native interface requires real filesystem paths, so the SAF bridge intentionally supports the Android external-storage document provider rather than pretending any cloud/virtual provider URI is a local path. Existing code checked `File.canRead()` and `File.canWrite()`, but explicitly required only the returned SAF read flag and only revalidated persisted read access. Launch validation also collapsed different provider/volume/access failures into a generic null path and did not consistently clear stale URI/path preferences.

Changes on the Stage 13 branch:
- Require returned and persisted SAF read **and** write grants; verify the persisted result before storing a folder.
- Explicitly reject a mounted read-only volume with a writable-folder explanation.
- Clear stale selection state after revoked grants, failed path mapping, changed canonical path, failed access checks, disconnected volumes and read-only storage.
- Add a `SRCAPK_STORAGE` line with provider authority, returned/persisted permission flags, volume state, read/write probes and categorized resolver/selection outcomes. The log omits full URIs, document IDs, absolute paths, filenames and file contents.
- Add JVM regression tests for valid Unicode/space paths, missing grants, persistence failure, selecting the game folder itself, traversal, revoked grants, disconnected/read-only media, insufficient persisted write grants and stale path state.

An additional audit found that canonical containment inside the volume root alone would still allow a malformed tree ID such as `Games/../Sibling` to resolve to a sibling folder. Stage 13 now rejects explicit `.` and `..` path segments before canonical mapping and has a regression assertion for this case.

The pinned SDK source also contains native filesystem write call sites for game state and replay output, so the writable-root requirement is retained pending proof of a working, appropriately scoped alternate write directory. These files are evidence of SDK behavior, not proof that every target build enables those optional features.

**Acceptance limitation:** these JVM tests use host-side Android API fakes. They cannot prove a particular OEM's document picker/provider behavior or reproduce the user's original error. The physical-device test remains open until run with the affected provider/device and a candidate APK that is permitted by the existing distribution gate.


### Stage 13 follow-up finding — partial persisted tree-selection state

A source review after the initial Stage 13 CI pass found that `LauncherActivity.startSource()` entered SAF grant/scope validation only when the saved mapped-path key existed. A stale/corrupt record containing only the URI could therefore bypass that launch-time check. The follow-up makes the validation predicate treat either URI or mapped-path key as evidence of an existing selection and adds JVM/static regression coverage. The implementation is now source-checked, but device/provider acceptance remains pending.
