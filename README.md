# Source Engine for Android

An Android launcher and native-runtime integration for compatible Source-engine games. This repository is **not** a complete game-content distribution: users must supply game files they are legally entitled to use. Installing the APK does not install Half-Life 2, Portal, VPK files, or other proprietary game assets.

## Project status

- Canonical branch: `android-fixes`.
- Android application ID: `com.magd.source.engine`.
- Build system: legacy Android Ant/ADT project targeting Android API 29; minimum declared API 17.
- Current native build output is for **ARMv7 / `armeabi-v7a`**. Do not assume 64-bit ABI support.
- A clean GitHub Actions build produced `bin/srceng-debug.apk` and uploaded the `source-engine-android-debug` artifact in [run 38938001867](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/38938001867). The artifact is 54,488,439 bytes (SHA-256 `0ed0e8919ef6209114eecd35dd15b3abbcca47588570d7dcb11a782a3f557e6b`).
- Stage 3 CI run [38981504284](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/38981504284) built and verified the game-library APK. Stage 4 CI run [38982919788](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/38982919788) passed storage/path checks. Stage 5 CI run [39070653298](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39070653298) passed launcher UX checks and built the APK; a scan-recovery follow-up now keeps the last committed library state when a scan throws instead of saving partial results: 56,548,121 bytes, SHA-256 `22f26e01e5ffaa26718bc525bd7457126e6c320bdc1bc72e93a16e8a3741c1e9`. Download the [Stage 5 workflow artifact](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39070653298/artifacts/12115152142) (expires 2026-10-31). CI verifies packaging, application ID, and native payload; it does **not** establish that every game works or that storage behavior has been validated on a physical device.
- Stage 5 scan-failure recovery was merged in [PR #15](https://github.com/majd7772233-a11y/Source-Engine/pull/15) as [`418eb63cca6da8dd21a3549b99c7a2b91132008b`](https://github.com/majd7772233-a11y/Source-Engine/commit/418eb63cca6da8dd21a3549b99c7a2b91132008b). PR CI [39473018430](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39473018430) and post-merge CI [39473351826](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39473351826) passed; the Stage 5 verifier now checks 17 assertions. Latest canonical debug APK: 56,572,263 bytes, SHA-256 `9000c986fb4ff55249ba793614c8d5be8cda0809f8abd3f5d13a5ff82f019b91`; [artifact 12287199143](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39473351826/artifacts/12287199143) expires 2026-11-03. CI does not substitute for physical-device/gameplay validation.
- Stage 6 Liquid Glass was merged as [commit `239fd915f307b43aa63867722b42e1204c332217`](https://github.com/majd7772233-a11y/Source-Engine/commit/239fd915f307b43aa63867722b42e1204c332217). Post-merge CI run [39072023024](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39072023024) passed 16 visual-system assertions, native compilation, Java/Ant packaging, APK identity/ABI checks, and artifact upload. APK: 56,552,089 bytes; SHA-256 `59ad6cdd9b3f262c356e08d304759700c883cc80a38b2a0e89ef8c4d5dec96e7`; [artifact 12114973248](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39072023024/artifacts/12114973248). Real-device appearance and low-end performance remain unverified.
- Stage 9 network/server-browser audit adds an **Online features and server browser status** panel under Advanced settings. It reports Android's current connectivity state only and explicitly marks in-app server browsing unavailable because no verified native discovery/list/join bridge is wired. It does not invent servers or a hosted backend. See [docs/ONLINE-FEATURES.md](docs/ONLINE-FEATURES.md); static coverage is in `scripts/verify-stage9.sh`. PR [#12](https://github.com/majd7772233-a11y/Source-Engine/pull/12) merged to `android-fixes` as `b9660ab08752d83c4ecd5f387298636d3d214e05`. Final pre-merge candidate workflow [39075085755](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39075085755) passed Stage 0–9 checks, native compilation, Java/Ant packaging, and APK verification (35 ARMv7 ELF libraries). APK: 56,571,000 bytes; SHA-256 `56cceb193f2b562bf1f5bb32c9c68da8e85f25b72ffa8d8647f81103af5a516f`; [artifact 12116274496](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39075085755/artifacts/12116274496), expires 2026-10-31. No real server query or multiplayer session is claimed.
- Stage 7 touch controls were merged as [commit `b46a6ae5fdff9b1c3328fe10b50dd1701f344c0a`](https://github.com/majd7772233-a11y/Source-Engine/commit/b46a6ae5fdff9b1c3328fe10b50dd1701f344c0a). Post-merge CI [run 39073356292](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39073356292) passed all Stage 0–7 checks, native compilation, Java/Ant packaging, APK identity/ABI/dependency checks, and artifact upload. Canonical APK: 56,562,047 bytes; SHA-256 `c78883743d21c5cf56372553096a59c3c148a5f9057dfd9a21eadfe6e3b8de69`; [artifact 12115514936](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39073356292/artifacts/12115514936), expires 2026-10-31. The overlay maps movement to WASD, fire to SDL mouse input, and jump/use/reload to keyboard actions. Real-game input behavior remains unverified.

- Stage 11 automated tests and CI quality gates are merged as [PR #16](https://github.com/majd7772233-a11y/Source-Engine/pull/16) in commit [`dec2b5f6b01c83cdfc58f8897578685680670b96`](https://github.com/majd7772233-a11y/Source-Engine/commit/dec2b5f6b01c83cdfc58f8897578685680670b96). PR workflow [39552539276](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39552539276) and post-merge workflow [39552785558](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39552785558) passed 73 JVM assertions, all Stage 0–12 verifiers, ShellCheck, YAML/actionlint validation, native compilation and APK verification. Current canonical debug APK: 56,573,086 bytes, SHA-256 `f25673a0132c7a4b6b0e73ed0a48d27e32f914431521817cfd31a37a94271083`; [artifact 12323743499](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39552785558/artifacts/12323743499) expires 2026-11-04. This validates CI/build, not physical-device/gameplay behavior.

## Build

### Option A — Package the checked-in native libraries

Requirements:
- Java 8
- Apache Ant
- An Android SDK compatible with this legacy Ant project, including `tools/ant/build.xml` and the API/build tools required by `project.properties`

Set `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) to that SDK, then run:

```bash
bash build.sh
```

The script generates `res/values/build_info.xml`, runs `ant clean debug`, and verifies that `bin/srceng-debug.apk` exists and is non-empty. `bash scripts/build-java.sh` is a compatibility entry point for the same build.

### Option B — Rebuild native libraries and package the APK

The full native workflow also requires the legacy NDK toolchain in `ndk-binaries/`, the compatible Android SDK in `android-sdk/`, and the Source SDK checkout/submodules in `srcsdk/`. With these checkouts prepared, run:

```bash
bash scripts/workflow.sh
```

This builds the tierhook, engine, GL4ES/Regal, client-wrapper, and server-wrapper libraries before invoking Ant. Each required native output is deleted before rebuilding so stale files cannot mask a failure. GitHub Actions records exact dependency revisions and uses the pinned Source SDK commit; the exact revisions are locked in `scripts/dependencies.lock` and fetched by `scripts/fetch-build-deps.sh`; nested Source SDK submodules are pinned by the parent commit's gitlinks.

## Release signing (opt-in)

Normal branch and pull-request builds upload the separate `source-engine-android-debug` artifact. A signed release is produced only by pushing a semantic-version tag such as `v1.12.3`, or by manually running **Actions → Android build** with `sign_release=true` and `release_version=1.12.3`. The version must be `MAJOR.MINOR.PATCH`; minor and patch components must each be below 1000. The release workflow derives Android `versionCode` as `major × 1,000,000 + minor × 1,000 + patch`, builds the Ant `release` variant (not the debug variant), aligns and signs it, verifies the signature/package/version, and uploads a separate `source-engine-android-release-VERSION` artifact with SHA-256 and signing-certificate reports. It does not publish a GitHub Release automatically.

Configure these exact GitHub Actions repository secrets before starting a signed build:

- `MY_KEYSTORE_JKS`: **Base64-encoded bytes** of your own release keystore file. On Windows PowerShell, generate the value with `[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\path\release-key.jks"))`. On Linux, use `base64 -w0 release-key.jks`. Paste the resulting Base64 text into the secret; do not upload or commit the keystore file.
- `KEYSTORE_PASSWORD`: the keystore password.
- `KEY_ALIAS`: the key alias inside the keystore.
- `KEY_PASSWORD`: the private key password.

The workflow passes passwords to `apksigner` through environment-variable references, turns off shell tracing for the signing step, writes the decoded keystore only to a permission-restricted temporary file, and removes that file on exit. Secret values are not printed by the workflow. If a required secret is missing or invalid, the signed-release step fails rather than claiming a signed artifact exists.

## Repository map

- `src/` — Java launcher, bounded game discovery/library, SAF-aware storage picker/path bridge, configurable per-game `TouchControlsOverlay`, update classes, Valve startup glue, and SDL Android integration.
- `res/` — Android resources.
- `libs/armeabi-v7a/` — native libraries packaged for ARMv7.
- `jni/src/tierhook/` — tierhook native build.
- `build.xml`, `project.properties`, `build.sh` — legacy Ant build configuration and local entry point.
- `scripts/workflow.sh` — full native-library and APK build.
- `scripts/verify-stage0.sh` — source/manifest/package/shell preflight checks.
- `scripts/verify-stage1.sh` — build-pipeline static integrity checks.
- `scripts/verify-stage4.sh` — SAF grant, removable-media, path-normalization, and launcher hand-off checks.
- `scripts/verify-stage5.sh` — library-first launcher, advanced settings, details, accessibility, and progress-state checks.
- `scripts/verify-stage8.sh` — GLES/ABI/display/memory/audio/native-library diagnostics and localization/CI wiring checks.
- `docs/COMPATIBILITY.md` — graphics/audio compatibility facts, known limitations, device test matrix, and performance measurement protocol.\n- `docs/ONLINE-FEATURES.md` — verified online capability status, server-browser limitation, and requirements before implementing a real browser.\n- `scripts/verify-stage9.sh` — online permission, status UI, disclosure/localization, and CI wiring checks.
- `scripts/verify-stage8.sh` — compatibility diagnostics, API guards, localization, native-library reporting and CI wiring.
- `docs/COMPATIBILITY.md` — known graphics/audio compatibility facts, physical-device test matrix and measurement protocol.
- `scripts/verify-stage7.sh` — touch-control mapping, multi-pointer release, profile persistence, configuration, localization, and CI-wiring checks.
- `scripts/verify-apk.sh` — APK package, native ABI/ELF/dependency, archive, and checksum verification.
- `scripts/dependencies.lock` — immutable full commit SHAs for the legacy NDK, Android SDK, and Source SDK repositories.
- `scripts/fetch-build-deps.sh` — fetches only the locked commits and checks out pinned Source SDK submodules.
- `build-reports/` — generated build metadata and SHA-256 checksum report (not committed).
- `.github/workflows/build.yml` — CI build and debug APK artifact upload.
- `ROADMAP.md` — staged implementation plan and acceptance criteria.
- `GPT.md` — persistent engineering notes and execution rules.
- scripts/test-java.sh — reproducible host-JVM tests against selected production Java classes, with Android API test doubles.
- scripts/verify-stage11.sh — XML/resource/package/dependency and Stage 11 CI wiring checks.
- scripts/validate-workflow.py — YAML structure, canonical triggers, pinned actions, and artifact/quality-gate checks.
- docs/DEVICE-SMOKE-TEST.md — physical-device test plan; not a claim of tests already performed.
- Stage 11 adds a standalone JVM suite and CI quality gates for the production path-normalization/argument helpers, additive preference migration, game discovery/readiness/persistence, Android XML/resource/package integrity, dependency locks, shell lint and workflow validation. From a checkout with Java and PyYAML, run bash scripts/test-java.sh, bash scripts/verify-stage11.sh, and python3 scripts/validate-workflow.py. The full Actions workflow also runs ShellCheck and actionlint before native dependency fetching; physical-device results are not inferred from these checks.

## Known limitations and next work

- The project still uses the legacy Ant/Android SDK build system and an old NDK toolchain.
- The project still depends on legacy external SDK/NDK repositories. Their exact source revisions are pinned, but the legacy toolchain remains old and no physical-device/game-runtime validation has been performed.
- A successful APK build is not proof of game compatibility, storage behavior, performance, or runtime stability.
- The in-game touch overlay offers a digital WASD movement pad, fire, jump, use, and reload controls, with per-game presets, drag positioning, size, opacity, sensitivity threshold, hide/show, and reset. It emits conventional keyboard/mouse bindings rather than analog controller axes; mods with custom bindings may require rebinding. Actual gameplay input, multitouch, controller hot-plug, and pause/resume still need physical-device testing. The launcher now presents a library-first home with a prominent folder picker, first-run guidance, per-game details/readiness, scan and launch progress, collapsed advanced settings, and Arabic launcher/library strings. Registered game folders can be selected, launched, removed, and rescanned; long paths/names and non-ready entries are handled explicitly. Stage 5 CI acceptance passed, but its first-run, navigation, and actual game-launch behavior still require physical-device validation. The launcher also scans registered folders for game/mod candidates, shows metadata/assets/ABI/native-runtime readiness, and persists the library and last-launch times. Discovery uses filesystem heuristics and does not prove game/mod compatibility. Stage 4 adds SAF folder selection and persisted URI grants for local storage providers that expose safe real paths to the native engine; generic cloud providers, disconnected volumes, and non-writable roots are rejected. Device-level SAF/removable-media acceptance remains pending.
- Startup validates the selected game folder and `gameinfo.txt`, forwards current launch arguments/environment explicitly, and reports missing runtime data with actionable errors. Stage 4 adds SAF tree selection and persisted-grant checks; the native engine still requires a real filesystem path, so only readable/writable local external-storage-provider paths are accepted. Generic cloud/document providers and inaccessible/read-only/disconnected volumes are rejected. This code still needs physical-device validation.
- Use the roadmap's acceptance criteria before advertising support for a game, ABI, input mode, or online feature.


## Stage 10 reliability and security

The Stage 10 candidate tightens internal activity/service exposure, permission-denial recovery, launch path/argument/environment bounds, optional data-directory failure handling, and internal runtime-asset extraction. The bundled VPK is written and synced to a temporary file before replacement; a partial file is not accepted solely because it is non-empty. The legacy updater cannot open an arbitrary URL and automatic APK update notifications are disabled until signed release verification and installation are implemented. CI coverage is defined in `scripts/verify-stage10.sh`. PR [#13](https://github.com/majd7772233-a11y/Source-Engine/pull/13) merged as `c6c21d2e21de9829214214d40fd67ceba1e6e9f2`. Final candidate workflow [39077037357](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39077037357) passed Stage 0–10 checks (26 Stage 10 assertions), pinned native dependency fetch, native compilation, Java/Ant packaging, APK identity/ABI/dependency verification (35 ARMv7 ELF libraries), and artifact upload. APK: 56,572,260 bytes; SHA-256 `71745581d79f0f195f890b764a70d4d99aacdc9460068dbaebece1ffd750d3fa`; [artifact 12116564321](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39077037357/artifacts/12116564321), expires 2026-10-31. Do not interpret static checks or a successful build as physical-device/gameplay validation.
