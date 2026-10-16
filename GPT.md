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
2. **A stage means all of it.** Complete every bullet and acceptance criterion in `ROADMAP.md`; do not return halfway through a stage or silently defer subparts.
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

- Active task: finish Stage 0 in `ROADMAP.md`. A curated integration commit has been created on `android-fixes` with `con-wrappers` and `test` as additional parents. Canonical application ID is now `com.magd.source.engine`; build scripts, workflow, ignore rules, and debug-keystore handling have been normalized. Do not call Stage 0 complete until a clean build/CI result and the remaining file-level checks are verified.
- Required outcome before Stage 1: one canonical integrated tree with its branch provenance understood, package references consistent, build instructions and workflow aligned, and every unresolved build blocker documented.
- Do not mark Stage 0 complete until its full acceptance criteria are checked. The initial inspection and curated merge are not a substitute for verifying every relevant call site and running the build. At the last check, GitHub Actions had zero runs for `android-fixes`.

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
- **Latest curated integration commit:** `95da41713d9df94f42a11cdd7d0e927fc9ee2c40`; subsequent roadmap log commit: `c9608269403a39146654fe59ed64f159df9cedd1`.
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
