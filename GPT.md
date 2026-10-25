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

## Stage sequencing clarification (2026-10-24)

- Stage 12 is the permanent defect/feedback lane, not the final stage. The actual final acceptance gate is unnumbered.
- Finish numbered implementation stages sequentially. Do not ask the user to perform piecemeal Android device tests during Stages 13–17. Consolidate physical-device/game testing into Stage 18 after implementation and the required build/provenance work are ready, immediately before unnumbered Final Acceptance.
- Stage 15 source implementation may be called implementation-complete based on the verified source/JVM/CI evidence, but hardware/game acceptance remains deferred and must not be claimed.
- Stage 16 is not complete while genuine Liquid Glass implementation or source-level accessibility/fallback work remains. Do not relabel a deferred device check as completed implementation.

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

- Canonical repository/branch: majd7772233-a11y/Source-Engine, android-fixes.
- **Correct roadmap model (2026-10-22):** Stage 12 is the permanent user-feedback/QA/bug-intake/correction lane; it is not the final stage. New implementation milestones are numbered Stage 13 onward. The real final gate is **Final Acceptance**, deliberately unnumbered.
- **Historical work point (superseded):** Stage 13 storage-provider work began first; the current implementation milestone is Stage 15. See ROADMAP.md for the active ledger.
- Stage 13 begins by re-auditing code already merged in PR #18; do not rewrite correct implementation blindly. Keep automated-test evidence distinct from physical-device acceptance.
- PR #19 introduced the source-provenance audit notes. An earlier workflow built the APK but did not attach a downloadable artifact; that historical behavior was changed so the verified Debug test APK is now uploaded by CI. Device installation/acceptance must still be verified separately.
- Planned milestones after Stage 13: Stage 14 discovery/readiness/launch truth; Stage 15 in-game input; Stage 16 launcher UX/accessibility/localization; Stage 17 feature truth/settings/online-update behavior; Stage 18 runtime compatibility/performance/regression evidence; Stage 19 source provenance/privacy/release distribution. See ROADMAP.md for criteria.
- After several milestones, the user should test an approved candidate and feed all findings into Stage 12. Prioritize any critical failure before progressing. Final Acceptance is unnumbered and requires implementation, user/device, legal and release evidence.

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


### 2026-10-18 — Stage 10 reliability/security (merged and CI-verified)

- Branch: `stage10-reliability-security`, based on `android-fixes`.
- Treat Stage 10 as a whole stage: audit/fix permission results, path/argument/environment validation, filesystem write correctness, component exposure, updater safety, user-path logging, and resource cleanup; run every existing verifier plus the native/Java/Ant/APK pipeline; fix all CI failures before merge.
- Do not claim the updater is production-ready: version checks are advisory only, and automatic APK update notification/installation is disabled until signed release metadata, checksum/signature validation and a verified install flow exist.
- Asset extraction must never chmod the private app directory to world-writable permissions. Cache validity depends on version plus the recorded extracted byte count; a failed write must not be recorded as successful.
- Explicitly mark internal activities/services non-exported. Permission callbacks with null/empty results must fail safely and leave no stale progress UI.
- Added `scripts/verify-stage10.sh`; all Stage 0–10 verifiers passed in workflow [39077037357](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39077037357), including 26 Stage 10 assertions, pinned native dependency fetch, native compilation, Java/Ant packaging, APK identity/ABI/dependency verification (35 ARMv7 ELF libraries), and artifact upload. PR [#13](https://github.com/majd7772233-a11y/Source-Engine/pull/13) merged as `c6c21d2e21de9829214214d40fd67ceba1e6e9f2`. APK: 56,572,260 bytes; SHA-256 `71745581d79f0f195f890b764a70d4d99aacdc9460068dbaebece1ffd750d3fa`; artifact [12116564321](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39077037357/artifacts/12116564321), expires 2026-10-31. Do not mark physical device acceptance complete without real hardware evidence.


### 2026-10-20 — Release-signing setup and Stage 5 state recheck

- Re-read the current canonical branch and the required working rules before acting on the user request to start Stage 5. The live repository had advanced beyond the older hand-off summary: Stage 5 was already implemented and merged in PR [#8](https://github.com/majd7772233-a11y/Source-Engine/pull/8) as `772a49b15163e3d354fda4d550a01e17930a1214`; Stage 6–10 are also present. Do not reapply or overwrite those UI changes. Stage 5 implementation/CI acceptance is recorded, while its real-device interaction/game-launch acceptance is still pending.
- The user explicitly supplied the known signing-secret names: `MY_KEYSTORE_JKS`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`. Started the release-signing portion of Stage 12 on branch `stage12-release-signing` without reading, printing, or embedding any secret values.
- Added `scripts/prepare-release-version.py` for strict `MAJOR.MINOR.PATCH` handling and a deterministic Android versionCode; `CHANGELOG.md`; and `scripts/verify-stage12.sh` with static and valid/invalid semantic-version checks.
- Updated `.github/workflows/build.yml` to keep the debug artifact separate, optionally build the non-debuggable Ant `release` variant, align and sign with `apksigner` using the exact repository-secret names, verify package/version/signature, produce SHA-256 and certificate reports, and upload a versioned release artifact. Release signing is opt-in via a `v*` tag or a manual dispatch with `sign_release=true` and a version.
- `MY_KEYSTORE_JKS` is defined as the Base64-encoded bytes of the user's keystore file. Decoded key material is restricted to a temporary file and removed by a shell trap; password values are provided to `apksigner` using `env:` references and shell tracing is disabled. Never check the keystore into the repository.
- **Not yet verified:** this branch's PR workflow has not run yet; real GitHub secret values cannot be inspected with the repository connector; the tag/manual signing path must run before claiming the secrets are configured correctly or that a signed APK exists. Stage 12 overall remains open, and no device installation was performed.

### Stage 5 follow-up — failed-scan state preservation (2026-10-20)

- Audited the already-merged Stage 5 launcher implementation against its user-visible error/recovery requirements. Found that `scanLibrary()` caught a runtime scan exception but still wrote the partially discovered list and pruned the registered roots/hidden paths, potentially replacing the last successful library state with incomplete results.
- Changed the UI-thread completion path to commit registered roots, hidden entries, and discovered paths only when the scan completed without an exception. Progress is always cleared and the existing recoverable error message remains visible; successful scans retain the prior behavior.
- Expanded `scripts/verify-stage5.sh` with a regression assertion that the registry/path writes are guarded by a null scan-error state.
- Follow-up branch: `stage5-scan-error-recovery`; scope is limited to the launcher scan error path, Stage 5 regression coverage, and this hand-off documentation. Hardware/device acceptance remains unverified.


### Stage 5 follow-up verification record — 2026-10-20

- PR [#15](https://github.com/majd7772233-a11y/Source-Engine/pull/15) was squash-merged into `android-fixes` as `418eb63cca6da8dd21a3549b99c7a2b91132008b`.
- PR workflow [39473018430](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39473018430) passed the Stage 5 regression verifier (17 launcher UX assertions), all other wired source verifiers, locked dependency fetch, native compilation, Java/Ant packaging, APK identity/ABI/dependency checks, and artifact upload. The post-merge canonical push workflow [39473351826](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39473351826) also completed successfully.
- Canonical APK: 56,572,263 bytes; SHA-256 `9000c986fb4ff55249ba793614c8d5be8cda0809f8abd3f5d13a5ff82f019b91`; [artifact 12287199143](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39473351826/artifacts/12287199143), expires 2026-11-03.
- Files changed in the follow-up: `src/me/nillerusr/LauncherActivity.java`, `scripts/verify-stage5.sh`, `ROADMAP.md`, `README.md`, and `GPT.md`.
- This is CI/build acceptance only. Real-device scan failure injection, library retention, first-run navigation, storage behavior, and actual game launch remain unverified on physical Android hardware.

### Stage 11 engineering ledger — automated tests and quality gates

- Working branch: stage11-automated-quality-gates, based on canonical commit 24ed5061c80d317322489420cbddd28f3823b81a.
- Added pure Java LauncherLogic and wired the production launcher/native bridge to the same helpers for canonical path normalization, single-component game-directory validation, and native -game argument construction.
- Added LauncherPreferences.migrate: a one-time additive schema migration that seeds defaults only for absent keys, preserves existing argv, gamepath, env, gamedir, and unrelated preferences, and avoids overwriting values from a newer schema.
- Added a standalone JVM test harness with small Android API test doubles to compile and exercise the real GameDiscovery, LauncherLogic, and LauncherPreferences production classes using temporary fixtures. Coverage includes invalid/normalized paths, arguments and traversal rejection, legacy preference preservation/idempotence, bounded discovery, title/metadata parsing, assets/native payload/ABI readiness, launch timestamps, and defensive shared-preference snapshots.
- Added scripts/verify-stage11.sh for XML and resource reference integrity, Arabic/default string key consistency, canonical package/API invariants, immutable dependency pin validation, test coverage and CI wiring. Added scripts/validate-workflow.py, ShellCheck error-level lint and pinned-version actionlint to run before downloading/building the native SDK dependencies.
- Added docs/DEVICE-SMOKE-TEST.md. It is a checklist, not a record of tests already run; all physical-device/gameplay results remain pending.
- Do not mark CI/build or JVM outcomes as physical-device acceptance. Record exact PR and post-merge action IDs and APK checksum after successful CI; fix any failure before merging.


### Stage 11 completion record — 2026-10-21

- PR [#16](https://github.com/majd7772233-a11y/Source-Engine/pull/16) merged into the canonical branch as dec2b5f6b01c83cdfc58f8897578685680670b96.
- PR workflow [39552539276](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39552539276) and canonical push workflow [39552785558](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39552785558) both completed successfully.
- JVM suite result: 73 assertions passed against production LauncherLogic, LauncherPreferences and GameDiscovery classes using temporary filesystem fixtures and minimal Android test doubles. Coverage includes canonical path handling, invalid input and traversal rejection, quoted game arguments, additive/idempotent preference migration, discovery title and metadata validation, readiness failures for missing assets/native libraries/unsupported ABI, bounded scans, last-launch persistence and defensive library snapshots.
- Static verifier result: 13 Android XML files, 109 R.string references and 16 production Java files checked. All Stage 0–12 verifiers passed. ShellCheck (error severity), YAML structural checks, full-SHA action pin checks and actionlint passed before the expensive native dependency fetch.
- Full native dependency fetch/build, Java/Ant packaging, APK identity/ABI/35 ELF native-library verification, SHA-256 recording and success-gated artifact upload passed on the canonical commit. APK: 56,573,086 bytes; SHA-256 f25673a0132c7a4b6b0e73ed0a48d27e32f914431521817cfd31a37a94271083; artifact 12323743499 expires 2026-11-04.
- The physical-device checklist is docs/DEVICE-SMOKE-TEST.md. No real-device, storage-provider, gameplay, audio, performance, thermal or crash-free runtime test is claimed; that acceptance remains open.



### 2026-10-21 — Product-level logical/UX audit and then-terminal roadmap interpretation (historical; superseded)

- Canonical audit baseline: `majd7772233-a11y/Source-Engine`, branch `android-fixes`, commit `8b204a102437a20f621e42381c8f38b7b10119dc`.
- User reported a physical-device storage-picking failure: “This provider cannot be used by the native engine…”. Code path in `StorageAccess.Api21.resolveFilesystemPath` permits only `com.android.externalstorage.documents`, then requires the candidate be a directory, readable **and writable**. `DirchActivity` maps null resolution/grant persistence failures to the same generic message. The exact provider/condition on the user's device remains unknown until URI-authority/permission-state diagnostics or device logs are captured.
- `DirchActivity` derives a parent launch root when the picked folder itself contains `gameinfo.txt`, while the persisted tree grant remains on the selected child. This can make the engine path broader than the selected grant. Pre-API-21 fallback only browses primary shared storage.
- `LauncherActivity` requests `WRITE_EXTERNAL_STORAGE` and `RECORD_AUDIO` together on API 23+ and treats denial of either as one general permission failure. No special all-files-access UX is present. Any remediation must respect Android's user-consent, policy and private-directory boundaries; never imply root/unlimited access.
- `GameDiscovery.scanDirectory` bounds traversal to depth 4 / 1200 directories but has no truncation state; inaccessible directory listings can silently return incomplete results. Readiness depends on metadata/asset markers/native file/ABI checks, not proof of successful engine/game execution. `LauncherActivity.startSource` records the last-launch timestamp before `startActivity` / native success.
- Resource audit: 132 default-locale strings vs 98 Arabic strings; 34 user-facing keys have no Arabic counterpart. The current Stage 11 resource check ensures Arabic keys are a subset of default keys, but does not enforce full Arabic coverage.
- `TouchControlsOverlay` supports a documented fixed keyboard/mouse mapping (WASD, Space, E, R, mouse fire), not a true analogue gamepad axis or universal mod bindings. **Correction to the initial investigation:** hiding controls does not strand the user; the gear menu is still drawn and hit-tested when controls are hidden. Do not report that as a confirmed bug.
- `LiquidGlassTheme` draws animated radial gradients/tinted panels and styled controls; source inspection does not show background blur/refraction, and `activity_directory_choice.xml` remains visually distinct legacy styling. Actual visual quality and low-end performance need device review.
- Stage 17 removes the disconnected `UpdateSystem` marker checker, the disabled `UpdateService` stub, unused update string, and dead checkbox fields. There is no in-app update checker/installer; server browser/join remains explicitly unavailable.
- `showCompatibilityDiagnostics` reports platform metadata/snapshots; it does not create a real GL context, benchmark a game, or verify audible runtime output. Stage 17 replaces inline environment parsing with the pure-Java `EnvironmentOverrides` helper, which validates the full list before mutation and has its own JVM regression suite.
- Do not treat the successful 73-assertion JVM suite, static checks, or 35-library APK verification as evidence of SAF-provider behavior, working gameplay, input, audio, thermal stability or crash-free sessions.
- Historical note: the then-current plan mistakenly treated Stage 12 as the final gate. Superseded by the 2026-10-22 correction: Stage 12 is an ongoing feedback lane and implementation milestones 13+ are planned. Implementation has now started on `stage12-storage-fixes`: detailed SAF errors and canonical grant containment, incomplete-scan state, Arabic parity + placeholder validation, optional-microphone permission separation, preflight validation of environment overrides, truthful launch-attempt wording, touch-map disclosure, and directory-picker visual integration are present. `docs/USER-SETUP-TROUBLESHOOTING.md` was added. Full CI and device verification are still pending; do not mark the final stage complete based on source edits or a green build alone. Findings live in docs/PRODUCT-QUALITY-AUDIT.md. Historical instruction superseded by the later roadmap correction: small fixes may use 12.x within the ongoing feedback lane; broader work is assigned to Stage 13+.


### 2026-10-22 — Stage 12 candidate CI succeeded; acceptance remains open

- Branch `stage12-storage-fixes`, candidate commit `59f03a4c2013fe36f42ab9d417ebfb07e261d10d`, PR [#18](https://github.com/majd7772233-a11y/Source-Engine/pull/18). Candidate workflow [39679419842](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39679419842) completed successfully: all Stage 0–12 verifiers, 80 JVM regression assertions, 151 Arabic/default resource keys with placeholder parity, workflow/YAML/ShellCheck/actionlint validation, locked native dependency fetch/build, Ant package, APK verification and artifact upload.
- APK is `com.magd.source.engine`, ARMv7/`armeabi-v7a`, 35 native ELF libraries, 56,589,391 bytes, SHA-256 `aae99b750b88030a0ff0cc5d7bf293b260c369c942e0b61439fa77af2707e295`. Debug artifact `12383626531` expires 2026-11-04; ZIP digest `a1c770735469be9b585caaa22286ec3c9c7c75437129efb572869434850bdb5e`. This run did not perform release packaging/signing because it was an ordinary PR debug build.
- Historical candidate-run status (superseded by the 2026-10-22 roadmap correction): PR #18 merged and post-merge workflow [39680488677](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39680488677) passed, but user-device/runtime, accessibility, rights/privacy and signed-release acceptance remained outstanding. The old interpretation that Stage 12 was final and there was no Stage 13 is no longer current; see the corrected current hand-off above.

### 2026-10-22 — Stage 12 merged and canonical CI verified

- PR [#18](https://github.com/majd7772233-a11y/Source-Engine/pull/18) merged into `android-fixes` as `f530574869f51091b5dccf0365d12f582c737ce2`. Canonical post-merge workflow [39680488677](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39680488677) succeeded: 80 JVM assertions, all Stage 0–12 verifiers, lint/workflow checks, locked native build, Ant packaging, APK identity/ABI/library verification and artifact upload.
- Canonical debug APK: 56,589,387 bytes; SHA-256 `d8d2721257bd129a4cd43aed2f740c8e5dd76bad1b350b30b54b9f750de826ef`; artifact `12383377856`, ZIP digest `ba388bc3b1c9fe70ebe983c9259f4758f49bd761e87e636c6530a85375faab5d`, expires 2026-11-04. Release packaging/signing was skipped.
- Historical status wording at the time of this CI run (superseded): Stage 12 was incorrectly described as the final gate. The device/runtime, accessibility, rights/privacy and signing acceptance items remain open, but they are now mapped to Stages 13–19 and the unnumbered Final Acceptance gate.


### 2026-10-22 — Stage 12.9 license inventory and fail-closed release guard

- Re-read the live canonical branch and Stage 12 rules. Historical note: that version of the roadmap incorrectly had no Stage 13. Superseded by the corrected numbering recorded in the current hand-off and ROADMAP.md.
- Audited the tracked repository tree at `dadb5e18d5774535fe7d8e1b7c0511b3640c0296`, the exact pinned `nillerusr/srcsdk` tree (`413f5848d9a4534cdf8a4d10f5c1e7b1eaf3835d`), its `gl4es/gl4es-git` submodule pin (`6996a61a9efed4192ac76dedf96acf9110bb75d7`), the native build workflow, the 35 tracked ARMv7 libraries, SDL-namespaced Java glue, and the available license/notice paths.
- Added `docs/THIRD-PARTY-LICENSE-AUDIT.md`. Findings are intentionally provisional: the app repository has no top-level project license/third-party notice, the pinned Source SDK fork has no root license or `thirdpartylegalnotices.txt` in its inspected tree, and 30 checked-in native binaries are not rebuilt by the current workflow and lack a committed per-binary provenance/license manifest. The GL4ES license at its exact pinned commit is MIT and requires retaining its notice; protobuf/gtest notices exist in the Source SDK tree. These findings do not decide whether any particular binary is authorized for redistribution.
- The workflow now uploads a `source-engine-android-debug-test` artifact after successful APK build and verification so the app can be installed and inspected. The Debug test artifact is separate from the opt-in non-debuggable signed-release path. The third-party provenance audit remains documentation about unresolved components; this workflow does not use it as a build or test-artifact upload gate.
- Extended `scripts/verify-stage12.sh` with static wiring checks and behavior tests for the blocked, incomplete, and complete synthetic review-record cases. PR CI must pass before these changes are considered integrated. Existing user-device/storage/gameplay/accessibility/performance and signed artifact install/upgrade acceptance remain pending.


### 2026-10-24 — Stage 14 game discovery and launch-state work

- Began Stage 14 from canonical android-fixes at 5030582b76048bed33844ef9223fbf558cae5bed.
- Re-audit found the scan completion callback redrew the library even after incomplete scans. renderGameLibrary() could prune paths whose media/provider was temporarily inaccessible; the UI and persisted snapshot therefore did not actually remain intact. The completion path now redraws and stores only after every configured root finishes successfully.
- Added explicit root-status categories, a cooperative Cancel scan control, a complete-empty vs. missing/unavailable distinction, localized progress/results and Stage 14 CI/JVM coverage.
- Keep last attempt wording; activity launch is not proof of stable native gameplay. Physical device/provider checks and the independent release provenance gate remain open.

### 2026-10-22 — Resume point: canonical Stage 12 acceptance and audio policy

- Confirmed PR #19 merged at `cbb4dfedc255c48100abc648e9a04805dc6a9213`; canonical workflow [39832326942](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39832326942) passed Stage 12 (30 assertions), JVM regression tests (80 assertions), static/workflow validation, native build and APK integrity verification.
- The audit is still `RELEASE BLOCKED`; the verified APK was not uploaded and signed-release steps were skipped. Do not provide a download link for this run or call the app release-ready.
- Corrected the quality audit's stale permission baseline: PR #18 separated startup storage permission from optional microphone permission. The modern Android all-files-access path and version matrix are still unresolved.
- Documented the existing `android:allowAudioPlaybackCapture="true"` as an opt-in for eligible playback capture by external, user-approved recorders. It does not initiate recording from Source Engine. Added a device smoke-test requirement and official Android documentation link.
- Historical plan note superseded on 2026-10-22: Stage 12 is now the permanent feedback/correction lane; the numbered implementation plan continues at Stage 13, and Final Acceptance is unnumbered.


### 2026-10-22 — Roadmap numbering model corrected per user intent

- The user clarified that Stage 12 must be a permanent place for feedback, bug reports, modifications and corrective fixes discovered over time—not a numbered final stage.
- The current roadmap now defines Stage 12 as the cross-cutting feedback/defect-intake lane; numbered milestones 13–19 cover implementation, runtime evidence and controlled release preparation; **Final Acceptance has no stage number**.
- Stage 13 is next and prioritizes the storage-provider/folder-scope failure previously reported. Existing PR #18 fixes must be re-audited and physically verified rather than assumed correct.
- Old dated engineering-log entries mentioning “no Stage 13” preserve the original historical plan only; they are superseded by this entry and the current hand-off above.
- Never bypass the fail-closed source-provenance gate to generate a downloadable APK. Plan the user’s candidate test around an artifact that is available through an approved gate.



### 2026-10-22 — Stage 13 started: storage provider and permission reliability

- Re-audited `StorageAccess`, `DirchActivity` and launcher path revalidation. The external-storage provider allowlist and canonical containment remain deliberate constraints.
- Require returned and persisted read/write grants, reject read-only mounted storage, and clear stale/corrupt folder selection state after provider/volume/path/access failures.
- Added `SRCAPK_STORAGE` diagnostics that omit URI strings and absolute paths, plus host-JVM regression coverage wired into the build workflow.
- Physical Android picker/provider, process restart, SD/USB and original-error reproduction remain pending. Do not report Stage 13 complete until the device outcomes are recorded.


### 2026-10-23 — Stage 13 scope-boundary follow-up

- Continued from the live canonical Stage 13 implementation at `48ac4875fef8bacd5cb3b3680c1ab4421e10b4c7`.
- Found a defense-in-depth gap: canonicalizing `primary:Games/../Sibling` keeps the result inside the storage volume but can move it outside the exact selected tree. The next correction explicitly rejects dot and dot-dot document-ID path segments and adds a regression test.
- Traced writable-storage assumptions against the pinned Source SDK revision. SDK source includes game-state file writes and replay-output directory creation (optional/build-dependent); do not remove the writable-root requirement without a deliberate, tested write-location redesign.
- The earlier Stage 13 CI run passed APK build verification but did not attach an artifact; the workflow now uploads a Debug test artifact for hands-on verification. Physical-device/provider acceptance remains open.


### 2026-10-23 — Stage 13 launch-state hardening follow-up

- On the live canonical head `5c2cff3bcea0e61dd9ce56b71a5e45bee509a774`, re-review found a launch-time fail-open edge: `LauncherActivity.startSource()` gated storage validation on `TREE_PATH_KEY` alone. If the URI existed but its mapped-path key was absent, the grant/scope check was skipped.
- Stage 13 follow-up changes validation to run whenever either half of the stored tree-selection record exists; host-JVM coverage exercises empty, URI-only and path-only preference state, and the Stage 13 verifier checks that launch code uses the shared predicate.
- Hardened repeat selection so an existing full persisted grant succeeds without relying on duplicate-take behavior, and a failed attempt to reselect the currently active URI does not unnecessarily release that grant.
- Device acceptance is still open; successful CI build and artifact upload do not prove the app runs correctly on a physical device. Download, install and test it on target hardware.


### 2026-10-24 — Stage 13 negative-path storage grant regression
- Extended `Stage13StorageAccessTestSuite` to exercise several failed replacement choices after a valid folder is active: missing write permission, selecting the game folder itself, and a cloud/virtual provider. The original URI/path and read-write grant must remain intact; rejected replacement URIs must not become persisted grants.
- Added clear-selection coverage proving cleanup removes the selected and orphaned external-storage document-tree grants but preserves an unrelated provider's persisted grant.
- Added Stage 13 static CI assertions that require both regression scenarios to remain wired into the suite.
- Added an explicit `content://` scheme check before filesystem mapping and in volume diagnostics, with a JVM regression for an HTTPS URI that has a spoofed external-storage authority. This prevents non-content URI shapes from being treated as local storage.
- Canonical branch at start of this follow-up: `android-fixes` @ `5f29a19b7ecd106a0299f8afaebdecbde1b3a0ff`; latest recorded canonical CI at that point: [39866755223](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39866755223) passed, but the license-clearance gate withheld downloadable artifacts. Physical storage/provider acceptance is still required and is not claimed from JVM/static tests.


## 7. Stage 17 resume point — 2026-10-24

- Canonical repository/branch: `majd7772233-a11y/Source-Engine`, `android-fixes`.
- Stage 17 baseline was `d372d7b6a0e575f8557c743d3aaec0a950e673fd`. Latest branch head is `cb43c464b16c5fd840532b801d3a1d9b9e2f3648`; canonical workflow [40132689538](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/40132689538) passed source/JVM/workflow validation, native build and APK verification. Debug test artifact [12575533251](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/40132689538/artifacts/12575533251) was uploaded and expires 2026-11-07; it is not a signed/public release or rights clearance.
- Stage 13 storage source/JVM implementation, Stage 14 scan source/JVM implementation, Stage 15 input-state source/JVM implementation and Stage 16 launcher/localization/accessibility source work have coverage. Physical provider, gameplay, TalkBack, rendering, audio and performance acceptance remains deferred to the consolidated Stage 18 window. Do not infer device acceptance from static/JVM/CI success.
- Stage 17 source implementation and canonical CI verification are complete. **Next milestone: Stage 18 — consolidated device/runtime acceptance**, after Stage 19's provenance/build prerequisites make the candidate appropriate to test. Stage 13's real provider/device behavior remains unverified; do not mark it fully accepted until the consolidated Stage 18 test records the result.
- **Current milestone: Stage 18 is next, gated on Stage 19 prerequisites.**
- Stage 17 changes separate environment parsing into pure Java with negative-path tests, remove unused version-marker/update-service remnants rather than implying a functioning updater, create a source-to-effect control inventory and add the Stage 17 static verifier to canonical CI.
- Latest CI is green for source/build verification, but Stage 19 rights/provenance review remains blocked. A debug test artifact exists for controlled testing only; do not treat it as clearance or a public release. Stage 18 device acceptance has not run.
- Stage 12 stays the permanent feedback/defect-correction lane. **Final Acceptance is unnumbered**, after numbered implementation, user/device acceptance and required review.

### Historical Stage 16 source implementation and canonical CI verification

- Stage 16 adds localized game-card TalkBack summaries, API-guarded semantic headings, scan status announcements, advanced-panel focus transfer/return and open/close announcements, and 48dp minimum dimensions for dynamically styled action buttons.
- LiquidGlassTheme composes translucent base and sheen layers with rounded specular edges and keeps the existing ambient color lighting, focused/pressed/disabled states, and persisted motion-off setting. It intentionally avoids requiring RenderEffect/blur, preserving the legacy API fallback; actual rendering quality and performance remain device-acceptance items.
- About content is more readable and game-card accessibility labels include status, explanation and folder. String-key and placeholder parity remain CI enforced in English/Arabic.
- Canonical workflow [40125765154](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/40125765154) subsequently passed the Stage 16 verifier and full source/JVM/native/APK verification. Physical TalkBack, RTL/font-scale, visual, motion and thermal checks remain in Stage 18; no device acceptance is implied.


- Stage 16 follow-up: advanced-settings Back now collapses the panel and restores focus before normal activity Back behavior. The glass overlay now draws a soft linear highlight, radial color bleed and a rounded specular edge within the surface; these are API-17-compatible and do not require RenderEffect. The verifier explicitly guards both behaviors.

- Acceptance correction superseded by canonical run [40125463160](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/40125463160) on commit `c5c556a6b4072690885d2dffe25de547af07da54`: the Stage 16 verifier and full workflow passed on that source head. Physical TalkBack, RTL/font-scale, visual, motion and thermal checks remain in Stage 18; CI does not establish device acceptance.


### 2026-10-24 — Stage 13 storage-selection durability CI result

- Canonical Android workflow [40125463160](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/40125463160) passed on `c5c556a6b4072690885d2dffe25de547af07da54`.
- All Stage 0–16 source verifiers passed. Host-JVM regression tests passed 80 assertions; Stage 13 storage-specific tests passed 101 assertions, including both injected preference-commit failure paths; Stage 14 passed 32 assertions and Stage 15 touch-input-state tests passed 18 assertions. Workflow validation passed.
- The CI verification build succeeded. APK verification passed for `com.magd.source.engine`, `armeabi-v7a`, 35 ELF libraries, 56,598,623 bytes, SHA-256 `ff64726e5ffbd3008911d12be3c6070711bfb204aaf4a69225125d5d029dc7e4`.
- The license/source-provenance gate explicitly returned `RELEASE BLOCKED`; no APK artifact was uploaded and signing/release steps were skipped. This remains fail-closed by design.
- Physical Android picker/provider testing remains not run and deferred to the consolidated Stage 18 acceptance window. Stage 13 is therefore not complete yet.


### Stage 13 resumed — typed preference corruption guard (2026-10-24)

- Canonical branch was rechecked before editing; Stage 13 storage code already covered persistable read/write grants, external-storage-only path mapping, mount/access checks, traversal rejection, scoped cleanup and failed preference commits.
- Added a fail-closed guard for corrupt SharedPreferences entries. Android `getString()` may throw ClassCastException when a preference key holds another type; storage URI/path reads now handle that case and key-presence checks ensure the damaged record still enters validation instead of bypassing it.
- New JVM regressions simulate Android's actual wrong-type getter behavior and verify corrupt URI+path records and URI+wrong-type-path records are cleared and their stale external-storage tree grants released.
- Stage 13 remains open for the physical-device SAF/provider and removable-volume matrix; these source/JVM checks are not real-device acceptance.
