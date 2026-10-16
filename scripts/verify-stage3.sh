#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
bash -n scripts/verify-stage3.sh
python3 - "$ROOT_DIR" <<'PY'
import pathlib, sys
import xml.etree.ElementTree as ET
root = pathlib.Path(sys.argv[1])
discovery = (root / "src/me/nillerusr/GameDiscovery.java").read_text(encoding="utf-8")
launcher = (root / "src/me/nillerusr/LauncherActivity.java").read_text(encoding="utf-8")
strings = ET.parse(root / "res/values/strings.xml").getroot()
workflow = (root / ".github/workflows/build.yml").read_text(encoding="utf-8")
checks = {
    "bounded scan depth": "MAX_DEPTH = 4" in discovery,
    "bounded scan count": "MAX_DIRECTORIES = 1200" in discovery,
    "canonical path cycle guard": "getCanonicalPath()" in discovery and "visited.add(canonical)" in discovery,
    "null-safe directory listing": "if (children == null) return;" in discovery,
    "permission-aware scan": "!directory.canRead()" in discovery,
    "case-tolerant gameinfo": 'equalsIgnoreCase(name)' in discovery and '"gameinfo.txt"' in discovery,
    "bounded metadata read": "MAX_GAMEINFO_BYTES" in discovery and "validateGameInfo(info)" in discovery,
    "data-driven title detection": "readTitle(info)" in discovery and '"game"' in discovery and '"title"' in discovery,
    "asset validation": "hasContentMarkers" in discovery and 'endsWith(".vpk")' in discovery and 'endsWith(".bsp")' in discovery,
    "device ABI validation": "supportsPackagedAbi()" in discovery and '"armeabi-v7a".equals(abi)' in discovery,
    "native payload validation": "hasNativeRuntime(context)" in discovery and "libtierhook.so" in discovery and "libclient.so" in discovery,
    "compatibility caveat": "srceng_library_detail_ready" in discovery,
    "persistent library paths": '"library_paths"' in discovery and "putStringSet(LIBRARY_PATHS" in discovery,
    "last launch persistence": "LAST_LAUNCHED_PREFIX" in discovery and "recordLaunch" in discovery,
    "rescan action": "srceng_library_rescan" in launcher and "scanLibrary(false, false)" in launcher,
    "current root scan": "scanLibrary(true, true)" in launcher,
    "remove and hide entry": "LIBRARY_HIDDEN" in launcher and "hidden.add(entry.path)" in launcher,
    "stale path cleanup": "GameDiscovery.inspectPath(this, path)" in launcher and "if (!livePaths.equals(paths))" in launcher,
    "non-ready launch disabled": "play.setEnabled(entry.launchable)" in launcher,
    "launch readiness enforced": "selectedEntry == null || !selectedEntry.launchable" in launcher,
    "last launch recorded": "GameDiscovery.recordLaunch(this, selectedEntry.path)" in launcher,
    "optional content distinction": "hasOptionalContent(children)" in discovery and "optionalContentAvailable" in discovery and "srceng_library_optional_found" in launcher,
    "accurate readiness summary": "srceng_library_count" in launcher,
    "CI invokes Stage 3 checks": "bash scripts/verify-stage3.sh" in workflow,
}
missing = [name for name, ok in checks.items() if not ok]
if missing:
    raise SystemExit("Stage 3 static checks failed: " + ", ".join(missing))
resource_names = {item.attrib.get("name") for item in strings}
required_resources = [
    "srceng_library_title", "srceng_library_scan_current", "srceng_library_rescan",
    "srceng_library_empty", "srceng_library_scan_invalid", "srceng_library_status_missing_info",
    "srceng_library_status_invalid_info", "srceng_library_status_missing_assets",
    "srceng_library_status_unsupported_abi", "srceng_library_status_native_missing",
    "srceng_library_status_ready", "srceng_library_unrecognized", "srceng_library_compatibility",
]
missing_resources = [name for name in required_resources if name not in resource_names]
if missing_resources:
    raise SystemExit("Missing Stage 3 strings: " + ", ".join(missing_resources))
print("Stage 3 static checks passed: " + str(len(checks)) + " discovery/library assertions.")
PY
