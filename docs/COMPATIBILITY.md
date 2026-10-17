# Graphics, Audio, Performance and Device Compatibility

This matrix separates facts established by repository/CI inspection from runtime behavior that requires a physical device. Never interpret a system-reported feature as proof that a specific game renders or plays sound correctly.

## Known compatibility baseline

| Area | Verified fact | Limit |
|---|---|---|
| Android | Manifest declares minimum API 17 and target API 24 | Device-specific behavior is not covered by source checks |
| Graphics API | Manifest declares OpenGL ES 2.0 as a required feature | The launcher diagnostic reads Android's advertised feature level; it does not create an EGL context or query actual GL extensions |
| Native ABI | Packaged native runtime is ARMv7 (armeabi-v7a) | No ARM64/x86 runtime is verified; a device must support 32-bit ARM native code |
| Native payload | CI's APK verifier checks libtierhook.so, libmain.so, libRegal.so, libclient.so, libserver.so, ELF architecture and unresolved non-system dependencies | Presence does not prove that every game starts |
| Graphics translation | The package includes Regal and the existing native runtime/GL translation path | GL4ES/Regal options and game-specific renderer behavior must be tested in the game; no new renderer toggle is exposed without verified native support |
| Resolution/scaling | SDL creates the native game surface using Android display/surface configuration | Launcher display metrics are informational, not a resolution-setting control; in-game options and command-line arguments depend on each game |
| Audio | Android audio output enumeration is reported on API 23+ | A listed output does not prove audible game playback, latency, routing, or microphone behavior |
| Memory | The diagnostic captures current Android available memory and low-memory status | It is a single snapshot, not a game peak-memory measurement |
| Thermal/performance | No physical-device measurements are committed yet | Startup timing, frame pacing, sustained thermal performance, and crash rates remain unverified |

## Launcher diagnostics

Open Advanced settings → Device compatibility diagnostics. The report records manufacturer/model, Android release/API, reported ABI, display dimensions/density/refresh rate, system-advertised GLES level, current available-memory snapshot, audio output count where supported, and presence of required native libraries. Unsupported API calls are guarded for the declared Android 17 minimum.

The report explicitly disclaims actual GL-context validation and game benchmarking. Graphics/audio settings are not duplicated as fake switches: existing command-line arguments and environment overrides remain available as advanced options. Only expose additional settings after tracing their native consumer and verifying their effects.

## Device test matrix — evidence still required

| Test device | Android/API | ABI | GLES/renderer | Game/content | Startup | Frame pacing | Memory/thermal | Audio/routing | Crash/log outcome |
|---|---|---|---|---|---|---|---|---|---|
| Physical device A | Not tested | Not tested | Not tested | Not tested | Pending | Pending | Pending | Pending | Pending |
| Physical device B (different Android/API or GPU) | Not tested | Not tested | Not tested | Not tested | Pending | Pending | Pending | Pending | Pending |

Do not replace Not tested with assumed values. To complete this matrix, install the exact CI APK on at least two representative compatible ARMv7 devices, record the diagnostics report, launch a legally owned test game, repeat cold and warm launches, play for a sustained period while recording frame pacing/thermal behavior, check audio routing, and capture Android logs for startup/native/GLES/audio failures. Record game/version and device/API/GPU details with every result.

## Performance measurement protocol

1. Record device model, Android/API, GPU/driver if discoverable, game build/content, APK SHA-256, and power/thermal starting state.
2. Perform three cold starts and five warm starts; record each elapsed time separately rather than publishing an unsupported average.
3. Measure frame pacing with Android GPU/Frame timing tools or a reliable in-game profiler; note scene, duration, resolution, and renderer.
4. Record available memory before launch, after loading, and after a 10-minute sustained session; capture Android low-memory or process-kill events.
5. Record thermal status/temperature where the device exposes it, plus any throttling or crash log.
6. Verify audible output on speaker and headphones/Bluetooth where available; test pause/resume and route changes.
7. Compare a baseline against one change at a time. Optimize only repeatable bottlenecks and rerun the same measurements.

CI can validate Java/API guards, resources, static wiring, native compilation, packaging, ABI and dependencies. It cannot substitute for physical GPU/audio/thermal measurements.
