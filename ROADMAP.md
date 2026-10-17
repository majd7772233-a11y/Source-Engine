# Source Engine for Android — Roadmap

> **Roadmap status:** Stages 0–9 implementation and CI verification are complete; physical-device/runtime acceptance remains pending for the items explicitly marked below. Checkboxes are evidence-based: an item is not complete merely because a file exists or a workflow is defined. A phase is complete only when its deliverables, verification, and user-visible behavior have been checked together.

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

### Stage 0 — Audit, branch integration, project normalization, and hand-off (completed)
- [x] Inventory the three branch heads, compare branch-level diffs, inspect manifests, root build files, workflows, Java entry points, JNI build files, native libraries, and localized resources. The final candidate adds repeatable source/manifest/shell checks; runtime behavior remains explicitly outside the claims made by this stage.
- [x] Select `android-fixes` as the canonical integration branch and create a curated multi-parent integration commit whose tree preserves the canonical launcher while incorporating the NDK `jni/` subtree from `test`, the shared ARMv7 native-library tree, and useful localized community-contact updates from `con-wrappers`. Incompatible alternate launcher/package code and unrelated/broken Discord automation were intentionally excluded rather than blindly overlaid. Commit: `95da41713d9df94f42a11cdd7d0e927fc9ee2c40`.
- [x] Set the manifest application package to `com.magd.source.engine`, update all five identified generated-`R` imports in `LauncherActivity`, `DirchActivity`, `UpdateService`, `UpdateSystem`, and `SDLActivity`, and preserve implementation namespaces (`me.nillerusr`, `com.valvesoftware`, `org.libsdl`). A clean APK build has now passed; the final candidate also checks the packaged APK identity.
- [x] Normalize local build entry points and ignore rules without relocating source/native files: `build.sh` no longer points at a developer-specific SDK path; `scripts/build-java.sh` delegates to it; `scripts/workflow.sh` uses environment-driven paths and corrected `srcsdk` paths; `.gitignore` now excludes local SDK/NDK/source checkouts and generated output.
- [x] Removed the tracked `debug.keystore` after removing the workflow's copy step; the build now lets Android tooling create/use a local debug key. Added ignore rules for local properties, generated resources/output, SDK/NDK checkouts, and Python cache files. No release key is configured or committed.
- [x] Establish documented build entry points and expected output (`bin/srceng-debug.apk`): `bash build.sh` generates required build metadata and packages existing native libraries; `bash scripts/workflow.sh` rebuilds native libraries from the external Source SDK and packages the APK. The CI native build and Ant packaging passed in run `38937163563`; root `build.sh` is syntax-checked by the final candidate.
- [x] Align `.github/workflows/build.yml` with `android-fixes`, add manual dispatch, check required tools/directories, and upload the APK as an artifact instead of relying on old Discord/deploy credentials. Explicitly select the cloned SDK and install the 32-bit runtime required by legacy `aapt`. External dependencies are still floating and must be pinned/reviewed in Stage 1.
- [x] Run repeatable source/manifest/shell checks and inspect the built APK's package ID and required ARMv7 native libraries in CI. All passed in run `38938001867` on candidate commit `bf7f506f741cac82c8f1419b4170e4ede3883cbe`.
- [x] Acceptance: one documented canonical branch contains the curated required code from all three branches; application identity is consistent; repository structure/build commands are documented in `README.md`; CI builds the APK and verifies its package/native payload; unresolved device/runtime and future-stage defects are accurately documented.

### Stage 1 — Reproducible build and native runtime integrity
- [x] Trace `scripts/workflow.sh`, `scripts/upload-to-github.sh`, `scripts/send-to-discord.py`, `build.sh`, `build.xml`, `project.properties`, JNI makefiles, and all external clone/submodule steps end to end.
- [x] Fix build path assumptions, shell quoting, fail-fast error propagation, XML-safe generated `build_info.xml`, stale APK/native output cleanup, artifact names, and unsafe legacy CI secret/deployment handling. All external native source revisions are pinned in `scripts/dependencies.lock` and verified by CI.
- [x] Define the currently verified packaged ABI as ARMv7 / `armeabi-v7a` only; the APK verifier checks the package identity, required native libraries, ELF machine type, and rejects native libraries outside this ABI. This does not imply runtime compatibility for every game.
- [x] Add fail-fast build diagnostics, `scripts/verify-apk.sh`, build metadata, SHA-256 output, and documented local build commands. Both checks passed in CI run `38972677450` on commit `ba054a72c3e74bc3b41fce6579f1f13f4611ed49`.
- [x] Acceptance: clean checkout builds and verifies the APK in CI; APK contains the expected Java resources and all 35 packaged ARMv7 ELF libraries passed architecture/dependency checks; artifact upload occurs only after success. Passed in run `38972677450` on commit `ba054a72c3e74bc3b41fce6579f1f13f4611ed49`. Physical-device/game-runtime testing remains outside this acceptance gate.

### Stage 2 — Startup, lifecycle, and crash-free launch
- [x] Trace launcher button → intent extras/preferences → SDL activity → `ValveActivity2` native initialization → SDL native thread. Launcher now forwards the current game path, arguments, and environment values explicitly instead of relying only on saved preferences.
- [x] Fix empty/unreadable game-root handling, game-directory traversal values, null directory listings, stale launch values, duplicate launch taps, and failure to re-enable Launch after returning to the launcher.
- [x] Fix Android API compatibility for runtime permission checks before API 23; reject empty permission results safely. Failed startup dialogs now explain the missing path/game metadata or native-data failure.
- [x] Make VPK extraction report success/failure, close streams, flush and sync output, verify a non-empty file, and persist the extraction version only after the write succeeds.
- [x] Apply validated user environment overrides before required runtime variables; protect reserved engine paths, check native `setenv` results, validate optional VPK paths, and report native initialization failures rather than continuing with incomplete configuration.
- [x] Ensure SDL static lifecycle state is reset after both successful shutdown and failed startup; handle repeated `singleTask` intents without silently changing an already-running native session. Existing pause/resume and configuration-change hooks were traced; manifest already declares SDL-handled orientation/configuration changes.
- [ ] **Runtime acceptance still unverified:** a lawful, complete compatible game installation and physical Android device are not available to CI. CI can verify Java compilation, APK packaging, static lifecycle guards, and artifact integrity, but cannot honestly prove in-game launch, background/resume behavior, or repeated launch behavior on a device. Do not mark runtime behavior as device-tested until the checklist in Stage 11 is run.

### Stage 3 — Game discovery and content validation
- [x] Replace assumptions that only `hl2` exists with a data-driven scanner for candidate game directories and their `gameinfo.txt` files.
- [x] Make scanning bounded, null-safe, permission-aware, case-tolerant, and resistant to unreadable directories and malformed content.
- [x] Distinguish a game root, a game/mod directory, engine support, required assets, and optional content. Do not show a game as launchable until required files and native support are validated.
- [x] Add a library view with game name, detected status, path, icon/art where available, last launch, and useful empty/error states. No hard-coded list of four games as a substitute for detection.
- [x] Acceptance implementation: discovered entries are data-driven; incomplete/unsupported candidates explain why they cannot launch; required VPK/BSP markers and optional content folders are distinguished; rescans rebuild the library from registered roots, removed entries remain hidden until the user explicitly scans their root again, and missing/stale paths are discarded.
- **Runtime acceptance note:** CI and APK packaging passed, but physical-device validation with multiple real game/mod folder layouts remains necessary before claiming every valid game is discovered or that engine compatibility is proven. The UI labels readiness as “Ready to try” and explicitly does not guarantee compatibility.
 
### Stage 4 — Storage permissions, removable media, and path reliability
- [x] Replace guessed `/storage/` volume enumeration with Android's Storage Access Framework (SAF) tree picker on API 21+; persist the read grant and selected tree URI, then revalidate the grant on launch.
- [x] Support primary shared storage and local removable SD/USB volumes only when the Android external-storage document provider maps the tree to a canonical, readable and writable filesystem path. Android 17–20 retain a constrained primary-storage browser because SAF tree selection is unavailable there.
- [x] Bridge SAF to the engine's real-path requirement conservatively: only accept `com.android.externalstorage.documents` tree URIs with validated volume/document IDs and canonical-path containment. Generic cloud/document providers are rejected; the engine is never given a `content://` URI as if it were a filesystem path.
- [x] Normalize selected paths and trailing separators; handle spaces/non-ASCII as ordinary Java path strings; recheck directory readability/writability, persisted grants, mounted removable volume state, and clear revoked/disconnected/read-only selections with actionable messages.
- [x] **CI acceptance:** PR [#7](https://github.com/majd7772233-a11y/Source-Engine/pull/7) merged as `de4de87043539facd5ed25c88ab4398e3e9142c8`. Run [38982919788](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/38982919788) passed Stage 0–4 checks, native compilation, Ant Java compilation/packaging, APK identity/ABI/dependency verification, and artifact upload. APK: 56,539,061 bytes; SHA-256 `71403db3618200d55e3996da601b936466961eefccdb9ea388f993b11eab3f92`; artifact [12081817272](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/38982919788/artifacts/12081817272), expires 2026-10-30.
- [ ] **Device acceptance pending:** install the APK on Android 5+ and Android 4.2–4.4 where available; verify internal-storage selection, SD/USB selection on a device that exposes it, app process restart and persisted grant, permission revocation, read-only media, and volume removal/reconnect. CI cannot simulate the platform document provider or real removable media.

### Stage 5 — Launcher information architecture and core UX
- [x] Replace the legacy form-heavy landing screen with a library-first home, first-run guidance, a prominent folder picker, library scan/rescan actions, and collapsed advanced settings while preserving saved command-line arguments, environment overrides, game path, and explicit SDL launch extras.
- [x] Add per-game details (readiness, explanation, detected folder, launch root, game directory, optional content, last launch), clear empty/error states, recoverable scan errors, and visible scan/launch progress.
- [x] Remove obsolete commented-out launcher controls and the legacy commented launch fallback. Keep every visible primary action wired; retain library remove/hide and explicit rescan behavior.
- [x] Improve cards, long-name/path truncation, 48dp minimum action targets, keyboard focusability, accessible details/status descriptions, Android 4.2 API guarding for live-region announcements, portrait-device install eligibility, and Arabic translations for launcher/library states.
- [x] **CI acceptance:** PR [#8](https://github.com/majd7772233-a11y/Source-Engine/pull/8) merged as `772a49b15163e3d354fda4d550a01e17930a1214`. Run [39070653298](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39070653298) passed Stage 0–5 static checks (16 Stage 5 assertions), locked native dependency fetch, native compilation, Java/Ant packaging, APK identity/ABI/dependency verification, and artifact upload. APK: 56,548,121 bytes; SHA-256 `22f26e01e5ffaa26718bc525bd7457126e6c320bdc1bc72e93a16e8a3741c1e9`; artifact [12115152142](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39070653298/artifacts/12115152142), expires 2026-10-31.
- [ ] **Device acceptance pending:** verify first-run flow, picker cancellation and successful selection, scan/rescan, game details/readiness, advanced settings persistence, portrait/landscape layouts, keyboard/controller focus, launch progress, and returning from an actual compatible game on physical Android hardware. CI cannot prove these user-visible interactions or game launch.

### Stage 6 — Liquid Glass visual system and motion
- [x] Define a shared visual system for translucent materials, tint, gradient edges, depth, typography, spacing, motion timing, and pressed/focused/disabled states. The legacy API-17+ app uses custom Drawable/GradientDrawable implementations and does not depend on newer blur APIs.
- [x] Add layered translucent glass panels with gradient depth, edge highlights, ambient multicolor light, and a continuously moving radial-gradient backdrop. This is a lightweight refraction-like treatment, not a claim of true backdrop blur.
- [x] Apply the shared style to game-library cards, header/footer navigation surfaces, advanced settings, buttons, text fields, loading/progress context, empty/error surfaces, and dialogs without removing existing interactions.
- [x] Add a persistent motion toggle, a static background fallback, API-guarded elevation, explicit pressed/focused/disabled button states, and pause the background animation when the launcher is paused.
- [x] Add `scripts/verify-stage6.sh`; CI checks 16 Liquid Glass assertions before compiling native dependencies and packaging the APK.
- [x] **CI acceptance:** PR [#9](https://github.com/majd7772233-a11y/Source-Engine/pull/9) merged as `239fd915f307b43aa63867722b42e1204c332217`. Post-merge canonical workflow [39072023024](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39072023024) passed Stage 0–6 static checks (including 16 Stage 6 assertions), native compilation, Java/Ant packaging, APK identity/ABI/dependency verification, and artifact upload. APK: 56,552,089 bytes; SHA-256 `59ad6cdd9b3f262c356e08d304759700c883cc80a38b2a0e89ef8c4d5dec96e7`; artifact [12114973248](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39072023024/artifacts/12114973248), expires 2026-10-31.
- [ ] **Device/performance acceptance pending:** inspect the visual treatment on real Android devices including a low-end device; test motion enabled/disabled, scrolling, dialogs, touch feedback, rotation, and thermal/battery/frame pacing. CI does not prove visual quality, actual blur, or smoothness. This stage does not redesign the in-game HUD.

### Stage 7 — Controls, input, and gameplay usability
- [x] Inventory the existing SDL input paths: Android touch/multitouch and pointer normalization in `SDLActivity`, hardware keyboard and mouse/pointer forwarding, volume/camera/zoom keys left to Android, and SDL joystick/gamepad enumeration plus add/remove polling in `SDLControllerManager`.
- [x] Add an in-game touch overlay with a virtual digital movement pad (W/A/S/D), fire via SDL mouse-button input, and jump/use/reload keyboard actions. This is intentionally conventional keyboard/mouse emulation; it does not claim analog-stick or universal mod-binding support.
- [x] Persist control position, size, opacity, sensitivity threshold, visibility, and layout preset per game profile (game path + game directory). Add classic, compact, and right-handed presets, drag-to-reposition editing, hide/show, and reset.
- [x] Harden multi-pointer handling: track pointer IDs independently, retain fire while multiple pointers hold it, avoid releasing keys still held by another input source, forward normal touches outside control hit regions, and release synthetic inputs on cancel/pause/destroy. Normalized positions survive orientation changes; existing SDL controller hot-plug polling is retained.
- [x] Add English/Arabic labels and a Stage 7 verifier with CI wiring.
- [x] **CI acceptance:** PR [#10](https://github.com/majd7772233-a11y/Source-Engine/pull/10), candidate commit `d172abdd639d314cae8f104d09dd35206f1a32b3`, passed Stage 0–7 static checks (19 Stage 7 input assertions), locked native dependency fetch, native compilation, legacy Java/Ant packaging, APK identity/ABI/dependency verification, and artifact upload in run [39073005622](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39073005622). APK: 56,562,054 bytes; SHA-256 `d9f0a8e4ce192d4122514b2ee774e095d56488d9a8c3a1d6f2880d46828337e4`; artifact [12115648990](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39073005622/artifacts/12115648990), expires 2026-10-31.
- [x] **Post-merge canonical verification:** PR #10 was squash-merged to `android-fixes` as `b46a6ae5fdff9b1c3328fe10b50dd1701f344c0a`. Canonical push workflow [39073356292](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39073356292) passed all Stage 0–7 static checks, native build, Ant APK packaging, APK identity/ABI/dependency verification, and artifact upload. Canonical APK: 56,562,047 bytes; SHA-256 `c78883743d21c5cf56372553096a59c3c148a5f9057dfd9a21eadfe6e3b8de69`; artifact [12115514936](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39073356292/artifacts/12115514936), expires 2026-10-31.
- [ ] **Device/game acceptance pending:** on a real compatible game, verify movement, firing, jump/use/reload bindings, simultaneous movement + fire multitouch, button dragging, size/opacity/sensitivity adjustments, profile persistence per game and reset, rotation, touch outside controls, controller connect/disconnect, keyboard/mouse, Android volume keys, and pause/resume without stuck inputs. Mods with nonstandard bindings may require in-game rebinding; CI cannot prove gameplay input works end to end.

### Stage 8 — Graphics, audio, performance, and device compatibility
- [x] Trace the repository-declared GLES 2.0 minimum, ARMv7-only native payload, packaged Regal/native libraries, SDL display surface, Android memory snapshot, and available audio-output reporting. See [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md).
- [x] Add localized, API-guarded device compatibility diagnostics in Advanced settings. Do not expose invented renderer/resolution/audio switches: existing command-line arguments and environment overrides remain the only generic advanced controls until a native consumer and effect are verified.
- [ ] Measure cold/warm startup, frame pacing, memory under a loaded game, sustained thermal behavior, audio routing, and crashes on at least two representative physical devices; optimize only repeatable bottlenecks. Physical-device access/results are not available in CI and have not been fabricated.
- [x] Diagnose missing required packaged native libraries and show system-reported GLES/ABI/display/audio/memory facts with explicit limits; retain the native startup error path for runtime/game failures.
- [ ] Acceptance: software implementation and CI checks pass; full stage acceptance remains open until the device matrix in docs/COMPATIBILITY.md contains actual test evidence and any evidenced issues are fixed.

### Stage 9 — Network/browser and optional online functionality
- [x] Audited the canonical manifest, launcher UI/data flow, native build outputs, APK verifier requirements, and Java/native integration. Android INTERNET and ACCESS_NETWORK_STATE permissions are declared; the launcher has no verified native server-list/discovery/join bridge. The current native build pipeline produces the engine, client/server wrappers, and GL4ES/Regal; a binary/library name alone cannot establish browser functionality.
- [x] Added an Advanced settings → Online features and server browser status panel. It reports Android's connectivity state without making a server request, then explicitly explains why in-app server browsing is unavailable. English and Arabic disclosures are included.
- [x] Avoided fabricated server lists, invented backends/endpoints, fake loading/progress, and nonfunctional join controls. Clearly separates Android network connectivity from game-server reachability and separates a game/mod's multiplayer support from a hosted project backend.
- [x] **Acceptance (capability-based):** online functionality unsupported by a verified native interface is explicitly marked unavailable with a concrete reason. No end-to-end server query or multiplayer session is claimed or simulated. See [docs/ONLINE-FEATURES.md](docs/ONLINE-FEATURES.md) and `scripts/verify-stage9.sh`.
- [ ] **Future browser acceptance remains blocked:** implement discovery/refresh/filter/join, timeout/cancel and real server testing only after a documented native interface and a known compatible server are identified.

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

- **Stage 0:** **Complete** — final verification candidate `bf7f506f741cac82c8f1419b4170e4ede3883cbe` passed run `38938001867`: source/manifest/shell checks, all five native builds, Ant APK packaging, application-ID inspection, required ARMv7 native-library checks, and artifact upload. The completion-record commit is synchronized across maintained branch refs. No device installation/launch is claimed. Integration commit: `95da41713d9df94f42a11cdd7d0e927fc9ee2c40`.
- **Stage 1:** **Complete** — canonical Stage 1 commit `96092dbfc7f030d643125505dbd51c36e2cb3145`; CI run [38973176340](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/38973176340) passed on the canonical branch. Immutable dependency locks, hardened build scripts, APK verification, and success-gated artifact upload are in place.
- **Stage 2 implementation:** merged by PR [#5](https://github.com/majd7772233-a11y/Source-Engine/pull/5), merge commit `98dfe6f894bc17a685d766379ab9e6158e788d7a`. CI run [38976380349](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/38976380349) passed source/shell checks, all Stage 2 static assertions, native compilation, APK packaging, APK validation, and artifact upload. Files changed: `LauncherActivity.java`, `ValveActivity2.java`, `ExtractAssets.java`, `SDLActivity.java`, `res/values/strings.xml`, `scripts/verify-stage2.sh`, `.github/workflows/build.yml`, `README.md`, `ROADMAP.md`, and `GPT.md`.
- **Stage 2 runtime acceptance:** **Pending device verification**, not falsely marked complete. No physical Android device or complete lawful game installation was available to this CI run; actual gameplay launch, pause/resume, and repeated-session behavior must still be exercised on-device. The code and build pipeline are merged; keep this explicit runtime limitation visible.
- **Stage 7 implementation and CI:** PR [#10](https://github.com/majd7772233-a11y/Source-Engine/pull/10) was squash-merged as `b46a6ae5fdff9b1c3328fe10b50dd1701f344c0a`. PR candidate workflow [39073005622](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39073005622) and post-merge canonical workflow [39073356292](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39073356292) both passed; canonical build uploaded APK artifact `12115514936`, SHA-256 `c78883743d21c5cf56372553096a59c3c148a5f9057dfd9a21eadfe6e3b8de69`. All 19 Stage 7 input assertions, native build, Java/Ant packaging, APK identity/ABI/dependency verification, and artifact upload passed. Real-game input, multitouch, profile, controller hot-plug, and lifecycle acceptance remain **pending physical-device verification**.
- **Stage 3 implementation and CI:** merged by PR [#6](https://github.com/majd7772233-a11y/Source-Engine/pull/6), squash commit `2bcc9e76eb73f977add87c8a94866a22002b4814`. Workflow run [38981504284](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/38981504284) passed all Stage 0–3 static checks, dependency fetch, native compilation, Ant packaging, APK identity/ARMv7 ELF/dependency validation, and artifact upload. APK: 56,534,379 bytes; SHA-256 `43712524774bed50c24a115fb2105f6699d022b5ad70ce85d7bf601e04005c7f`. Artifact ID `12080753429` expires 2026-10-30. The scanner/library implementation is merged; physical-device discovery against multiple real game/mod layouts and actual launch compatibility remain unverified.


### Stage 0 execution log — 2026-10-16
- Canonical branch retained as `android-fixes`; a curated multi-parent integration commit records `con-wrappers` and `test` ancestry without blindly taking their conflicting trees.
- Preserved the current `me.nillerusr` launcher and SDL activity namespace; set application package to `com.magd.source.engine` and updated the five identified `R` imports.
- Integrated the `test` branch NDK tierhook subtree and retained the shared `armeabi-v7a` native libraries (35 libraries were enumerated, including `libfirst.so` and `libtier0.so`). The `test` branch's alternate `in.celest` launcher and its unrelated Python/Discord bot/cache artifacts were not copied.
- Preserved the three upstream localized About-text community contact updates from `con-wrappers`.
- Removed the committed debug keystore and its copy step, added local/build artifact ignores, hardened permission callback handling against empty results, removed a developer-specific SDK path from `build.sh`, and made the native workflow path/environment driven.
- Updated CI to build on `android-fixes` and upload `bin/srceng-debug.apk` as an artifact. The repository currently reports zero Actions runs for this branch; no successful build has been verified yet.
- A post-change scan of the 10 Java files listed under the three app/SDL source namespaces found zero remaining `com.valvesoftware.source.R` imports and five imports of `com.magd.source.engine.R`.
- All four branch refs (`android-fixes`, `con-wrappers`, `test`, and the temporary validation branch) were synchronized to the same integrated commit `5daf4cb07129bbbf40119169097ca148d4bdd813`; no branch now points to a divergent code snapshot.
- **Still open:** validate every relevant file/call-site on the integrated tree; check script executable modes and dependency/toolchain compatibility; run CI or a clean build; fix any actual failures; only then close Stage 0.


### Stage 0 execution log — 2026-10-16 (CI build and final verification)
- CI initially failed because Ant used the runner's unrelated SDK path. The workflow now sets `ANDROID_HOME`/`ANDROID_SDK_ROOT` to the cloned legacy SDK and Ant receives `-Dsdk.dir="$ANDROID_HOME"`; the scripts fail early if the required `tools/ant/build.xml` is absent.
- The next reproduced failure was legacy `aapt` failing to load `libz.so.1` on Ubuntu 24.04. The workflow now installs `lib32z1`, `lib32stdc++6`, and `lib32ncurses6`.
- Root `build.sh` now generates `res/values/build_info.xml` (`last_commit`, `deploy_branch`) before invoking Ant, matching the native workflow and avoiding missing generated resource IDs.
- Successful clean CI run: [38937163563](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/38937163563), commit `5116d037a074e3c15652e465a3224576767e3b41`. All five native library build commands, Ant APK packaging, and artifact upload completed successfully. Artifact: `source-engine-android-debug`, approximately 54.5 MB.
- No physical-device installation, game-content launch, or runtime smoke test was performed. These remain explicitly unverified and belong in the appropriate later-stage acceptance checks.
- Added `scripts/verify-stage0.sh` and APK manifest/native-payload inspection to CI. Final run [38938001867](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/38938001867) passed all checks and uploaded artifact `source-engine-android-debug` (54,488,439 bytes; SHA-256 `0ed0e8919ef6209114eecd35dd15b3abbcca47588570d7dcb11a782a3f557e6b`). All four maintained branch refs were synchronized for the completion-record commit.
- Stage 0 is complete. Device installation, game-content launch, and physical-device runtime smoke tests were not performed; they must not be inferred from CI packaging success.


### Stage 9 execution log — 2026-10-18

- Work branch: `stage9-online-capability-audit`, based on `android-fixes`.
- Added Advanced settings → Online features and server-browser status, English/Arabic resources, `scripts/verify-stage9.sh`, CI wiring, and `docs/ONLINE-FEATURES.md`.
- The launcher reports Android connectivity state only and explicitly marks in-app server browsing unavailable because no verified native discovery/list/join bridge or known compatible server is established. This satisfies the roadmap's explicit-unavailability acceptance path; it is not a multiplayer test.
- Candidate CI passed in [run 39074890383](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39074890383): Stage 0–9 static checks, all native builds, Java/Ant packaging, APK identity/ABI/dependency verification (35 ARMv7 ELF libraries), and artifact upload succeeded. Candidate APK: 56,571,000 bytes; SHA-256 `9f3bdca78345b8eb8751edf614a4521cec5b9dd343ceaf640df024a502a36141`; artifact [12116990835](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39074890383/artifacts/12116990835), expires 2026-10-31. Merge evidence will be recorded after final documentation checks. Physical server discovery/join is not claimed.
