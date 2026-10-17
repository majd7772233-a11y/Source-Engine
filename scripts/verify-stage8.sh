#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
bash -n scripts/verify-stage8.sh
python3 - "$ROOT_DIR" <<'PY'
import pathlib, sys, xml.etree.ElementTree as ET
root = pathlib.Path(sys.argv[1])
launcher = (root / "src/me/nillerusr/LauncherActivity.java").read_text(encoding="utf-8")
strings_en = ET.parse(root / "res/values/strings.xml").getroot()
strings_ar = ET.parse(root / "res/values-ar/strings.xml").getroot()
workflow = (root / ".github/workflows/build.yml").read_text(encoding="utf-8")
manifest = ET.parse(root / "AndroidManifest.xml").getroot()
en = {item.attrib.get("name") for item in strings_en}
ar = {item.attrib.get("name") for item in strings_ar}
keys = {
    "srceng_compatibility_diagnostics", "srceng_compatibility_title",
    "srceng_compatibility_device", "srceng_compatibility_android",
    "srceng_compatibility_abi", "srceng_compatibility_display",
    "srceng_compatibility_refresh", "srceng_compatibility_gles",
    "srceng_compatibility_gles_unknown", "srceng_compatibility_memory",
    "srceng_compatibility_memory_low", "srceng_compatibility_memory_ok",
    "srceng_compatibility_audio_outputs", "srceng_compatibility_audio_unknown",
    "srceng_compatibility_native_ok", "srceng_compatibility_native_missing",
    "srceng_compatibility_limits"
}
checks = {
    "compatibility report is reachable from advanced settings": "showCompatibilityDiagnostics();" in launcher and "srceng_compatibility_diagnostics" in launcher,
    "report includes device and Android version": "Build.MANUFACTURER" in launcher and "Build.VERSION.SDK_INT" in launcher,
    "report includes display resolution and refresh rate": "getMetrics(metrics)" in launcher and "getRefreshRate()" in launcher,
    "report includes memory pressure snapshot": "getMemoryInfo(memory)" in launcher and "memory.lowMemory" in launcher,
    "GLES is clearly system-advertised rather than falsely probed": "reqGlEsVersion" in launcher and "srceng_compatibility_gles_unknown" in launcher,
    "audio output enumeration is API guarded": "Build.VERSION.SDK_INT >= 23" in launcher and "GET_DEVICES_OUTPUTS" in launcher,
    "native runtime dependencies checked": all(name in launcher for name in ("libtierhook.so", "libmain.so", "libRegal.so", "libclient.so", "libserver.so")),
    "diagnostic limitations are disclosed": "srceng_compatibility_limits" in launcher and "not an actual GL context probe" in strings_en.find("./string[@name='srceng_compatibility_limits']").text,
    "English diagnostic resources are complete": keys <= en,
    "Arabic diagnostic resources are complete": keys <= ar,
    "manifest retains OpenGL ES 2 minimum": any(node.tag == "uses-feature" and node.attrib.get("{http://schemas.android.com/apk/res/android}glEsVersion") == "0x00020000" for node in manifest),
    "CI invokes Stage 8 verifier": "bash scripts/verify-stage8.sh" in workflow
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("PASS" if ok else "FAIL") + ": " + name)
if failed:
    raise SystemExit("Stage 8 static verification failed: " + ", ".join(failed))
print("Stage 8 static checks passed: %d compatibility assertions." % len(checks))
PY
