# Source Engine for Android

An Android launcher and native-runtime integration for compatible Source-engine games. This repository is **not** a complete game-content distribution: users must supply game files they are legally entitled to use. Installing the APK does not install Half-Life 2, Portal, VPK files, or other proprietary game assets.

## Project status

- Canonical branch: `android-fixes`.
- Android application ID: `com.magd.source.engine`.
- Build system: legacy Android Ant/ADT project targeting Android API 29; minimum declared API 17.
- Current native build output is for **ARMv7 / `armeabi-v7a`**. Do not assume 64-bit ABI support.
- A clean GitHub Actions build produced `bin/srceng-debug.apk` and uploaded the `source-engine-android-debug` artifact in [run 38938001867](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/38938001867). The artifact is 54,488,439 bytes (SHA-256 `0ed0e8919ef6209114eecd35dd15b3abbcca47588570d7dcb11a782a3f557e6b`).
- Stage 3 CI run [38981504284](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/38981504284) built and verified the game-library APK. Stage 4 CI run [38982919788](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/38982919788) passed storage/path checks. Stage 5 CI run [39070653298](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39070653298) passed launcher UX checks and built the APK: 56,548,121 bytes, SHA-256 `22f26e01e5ffaa26718bc525bd7457126e6c320bdc1bc72e93a16e8a3741c1e9`. Download the [Stage 5 workflow artifact](https://github.com/majd7772233-a11y/Source-Engine/actions/runs/39070653298/artifacts/12115152142) (expires 2026-10-31). CI verifies packaging, application ID, and native payload; it does **not** establish that every game works or that storage behavior has been validated on a physical device.

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

## Repository map

- `src/` — Java launcher, bounded game discovery/library, SAF-aware storage picker/path bridge, update classes, Valve startup glue, and SDL Android integration.
- `res/` — Android resources.
- `libs/armeabi-v7a/` — native libraries packaged for ARMv7.
- `jni/src/tierhook/` — tierhook native build.
- `build.xml`, `project.properties`, `build.sh` — legacy Ant build configuration and local entry point.
- `scripts/workflow.sh` — full native-library and APK build.
- `scripts/verify-stage0.sh` — source/manifest/package/shell preflight checks.
- `scripts/verify-stage1.sh` — build-pipeline static integrity checks.
- `scripts/verify-stage4.sh` — SAF grant, removable-media, path-normalization, and launcher hand-off checks.
- `scripts/verify-stage5.sh` — library-first launcher, advanced settings, details, accessibility, and progress-state checks.
- `scripts/verify-apk.sh` — APK package, native ABI/ELF/dependency, archive, and checksum verification.
- `scripts/dependencies.lock` — immutable full commit SHAs for the legacy NDK, Android SDK, and Source SDK repositories.
- `scripts/fetch-build-deps.sh` — fetches only the locked commits and checks out pinned Source SDK submodules.
- `build-reports/` — generated build metadata and SHA-256 checksum report (not committed).
- `.github/workflows/build.yml` — CI build and debug APK artifact upload.
- `ROADMAP.md` — staged implementation plan and acceptance criteria.
- `GPT.md` — persistent engineering notes and execution rules.

## Known limitations and next work

- The project still uses the legacy Ant/Android SDK build system and an old NDK toolchain.
- The project still depends on legacy external SDK/NDK repositories. Their exact source revisions are pinned, but the legacy toolchain remains old and no physical-device/game-runtime validation has been performed.
- A successful APK build is not proof of game compatibility, storage behavior, performance, or runtime stability.
- The launcher now presents a library-first home with a prominent folder picker, first-run guidance, per-game details/readiness, scan and launch progress, collapsed advanced settings, and Arabic launcher/library strings. Registered game folders can be selected, launched, removed, and rescanned; long paths/names and non-ready entries are handled explicitly. Stage 5 CI acceptance passed, but its first-run, navigation, and actual game-launch behavior still require physical-device validation. The launcher also scans registered folders for game/mod candidates, shows metadata/assets/ABI/native-runtime readiness, and persists the library and last-launch times. Discovery uses filesystem heuristics and does not prove game/mod compatibility. Stage 4 adds SAF folder selection and persisted URI grants for local storage providers that expose safe real paths to the native engine; generic cloud providers, disconnected volumes, and non-writable roots are rejected. Device-level SAF/removable-media acceptance remains pending.
- Startup validates the selected game folder and `gameinfo.txt`, forwards current launch arguments/environment explicitly, and reports missing runtime data with actionable errors. Stage 4 adds SAF tree selection and persisted-grant checks; the native engine still requires a real filesystem path, so only readable/writable local external-storage-provider paths are accepted. Generic cloud/document providers and inaccessible/read-only/disconnected volumes are rejected. This code still needs physical-device validation.
- Use the roadmap's acceptance criteria before advertising support for a game, ABI, input mode, or online feature.
