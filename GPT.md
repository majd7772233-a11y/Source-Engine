# GPT.md — Persistent Engineering Notes for Source Engine for Android

> This is the assistant's project hand-off notebook. It is intentionally open-ended: append dated findings, decisions, discoveries, unresolved questions, and updated rules as work continues. Do not treat this file as end-user documentation.

## 1. Mission and product definition

Build a polished, reliable Android launcher/runtime integration for compatible Source-engine games. The app should make selecting valid game content, checking readiness, configuring supported options, and launching/returning from a game straightforward. It should eventually have a premium, genuine **Liquid Glass** launcher interface and a reliable, reproducible build/release pipeline.

The repository is the Android integration/launcher plus its available native wrapper and build automation. It is not a full legal redistribution of every Source game, SDK, or proprietary game asset. The current workflow clones external native source (including `nillerusr/srcsdk`) and game data such as `gameinfo.txt`/VPKs must be supplied lawfully by users. Do not promise every Source game works: compatibility must be demonstrated game-by-game and ABI-by-ABI.

## 2. User intent and design direction

The user explicitly wants a **real Liquid Glass** aesthetic, not ordinary glassmorphism with purple cards. Target visual language:
- Layered translucent materials with visible depth, polished glass edges, specular highlights, soft reflections, subtle refraction/distortion-like treatment, and background color bleeding through.
- Animated, flowing multicolor light/background with controlled motion, tasteful neon accents, and carefully crafted icons.
- Premium game-library cards, elegant navigation, refined settings/dialogs, clear states, and consistent motion across the launcher.
- Keep text legible, touch targets reliable, layouts coherent, and effects efficient. A blur implementation must have a fallback for older Android; do not make Android 12 `RenderEffect` the only implementation if older versions remain supported.
- The launcher redesign does **not** automatically redesign the in-game HUD. Native/SDL/game-side changes are separate work and must be tracked separately.

## 3. Repository identity and baseline observations

- Repository: `majd7772233-a11y/Source-Engine`.
- At initial audit, default branch: `android-fixes`, SHA `7fb280dadf9c0199d1fa287844a02292ee9d281a`.
- Other observed branches: `con-wrappers`, SHA `bb39775ebbcd692d36183984e2a4a026f7d07724`; `test`, SHA `d8b4ffc21c4119a40c5c21f723a8a5360d466ce2`.
- The three branches have divergent Android manifests, package identity/version/target settings, source differences, and native/build assets. They must be compared and integrated deliberately. Do not assume a normal merge is conflict-free or that the newest-looking branch is the correct baseline.
- Audited default root uses legacy Android Ant/ADT files: `build.xml`, `project.properties` (target android-29), `AndroidManifest.xml`, `src/`, `res/`, scripts, and `.github/workflows/build.yml`. No root Gradle build or Gradle wrapper was observed on that branch.
- The `con-wrappers` branch includes `jni/` and `libs/armeabi-v7a/` content not present in the default-branch root. Its workflow clones external native repositories and builds libraries with an older NDK toolchain. Preserve and verify this material.
- The workflow `.github/workflows/build.yml` exists across observed branches and has a trigger tied to `con-wrappers`; this is inconsistent with `android-fixes` being the default branch.
- Java entry points observed:
  - `me.nillerusr.LauncherActivity`: legacy launcher, settings/preferences, permission requests, launch button, path/arguments/environment inputs.
  - `me.nillerusr.DirchActivity`: manual filesystem directory picker, `/storage/` enumeration, game-path persistence.
  - `com.valvesoftware.ValveActivity2`: native argument/environment setup, VPK extraction call, shallow `gameinfo.txt` checks, default game `hl2`.
  - `org.libsdl.app`: SDL Android integration.
- Manifest package on default branch was `com.valvesoftware.source`, and Java activities import `com.valvesoftware.source.R`. User's required canonical application ID is **`com.magd.source.engine`**. Update manifest, generated-R imports, and any package-derived references together. Do not blindly rename Java packages `me.nillerusr`, `com.valvesoftware`, or `org.libsdl`; those are source namespaces, not necessarily the app application ID.
- The default manifest had min SDK 17, target SDK 24, version 1.12 and `android:debuggable="true"`; other branches differ. Final SDK policy must be intentional, and release configuration must not leave debugging enabled.
- Launcher default path is `/srceng`; engine startup defaults `gamedir` to `hl2`. `ValveActivity2.findGameinfo()` uses shallow nested directory loops, and `isModGameinfoExists()` scans direct children. Discovery/readiness must be made consistent and robust.
- The directory picker uses `java.io.File`, scans `/storage/`, and does not provide a modern persisted SAF grant workflow. Scoped-storage and removable media behavior must be handled honestly.
- A debug keystore is tracked in the default branch. Never use it as a release signing secret/key; review whether it can be safely removed and add ignore rules for local artifacts.
- Initial audit did not demonstrate a successful clean build or device launch. Never infer that CI passed from the presence of a YAML file.

## 4. Required working rules

1. **Execute directly.** The user strongly dislikes unnecessary discussion, repeated confirmations, and delaying execution. When asked to implement a stage, inspect the current repository and carry out the entire stage before reporting back.
2. **A stage means all of it — no partial hand-off.** Complete every bullet and acceptance criterion in `ROADMAP.md` before reporting completion. If an audit reveals a missing item, fix or finish it immediately as part of the same stage; never merely list it as remaining work, never call a partial implementation complete, and never substitute explanations for repository changes.
3. **Always re-read this file and ROADMAP.md first**, then inspect the current branch/latest commit and compare with previous work. Do not redo finished work or assume previous notes are still accurate without checking.
4. **Code is the source of truth.** Inspect actual files, references, call chains, resource IDs, native interfaces, scripts, workflow behavior, and branch differences. Keep the roadmap synchronized with the implementation.
5. **Branch consolidation must preserve working code.** Compare `android-fixes`, `con-wrappers`, and `test` file by file. Preserve unique native code, shared libraries, SDL files, resources, and scripts when they are needed. Resolve conflicts semantically; do not blindly copy one branch over another or discard large directories.
6. **Canonical package:** `com.magd.source.engine`. Update Android manifest package and all generated-`R` references/package-dependent configuration together. Keep Java namespaces unchanged unless necessary.
7. **Keep the build runnable.** Maintain correct relative paths and required tool versions. Do not casually migrate Ant to Gradle or relocate scripts/resources before tracing every consumer. Build modernization is acceptable only with an explicit verified path.
8. **No false claims.** Do not say a build/test/workflow/device run passed unless it was actually executed and its logs/result were inspected. If the environment cannot run it, state the exact blocker and finish other actionable work.
9. **Prioritize the user-visible logic defects** summarized as “great code but a terrible app”: broken launch, bad discovery, invalid path handling, missing permissions, silent errors, dead controls, misleading states, lost settings, and poor first-run experience.
10. **Design consistency matters.** New UI must align with the intended Liquid Glass design system and existing project architecture. Avoid unrelated visual styles, generic placeholder screens, fake controls, and effects that break performance or readability.
11. **Respect Android API compatibility.** Native SDL/game engine constraints may differ from launcher UI constraints. Use runtime/API guards and graceful fallbacks; do not call newer APIs unconditionally.
12. **Storage is permission-bound.** Never claim the app can bypass Android restrictions. Use SAF/persisted URI permissions where appropriate and verify whether the native engine can consume the selected location.
13. **Protect user data and secrets.** Never commit signing keys, passwords, tokens, or real credentials. Use GitHub Actions secrets for release signing. Treat user game content as user-owned and do not delete/move it without explicit reason.
14. **Native and game content boundaries.** Verify ABI and symbol dependencies before changing or deleting `.so` files. Do not redistribute proprietary game data or assume source checkout includes it.
15. **Every visible control must work.** No decorative fake buttons, silent no-ops, misleading progress indicators, or swallowed exceptions on critical flows.
16. **Commit discipline.** Use clear English commit messages, keep commits focused, and record the latest commit SHA and changed paths in the stage ledger.
17. **Update these notes continuously.** Append dated findings, architecture discoveries, decisions, unresolved risks, completed-stage summaries, test results, and corrections. Keep this file open-ended; do not replace it with a closed/final report.

## 5. Known risk map to keep checking

- **Build pipeline:** Ant/ADT age, missing local.properties, old Android build-tools paths, NDK/toolchain availability, external clones/submodules, generated resources, output paths, upload scripts, branch triggers.
- **Branch differences:** divergent manifests and package IDs, Java source variants, resource differences, native source and prebuilt libraries, workflow and scripts.
- **Application identity:** manifest package, `R` imports, any hard-coded package names, app data path, installer/update metadata.
- **Startup:** launch intent extras, default game/path, SDL lifecycle, native initialization order, VPK extraction, environment variables, orientation/resume/exit.
- **Discovery:** shallow scans, null filesystem results, unreadable directories, game/mod distinction, `gameinfo.txt` validation, hard-coded game lists.
- **Storage:** scoped storage, runtime permission results, SAF, persisted grants, SD/USB media, path spaces/non-ASCII characters, permission revocation.
- **UX:** legacy launcher, first-run flow, scan progress, game readiness, useful error/recovery, accessibility, localization, device size/orientation.
- **Liquid Glass:** real depth/translucency/highlights/blur treatment, motion, accessibility contrast, fallback, low-end performance.
- **Input/gameplay:** SDL touch overlay, multitouch, controller/keyboard, configurable layouts, settings persistence.
- **Native compatibility:** ABI coverage, library dependencies, GLES/GL4ES/Regal, audio, device lifecycle, memory and thermal behavior.
- **Online features:** server browser library is not proof of a complete online feature; verify native and UI flow before claiming support.
- **Security/release:** debug flag, tracked debug keystore, exported components, updater integrity, secret handling, attribution/licenses.
- **Verification:** CI tests, APK contents, install/start smoke tests, logs, release artifact and signature.

## 6. Current phase hand-off

- **Stage 0 is complete.** Canonical branch: `android-fixes`. Final verification candidate: `bf7f506f741cac82c8f1419b4170e4ede3883cbe`; the completion-record commit records the verified result and is synchronized across the maintained branches.
- Final verification passed in GitHub Actions run [38938001867](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/38938001867) on candidate commit `bf7f506f741cac82c8f1419b4170e4ede3883cbe`: source/manifest/package checks, shell syntax checks, all native builds, Ant APK packaging, APK application-ID inspection, required ARMv7 native-library presence checks, and artifact upload all succeeded.
- Artifact: `source-engine-android-debug`, 54,488,439 bytes, SHA-256 `0ed0e8919ef6209114eecd35dd15b3abbcca47588570d7dcb11a782a3f557e6b`.
- Fixed the SDK path mismatch by explicitly selecting the cloned legacy Android SDK, installed the 32-bit runtime libraries needed by its old `aapt`, and made both build entry points generate `res/values/build_info.xml`.
- Before beginning Stage 1, re-read its acceptance criteria. External dependency revisions remain unpinned; device installation/game launch, game discovery/storage UX, compatibility, and runtime stability are not claimed as verified by Stage 0.
- No physical-device installation/launch smoke test was run during Stage 0.

## 7. Dated engineering log

### 2026-10-16 — Initial repository audit and planning
- Read repository metadata and the root manifests/build files/workflow plus representative launcher/native-startup Java files.
- Confirmed three branches: `android-fixes`, `con-wrappers`, and `test`; the default branch was `android-fixes`.
- Confirmed divergent manifest packages/targets, old Ant build, workflow trigger mismatch, native `jni/`/ABI library content on `con-wrappers`, hard-coded `hl2` default, and manual filesystem picker.
- No build or device test was run during this inspection; do not describe the repository as buildable until verified.
- Next: finish branch/file inventory; consolidate carefully; create and maintain `ROADMAP.md` and this notebook; set canonical app ID to `com.magd.source.engine`.

## 8. Append-only working area

Use this section for new observations that do not yet fit another heading. Date each entry, include file paths/commit SHAs where available, and state whether a claim is verified, suspected, or still untested.

- [Open note] Record newly discovered architecture details, branch conflicts, build blockers, user decisions, and test results here.


### 2026-10-16 — Stage 0 implementation update
- **Curated integration commit:** `95da41713d9df94f42a11cdd7d0e927fc9ee2c40`. Follow-up verification/documentation commits include package-import fix `1d150505fbf6c21f5004449e8cc61add7842c4ac`, roadmap update `bd311344a0b7232ac66c0d19761a679a9dd642dd`, and GPT notes update `bcb5c5af40b7d97e9845f7b86b3ab7fb31b26a76`.
- Integrated branch ancestry for `con-wrappers` and `test` while preserving the canonical `android-fixes` tree and applying a selective, code-aware merge. Do not describe this as a blind full merge: incompatible alternate launcher/package code and unrelated Python/Discord automation were excluded deliberately.
- Set `AndroidManifest.xml` package to `com.magd.source.engine`; changed the generated resource import to `com.magd.source.engine.R` in `LauncherActivity.java`, `DirchActivity.java`, `UpdateService.java`, `UpdateSystem.java`, and `SDLActivity.java`. Preserve the Java implementation namespaces.
- Integrated the NDK tierhook subtree from `test`, kept the ARMv7 shared library set, and carried forward the useful localized community contact text from `con-wrappers`.
- Removed `debug.keystore` from the repository after removing the workflow copy step. Never use a checked-in debug key for release signing.
- Replaced the developer-specific root `build.sh` SDK path with SDK/environment checks; `scripts/build-java.sh` now delegates to the root script; `scripts/workflow.sh` uses environment-driven paths and corrected native source paths; `.gitignore` excludes local toolchains and generated output.
- Replaced the old CI workflow that targeted `con-wrappers` and pushed through external deploy/Discord credentials. It now targets `android-fixes`, supports manual dispatch, checks for the expected APK, and uploads a workflow artifact.
- Added a guard to `LauncherActivity.onRequestPermissionsResult` so an empty result array no longer crashes the launcher.
- A post-change scan of the 10 Java files under `src/me/nillerusr`, `src/com/valvesoftware`, and `src/org/libsdl/app` found no remaining `com.valvesoftware.source.R` imports; all five discovered resource imports now target `com.magd.source.engine.R`.
- **Not verified:** no successful CI run/build is available yet; GitHub Actions API returned zero runs for `android-fixes`. Script/toolchain assumptions and actual APK package/resources/native loading remain untested. Keep Stage 0 open until verified.
- **Next action:** inspect the final integrated tree and file modes, obtain a real CI/build result, fix all failures, and update both `ROADMAP.md` and this log with the exact result.


### 2026-10-16 — Branch synchronization
- The canonical tree was committed as a curated multi-parent integration commit, then all branch refs were fast-forwarded (or lease-updated for the temporary validation ref) to the same final commit: `5daf4cb07129bbbf40119169097ca148d4bdd813`.
- Current refs checked: `android-fixes`, `con-wrappers`, `test`, and `stage0-build-validation` all resolve to that same SHA. The temporary validation PR was closed because GitHub Actions returned zero runs; its roadmap clarification was applied directly to the canonical branch.
- This synchronization does not mean CI or runtime behavior has passed. Stage 0 remains open until a real build/CI result and remaining file-level checks are available.


### 2026-10-16 — Stage 0 CI build fixed and verified
- Initial live CI attempts exposed two real build failures: Ant imported the runner's unrelated `/usr/local/lib/android/sdk` instead of the cloned legacy SDK, then legacy `aapt` failed to load `libz.so.1` on Ubuntu 24.04.
- Fixed the selected SDK path in the workflow and Ant invocations, added a fail-fast SDK Ant-build-file check, installed required 32-bit runtime packages, and made root `build.sh` generate the build metadata resources it needs.
- GitHub Actions run `38937163563` completed successfully on `5116d037a074e3c15652e465a3224576767e3b41`; native library compilation, APK packaging, and artifact upload all passed. The uploaded artifact is named `source-engine-android-debug`.
- A final verification candidate adds source/package/shell checks and validates the APK's manifest package and required ARMv7 native libraries. Its CI result must be inspected before Stage 0 is marked complete.


### 2026-10-17 — Stage 1 build integrity work in progress
- Working branch: `stage1-build-integrity`; pull request: https://github.com/majd7772233-a11y/Source-Engine/pull/4. Do not merge or mark Stage 1 complete until the actual CI build is green and the remaining external GitLab dependency revisions are pinned.
- Re-read `ROADMAP.md` and this notebook before work. Stage 0's actual passing run is recorded above; physical-device/runtime behavior remains untested.
- Reworked `build.sh` and `scripts/workflow.sh` to use explicit SDK checks, XML-escape generated metadata, remove stale APK outputs before packaging, remove each native output before rebuilding it, and call the shared APK verifier after Ant succeeds.
- Added `scripts/verify-apk.sh`: validates ZIP integrity, application ID `com.magd.source.engine`, required dex/resources, the five required native libraries, ARM ELF headers for packaged native libraries, ABI directory consistency, APK byte size, SHA-256, and a generated metadata/checksum report.
- Added `scripts/verify-stage1.sh` to statically check shell syntax, stale-output guards, verification integration, artifact upload gating, XML escaping, and retired deployment scripts.
- CI now uses a fixed Ubuntu 24.04 runner label and pinned action commit SHAs observed in the previous successful run. It checks out `nillerusr/srcsdk` at commit `413f5848d9a4534cdf8a4d10f5c1e7b1eaf3835d`, logs exact external dependency revisions and recursive submodule SHAs, verifies the APK, then uploads APK + metadata + SHA-256 report only on success.
- Retired `scripts/upload-to-github.sh`, which embedded a token in a remote URL and force-pushed to an unrelated repository, and the unused Discord notifier. Neither is invoked by the current workflow; both now fail closed without reading credentials.
- **Pending at the time of this note:** CI execution and exact GitLab dependency pins. These were subsequently resolved in the follow-up entry below. Local build on a separate machine and actual installation/game launch remain untested.


### 2026-10-17 — Stage 1 native build verified
- Locked all external source revisions in `scripts/dependencies.lock`: `ndk-binaries` = `2768c10e6ec0c462ba1401f436891e2245d43cf7`; `android-sdk` = `45053e0056ebe4a79c04bab1d9b92bcd1488cba6`; `srcsdk` = `413f5848d9a4534cdf8a4d10f5c1e7b1eaf3835d`. The pinned `srcsdk` commit fixes `gl4es/gl4es-git` at `6996a61a9efed4192ac76dedf96acf9110bb75d7`.
- Added `scripts/fetch-build-deps.sh` to fetch and verify each full SHA, initialize the Source SDK recursively at its recorded gitlink revisions, and expose dependency SHAs to build metadata.
- Fixed the CI-discovered strict-shell error in the native `build()` function by separating local declarations, and corrected the static test assertion so it checks the actual `git -C ... submodule update` command.
- **Verified CI run:** https://github.com/majd7772233-a11y/Source-Engine/actions/runs/38972677450 — success. The workflow passed static source/shell checks, fetched all locked dependencies, compiled tierhook/engine/GL4ES/client/server native libraries, packaged the APK with Ant, verified the APK's application ID and 35 ARMv7 ELF libraries plus their declared dependencies, calculated SHA-256, and uploaded the artifact.
- **Intermediate verified APK** (this commit predates final documentation/trigger cleanup): 56,516,557 bytes; SHA-256 `e5e4c64baf0e660997d20a2bfc0ea2ab0dcea6ade371a45e183f0750edcdb177`. Artifact `source-engine-android-debug` ID `12077976000`, ZIP size 54,489,118 bytes, artifact ZIP digest `sha256:b97434f6553dc80c25ce4673bd88225a4e89b60a22277b6a037415184a4fd27c`.
- No GitHub deployment token or Discord token is used by the build workflow. The legacy push script and Discord notifier remain disabled and fail closed.
- **Not tested:** installation/launch on a physical device, real game-content compatibility, controls, rendering, or runtime stability. Do not claim those as passed.


### 2026-10-17 — Stage 2 startup/lifecycle implementation (runtime acceptance pending)
- Working branch: `stage2-startup-lifecycle`, based on canonical Stage 1 commit `96092dbfc7f030d643125505dbd51c36e2cb3145`.
- Fixed launcher-to-SDL value handoff: current `gamepath`, `argv`, `env`, and `gamedir` are forwarded as explicit extras; launch root is checked before starting, repeated taps are blocked while launch is in progress, and Launch is re-enabled when returning to the launcher.
- Hardened startup validation in `ValveActivity2`: path and game directory are null-safe; rejects traversal/path separators in game directory; handles unreadable/null directory listings; validates the selected game's `gameinfo.txt`; logs actionable failures.
- `ExtractAssets.extractVPK` now returns a result, closes streams, flushes/syncs the output, verifies a non-empty file, and commits the pak version only after successful extraction. Native initialization stops and explains failures rather than continuing.
- User environment overrides are parsed as NAME=VALUE entries, malformed names are ignored with diagnostics, reserved required engine variables cannot be overridden, optional custom VPKs are checked, and required native `setenv` results are checked.
- Fixed the Android pre-23 runtime-permission API call path in `SDLActivity`; empty permission results are handled explicitly. Added SDL new-intent handling and static-state reset after both normal and failed shutdown.
- **CI verified after code review:** Stage 2 PR [#5](https://github.com/majd7772233-a11y/Source-Engine/pull/5) merged as `98dfe6f894bc17a685d766379ab9e6158e788d7a`. Run [38976380349](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/38976380349) passed Stage 0/1/2 static checks, locked dependency fetch, native compilation, Ant APK packaging, APK identity/ABI/dependency verification, and artifact upload.
- **Stage 2 runtime acceptance remains pending:** CI cannot prove that a valid proprietary game starts, or verify pause/resume and repeated sessions on physical hardware. Do not describe those behaviors as device-tested. The implementation is merged; keep the roadmap's runtime checkbox open until an actual compatible device and lawful game content are tested.


### 2026-10-17 — Stage 3 game discovery implementation
- Working branch: `stage3-game-discovery`, branched from the canonical `android-fixes` tree at the Stage 2 documentation baseline.
- Added `src/me/nillerusr/GameDiscovery.java`: case-tolerant `gameinfo.txt` discovery; title extraction from game/title keys; bounded traversal (depth 4, 1,200 directories per root); canonical-path cycle protection; null/unreadable directory handling; a 256 KiB metadata cap; VPK/BSP asset markers; ABI and five required installed native-library checks; persistent paths and last-launch timestamps.
- Added an in-launcher game library with per-entry display name, readiness explanation, full path, last-launched time, Use/Launch/Remove actions, empty states, current-root scan and library rescan. Scanning runs off the UI thread. Removed paths are hidden until the user explicitly scans the current root again; rescans remove stale entries and roots that no longer exist.
- Launch is now gated by metadata, asset markers, packaged ARMv7 ABI support, and required installed native libraries. “Ready to try” explicitly warns that it does not prove game/mod compatibility. This is a conservative filesystem heuristic, not a compatibility certification.
- Added `scripts/verify-stage3.sh` and wired it into CI after Stage 0/1/2 checks. CI run [38981504284](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/38981504284) passed static checks, native compilation, Ant packaging, APK verification, and artifact upload. The initial run caught an invalid Android string-resource apostrophe escape; it was fixed and the passing run uses the corrected resource.
- **Merged:** PR [#6](https://github.com/majd7772233-a11y/Source-Engine/pull/6), squash commit `2bcc9e76eb73f977add87c8a94866a22002b4814`. The verified debug APK from run [38981504284](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/38981504284) is 56,534,379 bytes with SHA-256 `43712524774bed50c24a115fb2105f6699d022b5ad70ce85d7bf601e04005c7f`; artifact ID `12080753429` expires 2026-10-30. **Not device-verified:** no physical-device scan against several real game/mod folder layouts or successful game launch has been run. CI cannot establish discovery completeness or engine compatibility.

- Follow-up review: metadata validation now rejects unbalanced KeyValues braces/quotes and ignores braces inside quoted values and `//` comments; optional `custom`/`addons`/`workshop` content is surfaced separately, and the library summary reports ready candidates accurately. The Stage 3 verifier covers these behaviors.


### 2026-10-17 — Stage 4 storage/SAF implementation candidate
- Working branch: `stage4-storage-saf`, based on the latest canonical `android-fixes` Stage 3 tree.
- Replaced the `/storage/` directory guessing in `DirchActivity` with `ACTION_OPEN_DOCUMENT_TREE` on API 21+ and a constrained primary-shared-storage browser for API 17–20. The picker returns its selection to `LauncherActivity`, normalizes the path, stores the selected game directory when the chosen folder itself contains `gameinfo.txt`, and triggers a library scan.
- Added `StorageAccess.java` to persist and revalidate URI grants, restrict URI-to-path conversion to Android's external-storage document provider, parse volume/document IDs defensively, enforce canonical path containment, and reject unavailable, disconnected, or non-writable paths. The native engine receives a real filesystem path only; generic cloud/document providers are rejected instead of passed as fake paths.
- Launch now normalizes the game root before validation, checks that it is readable, and revalidates a saved SAF selection when the launch path is related to that tree. User-facing strings explain unsupported providers, revoked grants, unavailable/read-only media, and Android storage boundaries.
- Added `scripts/verify-stage4.sh` and wired it into CI after Stage 3 verification. It checks SAF grants, provider/path containment, removable-media state, launcher hand-off, path normalization, strings, and CI wiring.
- **CI verified and merged:** PR [#7](https://github.com/majd7772233-a11y/Source-Engine/pull/7) merged as `de4de87043539facd5ed25c88ab4398e3e9142c8`. Run [38982919788](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/38982919788) passed Stage 0–4 static checks, native builds, legacy Java compilation, Ant APK packaging, APK identity/ABI/dependency checks, and artifact upload. APK size 56,539,061 bytes; SHA-256 `71403db3618200d55e3996da601b936466961eefccdb9ea388f993b11eab3f92`; artifact ID `12081817272` expires 2026-10-30.
- The first Stage 4 CI attempt found the project compiles with Java source level 1.6, so a Java 7 multi-catch failed. Replaced it with separate catches without changing project-wide compiler settings. The SAF bridge's API 19 and API 21 calls were then isolated into nested helper classes to avoid loading newer framework classes on the minimum supported API range. The final run passed.
- **Still unverified on device:** no physical device has exercised the system picker, persisted grants after process death, real SD/USB volumes, read-only media, or permission revocation. Stage 4 implementation/build is merged; its device acceptance checkbox remains open in ROADMAP.md.


### 2026-10-17 — Stage 4 CI completion and merge
- Final candidate commit: `297fdb73dbe70e1c69f32ca42a7dab9998caf5ab`; merge commit on `android-fixes`: `de4de87043539facd5ed25c88ab4398e3e9142c8`.
- Passing workflow run: [38982919788](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/38982919788). All Stage 0–4 static checks passed (including 20 Stage 4 storage/path assertions), locked native dependencies fetched, native libraries compiled, Java sources compiled with the existing legacy source level, Ant APK packaging passed, and APK identity/ABI/native dependency verification passed.
- Verified APK: 56,539,061 bytes; SHA-256 `71403db3618200d55e3996da601b936466961eefccdb9ea388f993b11eab3f92`. Artifact `source-engine-android-debug`, ID `12081817272`, expires 2026-10-30.
- The code implementation is merged. Physical-device acceptance remains open; do not describe SAF persistence, removable-media behavior, or game launch from a selected path as device-tested until the Stage 4 checklist is actually exercised.


### 2026-10-17 — Stage 5 launcher information architecture and core UX
- Working branch: `stage5-launcher-ux`; PR [#8](https://github.com/majd7772233-a11y/Source-Engine/pull/8) merged into `android-fixes` as `772a49b15163e3d354fda4d550a01e17930a1214`.
- Replaced the legacy form-heavy launcher layout with a library-first home, first-run guidance, prominent folder selection, richer game cards, and a per-game details dialog showing readiness/reason, detected path, launch root, game directory, optional content, last launch, and the compatibility caveat.
- Preserved library scan/rescan/remove behavior, command-line arguments, environment overrides, game path, saved preferences, and explicit SDL intent extras. Advanced settings now start collapsed; scan/launch progress and scan failures have visible feedback. Obsolete commented-out checkbox controls and the old commented launch fallback were removed.
- Improved long-name/path handling, 48dp minimum touch targets, focusability and content descriptions, guarded accessibility live-region calls for API 17–18, allowed portrait-only devices in manifest feature filtering, and added Arabic translations for launcher/library states.
- Added `scripts/verify-stage5.sh` and CI integration. Workflow [39070653298](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39070653298) passed Stage 0–5 static checks (16 Stage 5 assertions), locked dependency fetch, native compilation, Java/Ant APK packaging, APK identity/ABI/dependency verification, and artifact upload.
- Verified debug APK: 56,548,121 bytes; SHA-256 `22f26e01e5ffaa26718bc525bd7457126e6c320bdc1bc72e93a16e8a3741c1e9`; artifact ID `12115152142`, expires 2026-10-31.
- **Device acceptance pending:** test first-run/picker flow, cancellation and recovery, scanning, detail/readiness states, advanced-setting persistence, portrait/landscape, keyboard/controller focus, launch progress, and return from a lawful compatible game on real Android hardware. Do not claim these have been physically tested.


### 2026-10-17 — Stage 6 Liquid Glass implementation and CI
- Working branch: `stage6-liquid-glass`; PR [#9](https://github.com/majd7772233-a11y/Source-Engine/pull/9) merged into `android-fixes` as `239fd915f307b43aa63867722b42e1204c332217`.
- Added `src/me/nillerusr/LiquidGlassTheme.java`: shared translucent gradient panels, edge highlights, pressed/focused/disabled button states, refined text-field surfaces, guarded elevation, and an animated multicolor radial-gradient backdrop compatible with the existing API-17 minimum. It deliberately avoids Android 12-only `RenderEffect`; this is ambient/refraction-like styling, not true backdrop blur.
- Applied the shared styling to the library, dynamic game cards, header/footer, advanced settings, fields, buttons, and dialogs. Added a persisted `liquid_motion` preference to the advanced panel; animation can be disabled and is paused with the launcher lifecycle.
- Added `scripts/verify-stage6.sh` and wired it after Stage 5 checks in CI. Post-merge canonical workflow [39072023024](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39072023024) passed all Stage 0–6 static checks (including 16 Stage 6 assertions), dependency fetch, native compilation, legacy Java/Ant packaging, APK identity/ABI/native dependency verification, and artifact upload.
- Verified post-merge APK: 56,552,089 bytes; SHA-256 `59ad6cdd9b3f262c356e08d304759700c883cc80a38b2a0e89ef8c4d5dec96e7`; artifact [12114973248](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39072023024/artifacts/12114973248), expires 2026-10-31.
- **Not physically validated:** no real-device visual/performance test has been performed. Test low-end GPU frame pacing, readability, touch feedback, motion toggle, dialogs, rotation, and battery/thermal behavior before marking device acceptance complete. The implementation does not redesign the in-game HUD.


### 2026-10-17 — Stage 7 touch controls and input integration
- Working branch: `stage7-touch-controls`; PR [#10](https://github.com/majd7772233-a11y/Source-Engine/pull/10).
- Audited the existing Android SDL input paths: touch/multitouch normalization and native touch forwarding in `SDLActivity`; keyboard/mouse/pointer forwarding; Android volume/camera/zoom keys intentionally left to Android; and controller enumeration/add/remove polling in `SDLControllerManager`.
- Added `src/org/libsdl/app/TouchControlsOverlay.java`, layered above the SDL surface. The transparent overlay forwards touches outside control hit regions to the underlying game surface. The virtual movement pad emits digital W/A/S/D, fire emits SDL mouse-button events, and jump/use/reload emit Space/E/R key events. It does not emulate analog axes or guarantee support for nonstandard game/mod bindings.
- Added three layout presets, drag-to-reposition editing, size/opacity/sensitivity controls, hide/show, reset, and SharedPreferences profiles keyed from game path + game directory. Position values are normalized to the screen so they survive orientation changes.
- Hardened input lifecycle handling: pointer IDs are tracked separately, multiple fire pointers do not release the mouse button prematurely, key-source counts prevent releasing a key still held elsewhere, and cancel/pause/destroy release held synthetic input. Touch input is ignored until native libraries initialize. Existing hardware controller hot-plug polling remains in SDLControllerManager.
- Added English and Arabic resources plus `scripts/verify-stage7.sh`; CI now runs the Stage 7 verifier after Stage 6. The verifier checks 19 input/configuration/localization/integration assertions.
- Early CI candidates failed because the new class lacked the generated `com.magd.source.engine.R` import; this was corrected. Final candidate `d172abdd639d314cae8f104d09dd35206f1a32b3` passed workflow [39073005622](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39073005622): all Stage 0–7 static checks, pinned dependency fetch, native compilation, Java/Ant packaging, APK identity/ABI/dependency verification, and artifact upload.
- Verified debug APK: 56,562,054 bytes; SHA-256 `d9f0a8e4ce192d4122514b2ee774e095d56488d9a8c3a1d6f2880d46828337e4`; artifact ID `12115648990`, expires 2026-10-31.
- **Not device-verified:** do not claim the virtual controls work in a real game until a compatible game is run on hardware. Test simultaneous movement/fire, key mappings, control edits and persistence per game, orientation changes, pass-through touch, controller connect/disconnect, Android volume keys, and pause/resume without stuck inputs. Keep Stage 7 device/game acceptance open in `ROADMAP.md`.


### 2026-10-17 — Stage 7 merged and canonical CI verified
- PR [#10](https://github.com/majd7772233-a11y/Source-Engine/pull/10) was squash-merged to `android-fixes` as `b46a6ae5fdff9b1c3328fe10b50dd1701f344c0a`.
- Post-merge canonical workflow [39073356292](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39073356292) succeeded. Stage 0–7 checks passed, pinned dependencies fetched, native libraries compiled, Ant APK packaging succeeded, APK identity/ABI/dependency checks passed, and the artifact uploaded.
- Canonical APK: 56,562,047 bytes; SHA-256 `c78883743d21c5cf56372553096a59c3c148a5f9057dfd9a21eadfe6e3b8de69`; artifact ID `12115514936`, expires 2026-10-31.
- This closes the implementation and CI portion of Stage 7 only. The physical-device/gameplay acceptance checkbox stays open until a compatible game is tested on real hardware.


## Stage 8 engineering ledger — 2026-10-18

- Work branch: stage8-graphics-audio-compat, based on android-fixes.
- Added Advanced settings → Device compatibility diagnostics in LauncherActivity. It reports manufacturer/model, Android release/API, system ABI, display dimensions/density/refresh rate, Android-advertised GLES feature level, current available-memory/low-memory snapshot, audio-output count on API 23+, and presence of the five native libraries already required by the APK verifier.
- Newer API calls are guarded for minSdk 17. The GLES value is explicitly described as Android-advertised feature metadata, not an actual EGL/GL context query. Audio output enumeration is not presented as proof of audible playback. Memory is a snapshot, not peak game memory.
- Added English and Arabic resource strings, scripts/verify-stage8.sh, CI wiring, and docs/COMPATIBILITY.md with verified repository facts, limitations, device test matrix, and measurement protocol.
- Deliberately did not add speculative graphics/audio switches. Existing command-line/environment overrides are the only generic controls until their native consumer and effect are verified.
- Physical testing limitation: no device benchmark was executed. Cold/warm startup, actual GLES extensions/rendering, frame pacing, sustained memory/thermal behavior, audible routing, and crash behavior remain unverified; roadmap acceptance remains open for those device results.
- Candidate CI was inspected end-to-end: workflow [39074171254](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39074171254) passed all 12 Stage 8 assertions, native compilation, Java/Ant packaging, APK verification for 35 ARMv7 ELF libraries, and artifact upload. APK 56,568,273 bytes; SHA-256 `e451d1c06303a2e03ce44bcc979c1393842f045943704eee769d18dda0e4c459`; artifact `12115922638`, expires 2026-10-31.
- PR [#11](https://github.com/majd7772233-a11y/Source-Engine/pull/11) was squash-merged to `android-fixes` as `fa3459c084df64f187a364ca87e5033e636f2a11`. README updated with merge and artifact evidence. The Actions connector does not expose a post-merge push run in its PR-only workflow query; do not claim a distinct post-merge run was inspected. Candidate code is identical to the merged implementation.
- Never mark physical-device acceptance complete without actual evidence. Stage 8 remains open only for representative-device measurements and real game graphics/audio/thermal/crash validation.


## Stage 9 engineering ledger — 2026-10-18

- Working branch: stage9-online-capability-audit, based on android-fixes.
- Audited the manifest's INTERNET and ACCESS_NETWORK_STATE permissions, launcher UI and Java/native boundary, native build script, and APK verifier. The Java launcher has no verified server discovery/list/join bridge. The build script compiles engine/client/server-wrapper/GL4ES libraries, and the APK verifier's five required core libraries are not evidence of a working server browser.
- Added Advanced settings → Online features and server browser status. It reports Android connectivity state only and explicitly says that this does not prove server reachability. It marks the in-app browser unavailable with a concrete reason in English and Arabic.
- Did not invent a project backend, server list, query endpoint, loading state, or join control. The app is not a game-server host; multiplayer depends on the selected game/mod and a known compatible server.
- Added scripts/verify-stage9.sh, CI wiring, and docs/ONLINE-FEATURES.md. Static checks validate the disclosure and permission/UI wiring; they do not claim a real server query.
- Acceptance path: explicitly unavailable when no verified native interface is available. Actual browser implementation is blocked until native discovery/query/join contracts and a known compatible server are established. Do not mark real multiplayer tested.
- PR [#12](https://github.com/majd7772233-a11y/Source-Engine/pull/12) merged to `android-fixes` as `b9660ab08752d83c4ecd5f387298636d3d214e05`.
- Final pre-merge candidate workflow [39075085755](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39075085755) passed all Stage 0–9 checks, native compilation, Java/Ant packaging, APK identity/ABI/dependency verification (35 ARMv7 ELF libraries), and artifact upload. Stage 9 passed 12 online-capability assertions. APK: 56,571,000 bytes; SHA-256 `56cceb193f2b562bf1f5bb32c9c68da8e85f25b72ffa8d8647f81103af5a516f`; artifact `12116274496`, expires 2026-10-31.
- No device or real multiplayer testing was performed. The GitHub Actions commit-run connector only exposes PR-triggered runs, so no distinct post-merge push run was independently inspected.


### 2026-10-18 — Stage 10 reliability/security implementation candidate

- Branch: `stage10-reliability-security`, based on `android-fixes`.
- Treat Stage 10 as a whole stage: audit/fix permission results, path/argument/environment validation, filesystem write correctness, component exposure, updater safety, user-path logging, and resource cleanup; run every existing verifier plus the native/Java/Ant/APK pipeline; fix all CI failures before merge.
- Do not claim the updater is production-ready: version checks are advisory only, and automatic APK update notification/installation is disabled until signed release metadata, checksum/signature validation and a verified install flow exist.
- Asset extraction must never chmod the private app directory to world-writable permissions. Cache validity depends on version plus the recorded extracted byte count; a failed write must not be recorded as successful.
- Explicitly mark internal activities/services non-exported. Permission callbacks with null/empty results must fail safely and leave no stale progress UI.
- Candidate adds `scripts/verify-stage10.sh`; CI must pass Stage 0–10 verifiers, pinned native dependency build, Java/Ant packaging, APK identity/ABI/dependency verification, and artifact upload before merge. Do not mark physical device acceptance complete without real hardware evidence.
