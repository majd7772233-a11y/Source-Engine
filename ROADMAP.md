# Source Engine for Android — Roadmap

> **Roadmap status (2026-10-24):** Stage 12 is the permanent QA/feedback/defect-correction lane, not the final stage. Stages 13–16 have source implementations and regression/static coverage; physical Android/provider/game/accessibility/performance acceptance is deferred to the consolidated Stage 18 window because no approved APK can be distributed yet. Canonical workflow [40125765154](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/40125765154) passed on head `d372d7b6a0e575f8557c743d3aaec0a950e673fd`, including source verifiers through Stage 16, JVM/lint/workflow validation, native build and APK verification. The provenance/redistribution gate remained blocked, so no artifact was uploaded and signing/release steps were skipped. **Current implementation stage: Stage 17 — feature truth, settings behavior and optional online/update capabilities.** Stage 19 remains the accountable source/legal review and controlled-distribution gate; **Final Acceptance** is unnumbered. Do not infer device acceptance or release readiness from static/JVM/build success.

## 1. Product goal

Turn this repository into a maintainable Android launcher and runtime integration for Source-engine games, with a dependable installation/build pipeline, a discoverable game library, reliable game-directory detection and launch, robust storage handling, configurable controls and graphics, and a premium **Liquid Glass** launcher UI. The launcher must remain usable on the intended Android range, including older devices; effects that require newer Android APIs need a graceful fallback. The game renderer/HUD is a separate scope from the launcher and must not be described as redesigned unless native/game-side code actually changes.

This repository is **not itself the complete Source SDK/game-content distribution**. The current CI script clones external repositories (notably `nillerusr/srcsdk`) and builds native components from them. Source game data such as `gameinfo.txt`, VPKs, and proprietary assets must be supplied lawfully by the user; the app must detect missing content and explain what is missing instead of implying that installing the APK installs a full game.

## 2. Current repository facts (rechecked 2026-10-22)

- Canonical branch: `android-fixes`; the audit baseline commit `8b204a102437a20f621e42381c8f38b7b10119dc` is historical and is not the current head.
- Android application ID: `com.magd.source.engine`; legacy Ant/ADT build, checked-in project target API 29, manifest minimum API 17 and target API 24. Native payload is verified by CI for ARMv7/`armeabi-v7a` (35 ELF libraries); this does not establish that a device/game combination works.
- Main user-facing path: `LauncherActivity` → `DirchActivity` / `StorageAccess` → `GameDiscovery` → `SDLActivity` / `ValveActivity2` and `TouchControlsOverlay`.
- Storage selection uses `ACTION_OPEN_DOCUMENT_TREE` from API 21, but the native bridge only accepts `com.android.externalstorage.documents` trees which map to a canonical local filesystem path, and currently requires both `canRead()` and `canWrite()`. Generic document/cloud providers are rejected; this is the direct code path that can yield the user's reported “This provider cannot be used by the native engine” message. Without the selected URI authority and runtime logs, this audit cannot determine which exact rejection condition was hit on the user's device.
- The manifest declares shared-storage and microphone permissions and `requestLegacyExternalStorage=true`. After PR #18, startup storage permission is separated from optional microphone permission; there is still no user-facing Android 11+ special “all files access” settings flow. Android permissions cannot grant root access or access to every app-private directory, and any special access must be justified, user-approved, and handled within OS/distribution constraints.
- Product code includes discovery, launcher UI, a keyboard-mapped touch overlay, visual styling, connectivity/compatibility reports, and a safe-but-disabled update path. The online status panel explicitly says that a working in-app server browser is unavailable. The update marker checker does not deliver or install app updates.
- The current host-JVM regression suite reports 80 assertions (an earlier Stage 11 run had 73); CI also runs source/resource/workflow checks and builds/verifies an APK when distribution gates allow. These gates do not exercise Android's real document picker/providers, permissions UX, touch events, native startup, in-game controls, rendering/audio or sustained performance.
- See [the product-quality audit](docs/PRODUCT-QUALITY-AUDIT.md) for evidence-based findings. Historical stage logs below are retained as history; earlier implementation/CI acceptance must not be interpreted as current product-wide or physical-device acceptance.

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
- [x] Add per-game details (readiness, explanation, detected folder, launch root, game directory, optional content, last launch), clear empty/error states, recoverable scan errors, and visible scan/launch progress. A failed/partial scan no longer commits its incomplete results over the last successful library snapshot.
- [x] Remove obsolete commented-out launcher controls and the legacy commented launch fallback. Keep every visible primary action wired; retain library remove/hide and explicit rescan behavior.
- [x] Improve cards, long-name/path truncation, 48dp minimum action targets, keyboard focusability, accessible details/status descriptions, Android 4.2 API guarding for live-region announcements, portrait-device install eligibility, and Arabic translations for launcher/library states.
- [x] **CI acceptance:** PR [#8](https://github.com/majd7772233-a11y/Source-Engine/pull/8) merged as `772a49b15163e3d354fda4d550a01e17930a1214`. Run [39070653298](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39070653298) passed Stage 0–5 static checks (16 Stage 5 assertions), locked native dependency fetch, native compilation, Java/Ant packaging, APK identity/ABI/dependency verification, and artifact upload. APK: 56,548,121 bytes; SHA-256 `22f26e01e5ffaa26718bc525bd7457126e6c320bdc1bc72e93a16e8a3741c1e9`; artifact [12115152142](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39070653298/artifacts/12115152142), expires 2026-10-31.
- [x] **Scan-failure recovery follow-up:** PR [#15](https://github.com/majd7772233-a11y/Source-Engine/pull/15) merged as `418eb63cca6da8dd21a3549b99c7a2b91132008b`; PR workflow [39473018430](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39473018430) passed 17 Stage 5 assertions and the full native/Java/Ant/APK pipeline, and canonical push workflow [39473351826](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39473351826) passed after merge. APK: 56,572,263 bytes; SHA-256 `9000c986fb4ff55249ba793614c8d5be8cda0809f8abd3f5d13a5ff82f019b91`; [artifact 12287199143](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39473351826/artifacts/12287199143), expires 2026-11-03.
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
- [x] Audit high-risk permission callbacks, directory-listing nulls, startup argument/environment construction, build-script quoting, runtime asset writes, and cleanup paths. The legacy SDL glue retains upstream-compatible catches for optional platform callbacks; app-owned critical paths now validate results and log actionable failures.
- [x] Remove forced production debug flags; explicitly export only the launcher entry point and mark the directory picker, SDL activity, and updater service non-exported. Avoid logging complete user-selected game paths.
- [x] Validate the updater's pinned HTTPS host, branch identifier, bounded response and finite timeouts. Automatic APK update notifications are disabled because there is no signed release manifest/checksum/install-verification flow; no downloaded APK is executed.
- [x] Validate startup path, argument, custom-VPK and environment sizes; fail when optional data-directory creation or required native environment setup fails. Runtime VPK extraction now writes and syncs a temporary file, atomically replaces the destination, validates cached byte counts, and cleans incomplete output. Failures explain that storage availability should be checked.
- [x] **CI acceptance:** PR [#13](https://github.com/majd7772233-a11y/Source-Engine/pull/13) merged as `c6c21d2e21de9829214214d40fd67ceba1e6e9f2`. Workflow [39077037357](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39077037357) passed Stage 0–10 checks (26 Stage 10 assertions), pinned dependency fetch, native compilation, Java/Ant packaging, APK identity/ABI/dependency verification (35 ARMv7 ELF libraries), and artifact upload. APK: 56,572,260 bytes; SHA-256 `71745581d79f0f195f890b764a70d4d99aacdc9460068dbaebece1ffd750d3fa`; artifact [12116564321](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39077037357/artifacts/12116564321), expires 2026-10-31. Physical-device storage, extraction-failure, permission, and actual game-launch validation remain pending.

### Stage 11 — Automated tests, static checks, and CI quality gates
- [x] Add executable host-JVM tests for path normalization, native argument construction, game-directory validation, additive preference migration, bounded filesystem discovery, title parsing, readiness states (missing metadata/assets/native libraries/unsupported ABI), launch-history persistence, and defensive library preference snapshots. Tests compile the actual production Java classes with minimal Android API test doubles and use temporary on-disk game fixtures; no Android SDK or proprietary game content is required for the JVM suite.
- [x] Add manifest/resource/package/dependency verification, shell syntax checks and ShellCheck (error severity), pinned-action checks, GitHub Actions structural validation and actionlint. Keep the existing native ABI/ELF/dependency and APK archive/identity/checksum verifier as the packaging gate.
- [x] Run static checks and JVM tests on pull requests and pushes to android-fixes before fetching/building native dependencies; build and upload APK/report artifacts only after all required checks pass. Preserve Actions logs and test output.
- [x] Add docs/DEVICE-SMOKE-TEST.md covering installation/upgrade, permission denial, storage selection and removable media, game scan/readiness, settings migration, launcher UI, launch/lifecycle, touch/controller/audio, performance and diagnostics.
- [x] **CI acceptance:** PR [#16](https://github.com/majd7772233-a11y/Source-Engine/pull/16) merged as `dec2b5f6b01c83cdfc58f8897578685680670b96`. PR workflow [39552539276](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39552539276) passed 73 JVM assertions, all Stage 0–12 verifiers, YAML/workflow validation, ShellCheck, actionlint, pinned dependency fetch, native compilation, Java/Ant packaging, APK verification and artifact upload. Canonical push workflow [39552785558](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39552785558) also passed. Canonical APK: 56,573,086 bytes; SHA-256 `f25673a0132c7a4b6b0e73ed0a48d27e32f914431521817cfd31a37a94271083`; artifact [12323743499](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39552785558/artifacts/12323743499), expires 2026-11-04.
- [ ] Device acceptance remains separate: run the physical-device checklist with a lawful compatible game on at least two representative devices where available. CI/JVM tests do not establish real Android SAF/provider behavior, successful game launch, gameplay input, sound routing, rendering performance, thermal stability or crash-free sessions.

### Status reconciliation for Stages 0–11 (implementation evidence vs. product acceptance)

The numbered sequence below remains as historical implementation milestones; this table prevents “code merged / CI green” from being confused with “everything accepted on a device.” Completed implementation items remain completed history. Open device/runtime/product gaps are reassigned to the relevant Stage 13–19 milestone rather than being hidden by the old stage number.

| Historical stage | Implementation / CI status | Acceptance still open and current destination |
|---|---|---|
| 0 — Branch integration and project normalization | Canonical branch and build/package checks were established and CI passed. | No end-to-end physical-device game session is implied; broader runtime checks are Stage 18. |
| 1 — Reproducible build and native integrity | Dependency pins, build diagnostics and APK/ABI verification have CI evidence. | Native build success does not prove a game launches; runtime matrix is Stage 18. |
| 2 — Startup and lifecycle | Source fixes and CI checks are recorded. | Launch, pause/resume, cold/warm restart and failure recovery on-device remain Stage 14/18. |
| 3 — Game discovery and content validation | Discovery/library implementation and source checks are recorded. | Real game/mod layout coverage and confirmed compatibility remain Stage 14/18. |
| 4 — Storage and SAF | SAF, persisted-grant and path checks were implemented and CI-tested. | The reported provider failure and real OS/provider/removable-volume behavior remain the focus of Stage 13. |
| 5 — Core launcher UX | Library-first flows and user-facing states have implementation evidence. | Real-screen navigation and accessibility review are Stage 16. |
| 6 — Liquid Glass visual system | Visual theme and motion implementation exist. | Actual blur/depth/readability, fallback and performance acceptance are Stage 16/18. |
| 7 — Touch controls and input | Touch overlay, settings and static/regression checks exist. | Real-game multitouch, key bindings, controller and lifecycle tests are Stage 15. |
| 8 — Graphics/audio/performance diagnostics | Diagnostic implementation and guarded API paths exist. | Real rendering/audio, frame pacing, heat and power measurements are Stage 18. |
| 9 — Network/optional online features | The UI discloses that server browsing is unavailable rather than faking it. | Any real server browser or updater work is Stage 17; no absent capability is marked complete. |
| 10 — Reliability/security | Defensive source changes and CI checks are recorded. | User-observed settings/feature behavior and platform validation continue in Stages 13, 17–19 as applicable. |
| 11 — Automated tests and CI | Static/workflow checks and the current 80-assertion JVM suite have CI evidence. | Automated checks cannot replace the device matrix and gameplay acceptance in Stage 18. |

### Stage 12 — Continuous QA, user feedback, defect intake and corrective changes (ONGOING LANE)

**Purpose:** Stage 12 is a permanent lane across the whole project lifecycle. It is not the final stage, is not a numbered implementation milestone that must be “completed”, and is not the release gate. Findings from user APK checks, device smoke tests, CI failures, code reviews and new requirements are recorded and triaged here.

**Operating cycle**
1. Record each finding with expected vs. actual behavior, reproduction steps, device/Android version where relevant, safe logs/screenshots, severity, and whether it is confirmed or still a hypothesis.
2. Fix small, self-contained regressions as Stage 12.x corrective patches when that is clearest. Promote broader features or cross-cutting work into a numbered milestone (Stage 13 or later) and link the original finding to it.
3. After several numbered milestones, provide a verified candidate only when the artifact gate permits it; the user tests the app and returns the outcome. New findings re-enter this lane and are prioritized before proceeding where they block core usage.
4. Preserve prior reports as history. Never mark an unrun device test as passed or use a green build as a substitute for product acceptance.

**Current intake / hand-off (2026-10-22):**
- The earlier interpretation of Stage 12 as the “final stage” was a roadmap-model mistake and is superseded. Current numbering: Stage 12 = continuous QA/feedback; Stage 13+ = implementation milestones; **Final Acceptance** = unnumbered terminal gate.
- The reported Android storage-provider error remains a high-priority, user-observed failure. PR #18 added specific SAF errors and path-scope checks, but it has not been proven fixed on the affected device. It is scheduled under Stage 13.
- Arabic resource parity (151 default and Arabic keys with placeholder parity) is present in source and has passed CI. On-device RTL/visual/accessibility review belongs to Stage 16.
- The license/provenance workflow remains fail-closed. Workflow [39832326942](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39832326942) passed build verification but reported RELEASE BLOCKED, skipped artifact upload and skipped signed-release work. Do not bypass this protection to produce a downloadable APK.
- The existing scripts/verify-stage12.sh checks the release-clearance gate and its fail-closed behavior. The script name is historical; it does not mean Stage 12 is the final stage.

### Stage 13 — Storage-provider, folder-scope and permission reliability (SOURCE IMPLEMENTATION COMPLETE; DEVICE ACCEPTANCE DEFERRED TO STAGE 18)

**Goal:** Make the reported storage/provider failure reproducible, diagnosable and recoverable without widening access beyond the user-granted folder.

- [x] Re-audit merged StorageAccess, DirchActivity and launcher launch-path code. The provider allowlist and canonical containment are preserved; explicit persisted write-grant verification and stale-state cleanup were missing.
- [x] Add privacy-conscious diagnostics for provider authority/type, returned and persisted permission flags, volume state, directory read/write checks and resolver/selection reason. `SRCAPK_STORAGE` intentionally omits full URIs, document IDs, absolute paths and file contents.
- [x] Trace writable-storage requirements against the pinned Source SDK tree (`nillerusr/srcsdk@413f5848d9a4534cdf8a4d10f5c1e7b1eaf3835d`). The SDK source contains filesystem writes, including achievement state via [`game/shared/achievementmgr.cpp`](https://github.com/nillerusr/srcsdk/blob/413f5848d9a4534cdf8a4d10f5c1e7b1eaf3835d/game/shared/achievementmgr.cpp) and directory creation for replay output via [`game/client/replay/replayrenderer.cpp`](https://github.com/nillerusr/srcsdk/blob/413f5848d9a4534cdf8a4d10f5c1e7b1eaf3835d/game/client/replay/replayrenderer.cpp). Those features are build/game dependent, but this is enough evidence not to remove the current writable-root requirement without a tested alternative write-path design.
- [x] Harden tree document IDs against both volume escape and in-volume sibling traversal (for example, `Games/../Sibling`). Retain canonical separator-aware volume containment and reject selecting the game folder itself as the grant root.
- [x] Require a `content://` URI scheme before interpreting an external-storage document-tree URI as a local path; non-content schemes now fail closed and report an unknown volume in diagnostics.
- [x] Add JVM regression coverage for missing read/write grants, provider rejection, persisted-grant failure/revocation, unmounted/read-only storage, stale state, game-folder selection, traversal, Unicode/space paths and incomplete stored URI/path pairs. Expanded negative-path coverage so a failed replacement (missing write grant, game-folder root or unsupported provider) cannot overwrite/revoke the active selection, and clearing selection prunes only this app's external-storage tree grants while preserving unrelated provider grants. Stage 13 static assertions run in canonical CI.
- [x] Close a launch-time fail-open gap: the launcher now validates saved storage state if either the persisted URI or mapped path exists, so a partial/corrupt pair cannot bypass SAF grant and tree-scope checks. Reselecting the currently saved URI also avoids needlessly re-taking or revoking an existing grant on a failed repeat selection.
- [x] Keep launch-time error messages aligned with picker errors for unsupported providers, malformed document IDs, and unsupported Android versions rather than collapsing these stored-selection failures into an unrelated generic message.
- [x] Keep API-19+ persisted-URI permission types and grant enumeration inside the API-19 compatibility helper; the outer class stays free of direct UriPermission references for the manifest minimum API 17. Added a Stage 13 static regression assertion.
- [x] Harden selection crash consistency: synchronously commit the URI/path pair before releasing superseded grants; on commit failure restore the previous pair and retain grants until a later durable replacement; commit selection removal before grant cleanup so a failed clear cannot strand a restored saved URI without access. Added host-JVM fault-injection tests for both commit-failure paths.
- [x] Prevent persisted URI-grant accumulation when switching folders: release superseded external-storage tree grants only after a new selection has been validated and stored, retain all grants on a failed replacement, and prune orphaned storage-tree grants after stale selection cleanup. File/document grants from other providers are untouched. Added JVM coverage and static assertions.
- [x] Document the practical provider/API matrix: constrained local-path browser for API 17–20; SAF local external-storage mapping from API 21; no promise for generic cloud/virtual providers or arbitrary app-private paths. Android 11+ all-files access remains intentionally unrequested pending policy, privacy and target-SDK review.
- [ ] Physical acceptance: reproduce or explicitly mark blocked the original error on the affected device/provider; test internal storage and removable SD/USB where available. Record logs and exact APK checksum if an approved candidate exists.
- [x] Canonical CI verification for the crash-consistency follow-up passed in [run 40125463160](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/40125463160) on commit `c5c556a6b4072690885d2dffe25de547af07da54`: all Stage 0–16 source verifiers passed; JVM regression suite passed 80 assertions; Stage 13 storage suite passed 101 assertions; Stage 14 discovery tests passed 32 and Stage 15 touch-input tests passed 18; workflow/YAML validation passed. Native/Ant build succeeded and APK verification passed for `com.magd.source.engine`, `armeabi-v7a`, 35 ELF libraries, 56,598,623 bytes, SHA-256 `ff64726e5ffbd3008911d12be3c6070711bfb204aaf4a69225125d5d029dc7e4`. This was a CI verification build only: the release license/provenance gate returned `RELEASE BLOCKED`, and artifact upload/signing/release steps were skipped. No downloadable APK was produced by the run. This does not close physical acceptance or Stage 13.

**Acceptance:** each tested selection either resolves inside the granted tree or shows a precise, actionable reason; no parent/sibling scope expansion; regression suite passes; physical outcomes are recorded honestly. The original provider failure is not resolved until retested on that device or its exact cause is demonstrated and verified.

### Stage 14 — Game discovery, readiness truth and launch-state correctness (SOURCE IMPLEMENTATION COMPLETE; RUNTIME ACCEPTANCE DEFERRED TO STAGE 18)

- [x] Audit the existing GameDiscovery.ScanResult / scanDetailed implementation from PR #18 without duplicating its depth, directory-limit or inaccessible-child guards.
- [x] Classify invalid input, unavailable/missing roots, non-directory roots, unreadable roots and access-denied roots; expose cooperative cancellation and distinguish complete zero-candidate scans.
- [x] Preserve both saved paths and the current rendered library after incomplete, cancelled or worker-failed scans. Every configured root must complete before the library snapshot can be replaced.
- [x] Add an explicit Cancel scan action, reset progress/control state after completion, and report unavailable roots separately from inaccessible folders, depth limits and directory caps.
- [x] Keep readiness explicitly at the “Ready to try” preflight level and history labelled as “Last attempt”; no successful native session is claimed without a runtime signal.
- [x] Add JVM tests for empty-but-complete roots, unavailable roots, invalid input, cancellation, depth limits, worker failure and mixed multi-root results; wire a dedicated Stage 14 verifier into CI.
- [ ] Runtime/device acceptance: verify discovery and launch behavior against lawful, complete game/mod layouts on Android, including hide/remove/restore, process restart, failed startup and the first stable native session. Record device/API, sanitized log and approved APK checksum; CI cannot substitute for this.

**Acceptance:** source/JVM checks pass; incomplete scans are never shown as definitive emptiness; missing storage, cancellation and complete-zero-candidate outcomes remain distinguishable; “ready” and “last attempt” do not overstate runtime success. Device acceptance remains external.

### Stage 15 — In-game input and gameplay controls (SOURCE IMPLEMENTATION COMPLETE; DEVICE ACCEPTANCE DEFERRED)

- [x] Extract keyboard-key ownership/reference counting into a platform-independent state helper so overlapping touch/button/joystick sources emit one key-down and one final key-up; host-JVM regression tests cover shared-key presses, pointer reuse, and release/cancel cleanup.
- [x] Harden interruption paths: opening the touch-settings menu, losing window focus, pause/stop, configuration/viewport resize, detached overlay and ACTION_CANCEL release held overlay keys/mouse buttons. A secondary pointer on empty space no longer rejects an active multitouch stream, and a second edit pointer cannot hijack the first pointer's drag.
- [x] Keep per-game settings for preset, control positions, size, opacity, sensitivity and visibility; preserve fixed mapping disclosure in English/Arabic and the reset/recovery route. The movement pad remains keyboard-mapped WASD, not analogue.
- [x] Add Stage 15 static verification and wire it into canonical CI alongside the new pure-Java regression suite.
- [Deferred to Stage 18 / consolidated pre-final test window by user request] Physical-device/game acceptance: simultaneous move/fire/action presses; pointer up/cancel; menu interruption; hide/show/edit/reset; resize/orientation; per-game profile persistence after process restart; physical keyboard/mouse; supported controller hot-plug and axis/button behavior. Record exact device, Android version, game/mod, APK SHA-256 and logs.
- [Deferred to Stage 18 / consolidated pre-final test window by user request] Verify supported hardware input in an actual compatible game. SDL keyboard/mouse/controller integration is preserved in source, but source/static/JVM checks do not prove device events reach every game/mod as expected.

**Implementation acceptance:** source/static checks and JVM regressions pass in canonical CI (latest checked run: [39847170781](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39847170781)). Stage 15 source implementation is complete. Device/game acceptance is intentionally outstanding and moved to Stage 18; do not generalize compatibility before that evidence exists.

### Stage 16 — Launcher experience, Liquid Glass, localization and accessibility (SOURCE IMPLEMENTATION COMPLETE; CANONICAL CI PASS; DEVICE ACCEPTANCE DEFERRED TO STAGE 18)

- [x] Audit the source-level first-run, picker, empty-library, scan progress/failure, game-card/details, advanced-settings, launch-error, About and dialog paths. Keep real-device visual/TalkBack review in Stage 18.
- [x] Keep the CI verifier enforcing complete default/Arabic string-key coverage and format-placeholder parity; preserve the corrected Arabic first-game call to action and scan-result wording.
- [x] Enable Android RTL layout mirroring and localize the directory-picker activity label.
- [x] Fix compatibility-diagnostics formatting so report separators render as actual line breaks.
- [x] Keep workflow meta-validation requiring all Stage 0–16 verifier scripts.
- [x] Add meaningful localized TalkBack summaries to game cards, semantic heading markers guarded for API 28+, live scan-state announcements, advanced-panel open/close announcements and focus return/transfer, accessible labels for dynamic scan actions, and Back handling that collapses advanced settings before leaving the screen.
- [x] Enforce at least 48dp touch width/height for styled actions, preserve sp text sizing and selectable long details, and improve About-dialog readability without clamping user font scale.
- [x] Strengthen the Liquid Glass surface with layered translucent base/sheen, soft linear and radial specular reflections, rounded edge treatment, existing colored ambient lighting, pressed/focused/disabled button states, and a persisted motion-off path. No mandatory RenderEffect/blur API is used, so older Android versions retain the layered fallback.
- [x] Verify the source implementation with scripts/verify-stage16.sh, including localized accessibility strings, heading API guard, announcements, touch-target constraints and layered surfaces.

**Acceptance:** the Stage 16 verifier, JVM/lint/workflow checks, native build and APK verification passed in canonical run [40125765154](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/40125765154) on commit `d372d7b6a0e575f8557c743d3aaec0a950e673fd`. The release-provenance gate intentionally skipped artifact upload and signing. Physical screenshots, TalkBack/focus checks, RTL/font-scale layout, rendering quality and performance/thermal measurements remain consolidated in Stage 18; no device acceptance is claimed.

### Stage 17 — Feature truth, settings behavior and optional online/update capabilities (SOURCE WORK IMPLEMENTED; CI VERIFICATION PENDING)

- [x] Trace main visible launcher/library/advanced-settings controls and settings persistence/forwarding paths in [the feature-to-effect inventory](docs/FEATURE-TRUTH-INVENTORY.md). Real-device and unverified game effects remain distinct.
- [x] Keep server discovery/list/filter/join unavailable until a real native bridge and compatible-server end-to-end behavior exist. The UI describes connectivity as Android network state only.
- [x] Remove the disconnected version-marker checker, unused “newer marker” string, unreferenced updater service and orphaned checkbox fields instead of implying in-app update detection or installation.
- [x] Extract environment parsing/application into a pure-Java helper. It validates the full list before calling native `setenv`, rejects duplicate names and launcher/Android/dynamic-linker reserved variables, keeps existing bounds, and provides actionable localized errors. Host-JVM tests cover invalid-after-valid input causing zero mutations and stopping after a native setter failure.
- [x] Review permissions/playback capture: startup storage permission remains separate from optional microphone access; the intentional playback-capture opt-in is documented as behavior for eligible external user-approved recorders, not an in-app recording feature.
- [x] Add Stage 17 static verification and wire it into canonical CI alongside the JVM parser tests.

**Acceptance:** the source inventory and dedicated verifier exist; unavailable online/update capabilities remain honest; invalid configuration cannot partially apply a validated prefix; native setter failure is surfaced. Canonical CI for this implementation commit must pass before Stage 17 source work is considered verified. Physical provider/game/audio/accessibility/performance checks remain deferred to Stage 18.

### Stage 18 — Compatibility, runtime stability, performance and consolidated device acceptance (AFTER ALL IMPLEMENTATION STAGES)

- [ ] Maintain a concrete API/ABI/GPU/device matrix beginning at manifest minimum API 17 and the verified ARMv7/armeabi-v7a package. Do not imply support for untested combinations.
- [ ] After Stages 13–17 source implementation and Stage 19 rights/build work are ready for a testable candidate, perform the user's requested consolidated physical-device review using docs/DEVICE-SMOKE-TEST.md: install/upgrade, process restart, permissions, SAF, discovery, game startup, pause/resume, input, audio, rendering, accessibility, exit and crash recovery. Do not ask the user to test earlier stages individually.
- [ ] Record repeatable startup times, frame pacing, memory, heat/throttling and power observations on representative devices; optimize only evidence-based bottlenecks.
- [ ] Convert confirmed defects from stages 13–18 into regression tests where feasible. Keep JVM/static checks, APK identity/ABI/dependency verification, YAML/action validation, ShellCheck/actionlint and pinned dependencies in CI.
- [ ] Aim for two representative devices where available. If device/game access is limited, record the limitation as Blocked rather than inventing a pass. This is the planned end-of-implementation test window immediately before the unnumbered Final Acceptance gate.

**Acceptance:** device records and logs accompany runtime compatibility claims; CI covers automatable regressions; untested combinations remain explicitly unverified.

### Stage 19 — Source provenance, privacy/legal review and controlled build distribution

- [ ] Complete docs/THIRD-PARTY-LICENSE-AUDIT.md with accountable, evidence-based decisions for project code, external source revisions, Java/native glue and every redistributed prebuilt binary. Preserve required notices and confirm redistribution rights; do not conclude anything is unlawful without evidence.
- [ ] Decide and document the intended app license and attribution/notice delivery. Ensure audio-capture, microphone and storage choices match privacy disclosures.
- [ ] Maintain the Debug test-artifact path: upload the verified APK for hands-on app testing, keep it distinct from a production release, and record device installation/smoke-test results.
- [ ] After clearance is granted and evidenced, run the secret-backed signed-release workflow. Verify package/version, certificate, signature, checksum, debuggability, install/upgrade behavior and artifact labels. Never commit signing secrets or keys.
- [ ] Make any APK intended for user testing available only through the approved gate; if the gate remains blocked, record user-device testing as blocked and continue only safe source/test work.

**Acceptance:** accountable provenance/redistribution review and notices are recorded; the gate accepts only the reviewed commit; signed package identity/signature/upgrade are verified; no unreviewed artifact is published.

## Final Acceptance — UNNUMBERED PROJECT END GATE

This is the actual final gate and deliberately has no stage number. It begins only when the numbered milestones have reached acceptance and the user has had a meaningful opportunity to inspect a testable candidate.

- [ ] Stages 13–19 are completed or explicitly deferred non-critical items have documented rationale and user agreement.
- [ ] Critical/high findings are fixed, verified or explicitly accepted by the user with known limitations.
- [ ] The user tested a candidate after several milestones; any failures re-entered Stage 12 and were corrected/retested.
- [ ] Required CI passes on the exact commit; artifact checksum, package/ABI and validation report match.
- [ ] Physical-device acceptance covers storage/provider access, discovery, lawful game launch, input, lifecycle, sound, accessibility and observed performance for the supported matrix.
- [ ] Rights/provenance review, notices, privacy decisions, signed-package verification and install/upgrade tests are recorded.
- [ ] Release notes describe only observed capabilities. No critical blocker, false compatibility claim, unapproved artifact or missing required notice remains.

**Do not mark Final Acceptance complete until user-visible evidence and required reviews actually exist. A CI-only pass is not enough.**

### Stage 14 implementation log — 2026-10-24

- Re-audited the canonical head 5030582b76048bed33844ef9223fbf558cae5bed. The scanner already surfaced traversal limits and some child access failures; root failure causes and user cancellation were not structured.
- Fixed a snapshot-consistency bug: an incomplete scan called renderGameLibrary(), which could prune currently inaccessible entries from persisted paths while saying the prior snapshot was preserved. Incomplete, cancelled and failed scans now keep both the stored snapshot and existing screen intact.
- Added explicit root status, cooperative cancellation, separate counts for invalidated roots, and regression coverage for complete-empty vs incomplete results. Stage 14 static checks and JVM suite are mandatory in canonical CI.
- Device/native-runtime acceptance is still pending. This work does not bypass the independent source-provenance and redistribution artifact gate.

### Stage 15 implementation log — 2026-10-24

- Source changes landed in commit [5a2ac95](https://github.com/majd7772233-a11y/Source-Engine/commit/5a2ac95d47ae966b5d4b6d5645e7a0a1975d0574). The overlay’s key ownership was not isolated for JVM testing; TouchInputState now reference-counts simultaneous button/joystick owners and emits key-up only after the final owner releases. Tests cover two pointers sharing a key, overlapping joystick/button ownership, pointer-ID reuse and idempotent release-all cleanup.
- Touch input is released before the modal settings dialog and on focus loss, pause/stop, configuration/viewport changes, detachment, and cancellation. Edit-mode drags are single-pointer-owned. The Stage 15 static verifier and expanded physical-device steps are wired into canonical CI/documentation.
- The fixed mapping remains WASD, Space, E, R and mouse button 1. This is not an analogue stick and does not promise universal mod compatibility.
- Physical-game tests remain unchecked: simultaneous controls, real controller/keyboard/mouse behavior, focus/lifecycle interruptions and profile persistence must be recorded against a specific device, game and APK hash. The license/provenance gate is not bypassed to obtain a downloadable APK.

## 5. Active product defect register

The implementation-stage checklists above record history and CI evidence. Active defects enter the permanent **Stage 12 feedback lane** and are assigned to numbered implementation milestones; older checks are not product-wide acceptance. Full rationale and source evidence: [`docs/PRODUCT-QUALITY-AUDIT.md`](docs/PRODUCT-QUALITY-AUDIT.md).

| ID | Priority | Area | Audit finding | Target milestone | Current state (implementation and acceptance kept separate) |
|---|---|---|---|---|---|
| PQ-01 | Critical | Storage | User-observed SAF provider rejection; strict provider/path assumptions, read/write mismatch and generic diagnostics | 13 | Distinct selection error codes/messages and root-scope checks added; must test with the provider/device that failed |
| PQ-02 | High | Permissions | No OS-aware all-files-access route; storage and microphone previously requested together | 13, 17, 19 | Microphone removed from startup permission bundle; all-files-access strategy remains an explicit compatibility/policy decision |
| PQ-03 | High | Scope | Game-folder selection could derive a parent path wider than the tree grant | 13 | Selecting a game folder itself is rejected; canonical granted-root containment is checked on return and launch |
| PQ-04 | High | Discovery | Scan limits and inaccessible child listings could look like definitive empty results | 14 | Detailed scan result tracks inaccessible paths and limits; incomplete scans preserve prior snapshot and show an explanation |
| PQ-05 | High | Readiness | “Ready to try” is a filesystem/ABI heuristic, not proof the game runs | 14, 18 | Remains a preflight heuristic; explicit runtime compatibility evidence still missing |
| PQ-06 | High | Launch history | Last-launch timestamp was recorded before SDL/native launch success | 14 | UI says “Last attempt”; an actual native-ready success signal remains unimplemented/unverified |
| PQ-07 | High | Localization | Initial audit found 34 default strings without Arabic translations | 16 | Branch has 151 keys in each locale; key/placeholder parity passed in canonical workflow 39832326942; on-device RTL review remains open |
| PQ-08 | High | Input | Touch overlay simulates a fixed keyboard/mouse mapping, not analogue or universal mod controls; touch state must be balanced across interruptions | 15 | Key ownership and menu/focus/resize recovery have JVM/static coverage; real-game input and device acceptance remain open |
| PQ-09 | High | Visual UX | Picker/dialogs were visually inconsistent with the stated Liquid Glass goal | 16 | Shared visual styling applied to the picker; screenshots, accessibility and device performance remain unverified |
| PQ-10 | Medium | Feature truth | Update checker only logs a version marker; legacy update service is disabled | 17 | “Newer marker” text is now explicit that in-app update is unavailable; a verified updater is still absent |
| PQ-11 | Medium | Online | In-app server browser/join flow is not implemented | 17 | Remains explicitly unavailable; do not count connectivity as server discovery |
| PQ-12 | High | Input validation | Invalid environment entries could be skipped while other entries applied | 17 | Entire list is validated before setenv and invalid/reserved entries return actionable localized errors; canonical source/CI checks passed; runtime behavior remains unverified on-device |
| PQ-13 | High | Test coverage | Static checks/JVM assertions do not exercise Android providers, gameplay, audio or performance | 18 | Added regression assertions; physical-device and gameplay coverage remains open |
| PQ-14 | High | Privacy/legal/release | Audio capture/permission decisions, license review and signed release validation remain open | 17, 19 | Startup permission coupling improved; privacy/legal/signing acceptance remains open |

### Historical audit and correction of the Stage 12 model (2026-10-21 to 2026-10-22)

- Audit baseline: canonical branch `android-fixes`, commit `8b204a102437a20f621e42381c8f38b7b10119dc`.
- This is a **review and roadmap/documentation re-scope**, not a product-fix commit. The app source has not been changed as part of the initial audit; the user's observed storage failure has not yet been reproduced on a second device.
- The known storage failure, 34 missing Arabic resources, silent scan truncation/error paths, heuristic readiness state, premature “last launched” recording, fixed touch bindings, visual target gap and feature/release gaps are recorded in `docs/PRODUCT-QUALITY-AUDIT.md`.
- The original audit preceded fixes in PRs #18 and #19. The former plan incorrectly assigned all remaining work to terminal Stage 12.x. This is superseded: Stage 12 is the permanent feedback lane, PQ findings are mapped to Stages 13–19, and Final Acceptance is unnumbered.


## 6. Completion ledger

Update this ledger at the end of every numbered implementation milestone with commit SHA, changed files, checks actually run, open failures and the next milestone. Stage 12 is a persistent intake lane rather than a stage to close. Final Acceptance is unnumbered and must never be marked complete without user/device evidence and required rights/release records.

- **Stage 0:** **Complete** — final verification candidate `bf7f506f741cac82c8f1419b4170e4ede3883cbe` passed run `38938001867`: source/manifest/shell checks, all five native builds, Ant APK packaging, application-ID inspection, required ARMv7 native-library checks, and artifact upload. The completion-record commit is synchronized across maintained branch refs. No device installation/launch is claimed. Integration commit: `95da41713d9df94f42a11cdd7d0e927fc9ee2c40`.
- **Stage 1:** **Complete** — canonical Stage 1 commit `96092dbfc7f030d643125505dbd51c36e2cb3145`; CI run [38973176340](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/38973176340) passed on the canonical branch. Immutable dependency locks, hardened build scripts, APK verification, and success-gated artifact upload are in place.
- **Stage 2 implementation:** merged by PR [#5](https://github.com/majd7772233-a11y/Source-Engine/pull/5), merge commit `98dfe6f894bc17a685d766379ab9e6158e788d7a`. CI run [38976380349](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/38976380349) passed source/shell checks, all Stage 2 static assertions, native compilation, APK packaging, APK validation, and artifact upload. Files changed: `LauncherActivity.java`, `ValveActivity2.java`, `ExtractAssets.java`, `SDLActivity.java`, `res/values/strings.xml`, `scripts/verify-stage2.sh`, `.github/workflows/build.yml`, `README.md`, `ROADMAP.md`, and `GPT.md`.
- **Stage 2 runtime acceptance:** **Pending device verification**, not falsely marked complete. No physical Android device or complete lawful game installation was available to this CI run; actual gameplay launch, pause/resume, and repeated-session behavior must still be exercised on-device. The code and build pipeline are merged; keep this explicit runtime limitation visible.
- **Stage 7 implementation and CI:** PR [#10](https://github.com/majd7772233-a11y/Source-Engine/pull/10) was squash-merged as `b46a6ae5fdff9b1c3328fe10b50dd1701f344c0a`. PR candidate workflow [39073005622](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39073005622) and post-merge canonical workflow [39073356292](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39073356292) both passed; canonical build uploaded APK artifact `12115514936`, SHA-256 `c78883743d21c5cf56372553096a59c3c148a5f9057dfd9a21eadfe6e3b8de69`. All 19 Stage 7 input assertions, native build, Java/Ant packaging, APK identity/ABI/dependency verification, and artifact upload passed. Real-game input, multitouch, profile, controller hot-plug, and lifecycle acceptance remain **pending physical-device verification**.
- **Stage 3 implementation and CI:** merged by PR [#6](https://github.com/majd7772233-a11y/Source-Engine/pull/6), squash commit `2bcc9e76eb73f977add87c8a94866a22002b4814`. Workflow run [38981504284](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/38981504284) passed all Stage 0–3 static checks, dependency fetch, native compilation, Ant packaging, APK identity/ARMv7 ELF/dependency validation, and artifact upload. APK: 56,534,379 bytes; SHA-256 `43712524774bed50c24a115fb2105f6699d022b5ad70ce85d7bf601e04005c7f`. Artifact ID `12080753429` expires 2026-10-30. The scanner/library implementation is merged; physical-device discovery against multiple real game/mod layouts and actual launch compatibility remain unverified.


- **Stage 15 implementation (in progress):** source changes in commit [`5a2ac95`](https://github.com/majd7772233-a11y/Source-Engine/commit/5a2ac95d47ae966b5d4b6d5645e7a0a1975d0574) add testable key ownership and touch-interruption cleanup. Commit [`979f9fc`](https://github.com/majd7772233-a11y/Source-Engine/commit/979f9fc076520342926bd8a9ea77171933f7d3e1) adds the Stage 15 verifier and physical-device cases. The extra idempotent joystick transition regression was merged through [PR #22](https://github.com/majd7772233-a11y/Source-Engine/pull/22) as [`fbb1114`](https://github.com/majd7772233-a11y/Source-Engine/commit/fbb1114594f5734a129a1c6996288caed05aba56). A source-contract review passed 25/25 checks and the isolated JVM input-state suite passed 18 assertions; a fresh GitHub Actions run for these Oct 24 commits is not visible, so full canonical CI is still unverified. Physical-game acceptance remains pending; do not close the stage.

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
- PR [#12](https://github.com/majd7772233-a11y/Source-Engine/pull/12) merged into `android-fixes` as `b9660ab08752d83c4ecd5f387298636d3d214e05`. Final pre-merge candidate workflow [39075085755](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39075085755) passed Stage 0–9 static checks, all native builds, Java/Ant packaging, APK identity/ABI/dependency verification (35 ARMv7 ELF libraries), and artifact upload. APK: 56,571,000 bytes; SHA-256 `56cceb193f2b562bf1f5bb32c9c68da8e85f25b72ffa8d8647f81103af5a516f`; artifact [12116274496](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39075085755/artifacts/12116274496), expires 2026-10-31. No real server discovery/join or multiplayer session is claimed. The Actions connector only exposes PR-triggered workflow runs in its commit query, so a distinct post-merge push run was not independently inspected.


## Stage 10 engineering ledger — 2026-10-18

- Working branch: `stage10-reliability-security`, based on `android-fixes`.
- Hardened permission request APIs against pre-23 calls and null/empty input; permission-denial recovery resets launcher button/progress state. SDL startup fails safely on empty permission results.
- Marked launcher as the only externally exported activity. The directory picker, native SDL activity, and legacy updater service are explicitly non-exported; no forced `android:debuggable=true` is present.
- Replaced the unsafe `chmod` shell command and broad world-writable permissions with atomic internal asset extraction. A temporary VPK is synced and validated before replacement; cached file length is compared with persisted metadata, and incomplete temporary output is cleaned up.
- Bounded startup arguments, game path, custom VPK list and environment overrides; checked optional data-directory creation and native environment configuration; removed complete user game paths from startup validation logs.
- Pinned the advisory version check to the expected HTTPS raw GitHub host, constrained branch names, added connection/read timeouts and response-size/commit-SHA validation, and disabled the legacy notification route because no signed release verification/install mechanism exists.
- Added `scripts/verify-stage10.sh` to CI, covering manifest exposure, permission callbacks, path/input limits, atomic extraction, updater restrictions, directory-listing guards and shell syntax. Workflow [39077037357](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39077037357) passed the full build and artifact upload; PR [#13](https://github.com/majd7772233-a11y/Source-Engine/pull/13) merged as `c6c21d2e21de9829214214d40fd67ceba1e6e9f2`. Device/runtime acceptance remains pending.

- Canonical post-merge acceptance: [workflow 39680488677](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39680488677) passed on commit `f530574869f51091b5dccf0365d12f582c737ce2`. Debug APK: 56,589,387 bytes; SHA-256 `d8d2721257bd129a4cd43aed2f740c8e5dd76bad1b350b30b54b9f750de826ef`; [artifact 12383377856](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39680488677/artifacts/12383377856), ZIP digest `ba388bc3b1c9fe70ebe983c9259f4758f49bd761e87e636c6530a85375faab5d`, expires 2026-11-04. All 80 JVM assertions, Stage 0–12 verifiers, native builds, Ant packaging and APK integrity checks passed; release signing was skipped.

### 2026-10-23 — Stage 13 grant-lifecycle hardening

- Re-review found that choosing multiple different valid folders replaced the saved URI/path pair but left obsolete persisted URI grants active. Partial/corrupt saved state could also leave a grant orphaned from the preference record.
- The replacement path stores the new validated selection before pruning obsolete local external-storage tree grants. On invalid/stale selection cleanup it prunes the same scoped tree-grant category; it deliberately leaves grants from other providers and file/document URIs untouched.
- Added JVM regression checks for failed replacement preserving the old and orphaned grants, and successful replacement retaining only the active tree grant. CI includes source-order and provider/path-scope assertions.
- Physical picker/provider acceptance is still blocked pending an approved candidate APK and the user's device/provider evidence; source/JVM coverage does not reproduce OEM behavior.


### 2026-10-23 — Stage 13 launch error-message parity

- Final source review found that the picker surfaced unsupported providers and malformed document IDs specifically, but the launch-time revalidation mapper sent those same categories to a generic invalid-selection message if the saved URI later became malformed or unsupported.
- Added explicit launch-time mappings to the existing localized resource strings and extended the Stage 13 static verifier to prevent the mismatch returning.
- Device/provider acceptance remains pending; this improves source-level diagnosability but does not reproduce the user's original provider failure.


### 2026-10-23 — Stage 13 API 17–18 compatibility correction

- A post-implementation compatibility review caught that the tree-grant-pruning helper directly referenced android.content.UriPermission in the outer StorageAccess class, despite the existing design isolating API-19 URI-permission calls in a nested helper for the declared API-17 minimum.
- Moved the persisted-permission enumeration into Api19; the outer class now calls that method only behind the API-level guard. Added a static assertion that fails if UriPermission leaks back into the outer class.
- Full-device API 17/18 class-loading and storage behavior still require an actual legacy device or emulator; the source guard is not treated as a runtime test.


### Stage 16 implementation log — 2026-10-24

- Started Stage 16 from canonical branch head 576a193834266967b52a75e6d2c7de354a377f05 after confirming Stage 15 code work exists but physical-game acceptance is still open.
- Enabled Android RTL mirroring at the application level and replaced the directory chooser's hard-coded English activity label with the localized application name.
- Fixed a concrete user-facing formatting defect in compatibility diagnostics: Java was appending the literal characters \n instead of line breaks, making the device report harder to read.
- Added a Stage 16 CI verifier for locale-key/format-placeholder parity and the new source-level regressions; wired it into the canonical workflow. Corrected Arabic empty-library CTA copy and dynamic scan-count wording.
- Device screenshots, TalkBack/keyboard focus, font scaling, contrast, low-motion/old-API appearance and thermal behavior are still pending. This is a partial implementation milestone, not Stage 16 completion.


### Stage 13 follow-up — corrupted preference value types (2026-10-24)

- Re-audit found one unhandled corruption shape: Android's `SharedPreferences.getString()` throws `ClassCastException` when either storage-selection key is present with a non-string value. The old predicate read values instead of checking key presence, so a corrupt record could crash before it was cleared or be mistaken for no stored selection.
- Added defensive reads and made validation use `contains()` to detect even wrong-typed/partial URI-path records. These records now fail closed, clear the selected state, and release stale external-storage tree grants only after the preference cleanup commits.
- Updated the host preferences fake to reproduce Android's type-mismatch behavior and added regressions for both keys corrupt and a valid URI paired with a wrong-typed path. The Stage 13 static verifier now requires this coverage.
- This improves corrupt-state handling but does **not** close Stage 13: the original provider error still needs the consolidated physical-device acceptance run, and SAF picker behavior cannot be simulated by this host-JVM test.
