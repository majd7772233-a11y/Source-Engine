# Source Engine for Android — Roadmap

> **Roadmap status:** Phase 0 is the active integration and preparation phase. Checkboxes are evidence-based: an item is not complete merely because a file exists or a workflow is defined. A phase is complete only when its deliverables, verification, and user-visible behavior have been checked together.

## 1. Product goal

Turn this repository into a maintainable Android launcher and runtime integration for Source-engine games, with a dependable installation/build pipeline, a discoverable game library, reliable game-directory detection and launch, robust storage handling, configurable controls and graphics, and a premium **Liquid Glass** launcher UI. The launcher must remain usable on the intended Android range, including older devices; effects that require newer Android APIs need a graceful fallback. The game renderer/HUD is a separate scope from the launcher and must not be described as redesigned unless native/game-side code actually changes.

This repository is **not itself the complete Source SDK/game-content distribution**. The current CI script clones external repositories (notably `nillerusr/srcsdk`) and builds native components from them. Source game data such as `gameinfo.txt`, VPKs, and proprietary assets must be supplied lawfully by the user; the app must detect missing content and explain what is missing instead of implying that installing the APK installs a full game.

## 2. Repository facts established from code

- GitHub repository: `majd7772233-a11y/Source-Engine`.
- At audit time the default branch was `android-fixes` at `7fb280dadf9c0199d1fa287844a02292ee9d281a`; other branches were `con-wrappers` at `bb39775ebbcd692d36183984e2a4a026f7d07724` and `test` at `d8b4ffc21c4119a40c5c21f723a8a5360d466ce2`.
- The default branch is an older Ant/ADT-style Android project: root `build.xml`, `project.properties` (`target=android-29`), `AndroidManifest.xml`, Java under `src/`, and Android resources under `res/`. There is no Gradle wrapper or root `build.gradle` in the audited default-branch root.
- The launcher entry point is `me.nillerusr.LauncherActivity`; the directory picker is `me.nillerusr.DirchActivity`; native startup integration is in `com.valvesoftware.ValveActivity2`; SDL Android glue is under `org.libsdl.app`.
- The manifest on `android-fixes` declared package `com.valvesoftware.source`, min SDK 17, target SDK 24, version 1.12, and `android:debuggable="true"`. The `con-wrappers` and `test` manifests use different package/version/target choices. The requested canonical application ID is `com.magd.source.engine`.
- `LauncherActivity` imports `com.valvesoftware.source.R`, defaults the game path to `/srceng`, persists launch arguments/path/environment in shared preferences, and launches `SDLActivity`. `ValveActivity2` defaults to game directory `hl2`, searches for `gameinfo.txt` using shallow directory scans, and configures native environment variables.
- `DirchActivity` manually lists filesystem directories using `java.io.File` and guesses external storage paths from `/storage/`; this is not a reliable modern Android Storage Access Framework implementation.
- The workflow file `.github/workflows/build.yml` is present on the audited branches, but its trigger is tied to `con-wrappers`. It installs Java 8/Ant and external SDK/NDK repositories, clones `nillerusr/srcsdk`, calls `scripts/workflow.sh`, and uploads through external scripts. The workflow and the branch it expects must be made consistent.
- The `con-wrappers` branch contains native-build material absent from the audited `android-fixes` root, including `jni/` and `libs/armeabi-v7a/`; it also has native wrapper code and a hard-coded, limited game list. The `test` branch has another divergent launcher/package setup. These branches are not interchangeable snapshots.
- A source-code review is not a successful build. At roadmap creation, no clean, reproducible build or device smoke test had been demonstrated.

## 3. Rules for working through this roadmap

1. **“Stage N” means the whole stage.** Finish every subtask and its acceptance criteria before reporting it complete; do not stop after the first convenient subset.
2. Before editing, read this roadmap and `GPT.md`, inspect the current branch and latest commit, and check whether the intended work has already been completed. Update this roadmap when reality changes.
3. Keep one canonical integration branch and one coherent application ID. Do not merge branches blindly: compare file-by-file, preserve unique working native/JNI artifacts, resolve semantic conflicts deliberately, and record the provenance of imported code.
4. Do not delete native libraries, SDK glue, assets, or source files merely because they look duplicated. Verify architecture, ABI, origin, and references first.
5. Keep UI work consistent with the established app structure. The target design is genuine layered **Liquid Glass**: translucent material with convincing depth, highlights/refraction-like treatment, controlled blur, animated multicolor light, polished icons and motion—not just purple cards. Provide a performant fallback for older Android versions and lower-end devices.
6. Separate launcher UI from in-game rendering. Do not claim in-game HUD, touch controls, or engine behavior is fixed by changing the launcher alone.
7. Do not bundle or fetch proprietary game content in a way that violates licensing. Build the open integration code and provide clear user-supplied-content checks.
8. Never claim a build, test, installation, launch, or performance improvement unless it was actually run and its result inspected. When a build cannot be run, state the exact blocker.
9. Prefer observable user outcomes: no dead buttons, silent failures, invalid paths, misleading success states, inaccessible settings, crashes, or confusing errors.
10. Preserve a useful change history and use clear English commit messages. Avoid unrelated formatting churn.

## 4. Stages

### Stage 0 — Audit, branch integration, project normalization, and hand-off (current)
- [x] Inventory the three branch heads, compare branch-level diffs, inspect the manifests, root build files, workflows, key Java entry points, JNI build files, native library inventory, and localized resources. The first pass is documented; remaining line-by-line call-site/runtime verification is still required before Stage 0 acceptance.
- [x] Select `android-fixes` as the canonical integration branch and create a curated multi-parent integration commit whose tree preserves the canonical launcher while incorporating the NDK `jni/` subtree from `test`, the shared ARMv7 native-library tree, and useful localized community-contact updates from `con-wrappers`. Incompatible alternate launcher/package code and unrelated/broken Discord automation were intentionally excluded rather than blindly overlaid. Commit: `95da41713d9df94f42a11cdd7d0e927fc9ee2c40`.
- [x] Set the manifest application package to `com.magd.source.engine`, update all identified generated-`R` imports in `LauncherActivity`, `DirchActivity`, `UpdateService`, and `UpdateSystem`, and preserve implementation namespaces (`me.nillerusr`, `com.valvesoftware`, `org.libsdl`). Runtime/package consistency still needs a clean APK build.
- [x] Normalize local build entry points and ignore rules without relocating source/native files: `build.sh` no longer points at a developer-specific SDK path; `scripts/build-java.sh` delegates to it; `scripts/workflow.sh` uses environment-driven paths and corrected `srcsdk` paths; `.gitignore` now excludes local SDK/NDK/source checkouts and generated output.
- [x] Removed the tracked `debug.keystore` after removing the workflow's copy step; the build now lets Android tooling create/use a local debug key. Added ignore rules for local properties, generated resources/output, SDK/NDK checkouts, and Python cache files. No release key is configured or committed.
- [x] Establish checked, documented build entry points and expected output (`bin/srceng-debug.apk`), including explicit SDK/Java/Ant checks and separate Java-only versus native+APK scripts. The build has not yet been executed successfully, so reproducibility remains unverified.
- [x] Align `.github/workflows/build.yml` with `android-fixes`, add manual dispatch, fail if required directories/tools/APK output are missing, and upload the APK as a workflow artifact instead of relying on old Discord/deploy credentials. External dependencies are still floating and must be pinned/reviewed later.
- [ ] Confirm manifest/activity/resource/package consistency and inventory all known defects without claiming runtime fixes that have not been tested.
- [ ] Acceptance: one documented canonical branch contains the unique required code from all three branches; package ID is consistent; the repository structure and build commands are documented; a clean CI build is either green or blocked by a clearly reproduced, actionable dependency/toolchain issue.

### Stage 1 — Reproducible build and native runtime integrity
- [ ] Trace `scripts/workflow.sh`, `scripts/upload-to-github.sh`, `scripts/send-to-discord.py`, `build.sh`, `build.xml`, `project.properties`, JNI makefiles, and all external clone/submodule steps end to end.
- [ ] Fix path assumptions, quoting, error propagation, executable permissions, dependency downloads, generated `build_info.xml`, artifact names, and CI secrets handling.
- [ ] Define the supported ABI matrix based on actual engine/native compatibility; verify every packaged `.so` has the correct ABI and required dependencies. Do not label an ABI supported just because a directory exists.
- [ ] Add deterministic build diagnostics, artifact verification, APK inspection, and a documented local build.
- [ ] Acceptance: clean checkout builds the same expected APK in CI; APK contains the expected Java resources and native libraries; no success artifact is published after a failed build.

### Stage 2 — Startup, lifecycle, and crash-free launch
- [ ] Trace launcher button → intent extras/preferences → SDL activity → `ValveActivity2` native initialization → engine entry point.
- [ ] Fix lost or stale intent values, null/empty values, activity recreation, background/resume, orientation changes, repeated taps, and returning from a game.
- [ ] Replace silent exceptions and unhelpful native-start failures with actionable diagnostics and safe recovery.
- [ ] Validate initialization order for environment variables, VPK extraction, app/native library paths, and game path.
- [ ] Acceptance: valid content launches; invalid/missing content returns to a usable launcher with a clear explanation; repeated launch/resume does not duplicate or corrupt sessions.

### Stage 3 — Game discovery and content validation
- [ ] Replace assumptions that only `hl2` exists with a data-driven scanner for candidate game directories and their `gameinfo.txt` files.
- [ ] Make scanning bounded, null-safe, permission-aware, case-tolerant, and resistant to unreadable directories and malformed content.
- [ ] Distinguish a game root, a game/mod directory, engine support, required assets, and optional content. Do not show a game as launchable until required files and native support are validated.
- [ ] Add a library view with game name, detected status, path, icon/art where available, last launch, and useful empty/error states. No hard-coded list of four games as a substitute for detection.
- [ ] Acceptance: valid supported games/mods are discovered; unsupported or incomplete folders are explained; rescans and removal/update of paths work without stale entries.

### Stage 4 — Storage permissions, removable media, and path reliability
- [ ] Replace unreliable `/storage/` guessing and broad `java.io.File` browsing with Android Storage Access Framework where appropriate; persist URI grants and revalidate them.
- [ ] Support internal shared storage, user-selected folders, and removable SD/USB storage only where Android/device permissions actually allow it. Never imply the app can bypass scoped storage.
- [ ] Resolve URI-to-engine access safely; if native engine code requires real filesystem paths, implement and document a supported access strategy rather than pretending every URI is a path.
- [ ] Fix path normalization, trailing separators, spaces/non-ASCII paths, missing directories, read-only media, revoked permissions, and removable media disconnects.
- [ ] Acceptance: users can select/reselect a supported game folder, the selection survives process restart when permitted, and access errors are clear rather than silent.

### Stage 5 — Launcher information architecture and core UX
- [ ] Replace the legacy form-heavy launcher with a coherent, responsive game-library home while preserving working launch arguments, environment overrides, and advanced settings.
- [ ] Add clear first-run setup, game-folder selection, scan/rescan, per-game details, launch readiness, loading/progress, empty states, errors, and recovery actions.
- [ ] Remove dead/hidden controls and commented-out UI that misleads users; every visible action must work and persist as intended.
- [ ] Improve accessibility, touch target sizing, landscape/portrait behavior where supported, keyboard/controller navigation, contrast, localization, and long-name handling.
- [ ] Acceptance: a new user can understand what is required, select a game, see why it is or is not ready, launch it, and return without needing undocumented knowledge.

### Stage 6 — Liquid Glass visual system and motion
- [ ] Define design tokens for material, tint, blur, edge highlights, gradients, depth, typography, iconography, spacing, animation timing, and pressed/focused/disabled states.
- [ ] Build layered glass panels with meaningful translucency, subtle distortion/refraction-like effects, specular highlights, ambient color, depth, and a living multicolor background; avoid flat opaque cards pretending to be glass.
- [ ] Create polished game artwork cards, navigation, dialogs, buttons, selectors, settings, loading/progress states, and empty/error states in one consistent visual language.
- [ ] Implement animation with lifecycle awareness, reduced-motion/performance controls, and a fallback that works on older Android versions; avoid relying on Android 12-only `RenderEffect` as the only blur path.
- [ ] Keep effects GPU/memory-conscious and validate on representative low-end devices. Do not sacrifice legibility or interaction feedback for effects.
- [ ] Acceptance: all launcher screens share the same visual system; effects degrade gracefully; no obvious frame drops, unreadable text, or broken touch interaction. This stage does not claim to redesign the in-game HUD.

### Stage 7 — Controls, input, and gameplay usability
- [ ] Inventory the existing SDL touch overlay, gamepad/controller, keyboard, mouse, volume-button, and pointer paths from code and native integration.
- [ ] Provide configurable on-screen controls where technically supported: position, size, opacity, sensitivity, layout presets, reset, and per-game profiles.
- [ ] Fix multitouch edge cases, touch capture/release, overlapping controls, orientation changes, controller hot-plug, and input focus transitions.
- [ ] Add sensible defaults and a safe reset path; distinguish launcher controls from in-game controls.
- [ ] Acceptance: supported input methods work in a real game session; configuration persists and can be restored; unsupported combinations are clearly documented.

### Stage 8 — Graphics, audio, performance, and device compatibility
- [ ] Trace OpenGL/GLES and GL4ES/Regal configuration, resolution/scaling, texture/memory behavior, audio routing, and device-specific workarounds.
- [ ] Provide understandable graphics/audio settings only for options the runtime actually honors; validate that settings are applied and persisted.
- [ ] Measure startup time, frame pacing, memory use, thermal behavior, and crashes on representative hardware/Android versions; optimize evidenced bottlenecks.
- [ ] Handle unsupported GLES capabilities and missing native dependencies with diagnostics rather than a blank screen.
- [ ] Acceptance: a documented compatibility matrix exists and settings have verified effects; no untested performance claims.

### Stage 9 — Network/browser and optional online functionality
- [ ] Audit `libServerBrowser.so`, native server-browser interfaces, network permissions, and actual browser UI/data flow before promising multiplayer or server browsing.
- [ ] Implement server discovery/refresh, loading/error/empty states, filtering, joining, timeout/cancel behavior, and safe network handling only where the underlying engine supports it.
- [ ] Clearly separate a server browser from a multiplayer backend; do not invent a project server or imply online play is guaranteed for every game/mod.
- [ ] Acceptance: supported online features work end to end against a known compatible server, or are explicitly marked unavailable with a reason.

### Stage 10 — Reliability, security, and user-visible logical defects
- [ ] Audit permission requests/results (including empty result arrays), null list results, unchecked filesystem calls, unsafe string/argument construction, shell-script interpolation, silent catch blocks, and resource cleanup.
- [ ] Remove production debug flags; review exported activities/services, intent validation, file access, update logic, network calls, and logging of sensitive local paths.
- [ ] Validate updater code and remote endpoints before enabling it; never silently download/execute unverified content.
- [ ] Add robust validation and understandable recovery for missing game files, wrong paths, failed extraction, native load errors, storage removal, and insufficient space.
- [ ] Acceptance: critical paths fail safely and explain the next action; no unhandled crash or false-success path remains in the tested scenarios.

### Stage 11 — Automated tests, static checks, and CI quality gates
- [ ] Add tests for path normalization, game discovery, readiness checks, preference migration, argument construction, and relevant pure Java logic.
- [ ] Add manifest/resource/package validation, shell linting, dependency/ABI checks, APK sanity checks, and workflow validation.
- [ ] Run tests on pull requests and the canonical branch; preserve logs and publish artifacts only after required checks pass.
- [ ] Add a device smoke-test checklist for installation, first run, storage selection, game scan, launch, pause/resume, audio/input, and exit.
- [ ] Acceptance: the full defined test suite runs reproducibly and failures are visible; test coverage is not represented as device validation.

### Stage 12 — Release packaging, documentation, and final acceptance
- [ ] Establish release versioning, release notes, signing through repository secrets, artifact checksums, and a clear distinction between debug and release APKs.
- [ ] Never commit signing keys/passwords or use the committed debug keystore as a release key. Verify signing configuration and secret names before enabling release publication.
- [ ] Write user-facing setup/troubleshooting documentation for lawful game-content setup, storage permissions, supported games/ABIs, known limitations, and crash diagnostics.
- [ ] Review license/attribution notices for the launcher, SDL, native wrappers, external source checkouts, and any redistributed binaries.
- [ ] Run the complete acceptance matrix on the final canonical branch and verify release artifact contents and installation.
- [ ] Acceptance: a reproducible, signed release can be built from the documented canonical branch; users can install it, configure content, and understand limitations.

## 5. Cross-stage defect register

These are confirmed code/design risks or missing capabilities observed during the initial audit. They must be rechecked against the integrated branch before being labelled fixed.

| ID | Area | Observed issue / risk | Required outcome |
|---|---|---|---|
| R-01 | Branches | Three divergent branches with different manifest/package/native content | One audited integration branch; preserve unique required files |
| R-02 | Build | Legacy Ant build depends on local SDK/toolchain and external clones | Reproducible documented build and green CI |
| R-03 | CI | Workflow trigger targets `con-wrappers`, while default branch is `android-fixes` | Triggers and scripts match canonical branch |
| R-04 | Native | JNI/prebuilt libraries are not present in the audited default-branch root | Verify and integrate required native code and ABI libraries |
| R-05 | Identity | Package differs between branches; requested ID is `com.magd.source.engine` | Consistent manifest/package-generated resource references |
| R-06 | Manifest | Debuggable app, old SDK declarations, inconsistent targets | Explicit supported API policy and release-safe manifest |
| R-07 | Discovery | Default game is `hl2`; shallow scans and fixed game assumptions | Reliable data-driven detection and validation |
| R-08 | Storage | Manual `/storage/` enumeration and `java.io.File` access | Permission-aware SAF/removable-media strategy |
| R-09 | UX | Legacy launcher form, incomplete/hidden options and limited feedback | Complete library/setup UX; no dead visible controls |
| R-10 | Visual design | No unified Liquid Glass system | Real layered glass, motion, fallbacks and performance validation |
| R-11 | Input | Full touch-control customization is not established by the audited code | Verify and implement only supported controls end to end |
| R-12 | Online | Presence of native server-browser library does not prove complete UI/service | Verify end-to-end functionality before advertising |
| R-13 | Testing | No demonstrated reproducible build/device smoke test at audit time | CI and documented device validation |
| R-14 | Documentation | Missing or insufficient project, build, content and troubleshooting guidance | Maintain README and this roadmap/working notes |
| R-15 | Secrets/artifacts | Debug keystore is tracked; upload script uses external credentials | Remove sensitive material safely; use GitHub secrets |

## 6. Completion ledger

Update this ledger at the end of every stage with commit SHA, files changed, checks actually run, failures still open, and the next stage. Never mark a stage complete from code inspection alone when its acceptance criteria require a build or runtime test.

- **Stage 0:** In progress — curated integration/package/build-script/workflow changes committed. A clean CI/build result has not been observed; line-by-line audit and build validation remain open. Latest integration commit: `95da41713d9df94f42a11cdd7d0e927fc9ee2c40`.
- **Stage 1–12:** Not started / pending re-audit after Stage 0.


### Stage 0 execution log — 2026-10-16
- Canonical branch retained as `android-fixes`; a curated multi-parent integration commit records `con-wrappers` and `test` ancestry without blindly taking their conflicting trees.
- Preserved the current `me.nillerusr` launcher and SDL activity namespace; set application package to `com.magd.source.engine` and updated the four identified `R` imports.
- Integrated the `test` branch NDK tierhook subtree and retained the shared `armeabi-v7a` native libraries (35 libraries were enumerated, including `libfirst.so` and `libtier0.so`). The `test` branch's alternate `in.celest` launcher and its unrelated Python/Discord bot/cache artifacts were not copied.
- Preserved the three upstream localized About-text community contact updates from `con-wrappers`.
- Removed the committed debug keystore and its copy step, added local/build artifact ignores, hardened permission callback handling against empty results, removed a developer-specific SDK path from `build.sh`, and made the native workflow path/environment driven.
- Updated CI to build on `android-fixes` and upload `bin/srceng-debug.apk` as an artifact. The repository currently reports zero Actions runs for this branch; no successful build has been verified yet.
- **Still open:** validate every relevant file/call-site on the integrated tree; check script executable modes and dependency/toolchain compatibility; run CI or a clean build; fix any actual failures; only then close Stage 0.
