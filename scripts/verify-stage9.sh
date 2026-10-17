#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
bash -n scripts/verify-stage9.sh
python3 - "$ROOT_DIR" <<'PY'
import pathlib, sys, xml.etree.ElementTree as ET
root = pathlib.Path(sys.argv[1])
launcher = (root / "src/me/nillerusr/LauncherActivity.java").read_text(encoding="utf-8")
manifest = ET.parse(root / "AndroidManifest.xml").getroot()
workflow = (root / ".github/workflows/build.yml").read_text(encoding="utf-8")
strings_en = ET.parse(root / "res/values/strings.xml").getroot()
strings_ar = ET.parse(root / "res/values-ar/strings.xml").getroot()
en = {node.attrib.get("name"): node.text or "" for node in strings_en}
ar = {node.attrib.get("name"): node.text or "" for node in strings_ar}
android = "{http://schemas.android.com/apk/res/android}"
permissions = {node.attrib.get(android + "name") for node in manifest.findall("uses-permission")}
keys = {
    "srceng_online_features", "srceng_online_title", "srceng_online_network_connected",
    "srceng_online_network_disconnected", "srceng_online_server_browser_unavailable",
    "srceng_online_multiplayer_note"
}
checks = {
    "internet permission is declared": "android.permission.INTERNET" in permissions,
    "network-state permission is declared": "android.permission.ACCESS_NETWORK_STATE" in permissions,
    "online status action is wired into advanced settings": "showOnlineFeaturesStatus();" in launcher and "srceng_online_features" in launcher,
    "connectivity check uses Android network state rather than claiming server reachability": "getActiveNetworkInfo()" in launcher and "active.isConnected()" in launcher,
    "connected state explicitly does not imply a reachable game server": "does not confirm that a game server is reachable" in en.get("srceng_online_network_connected", ""),
    "server browser explicitly unavailable with concrete reason": "no verified native server discovery/list/join bridge" in en.get("srceng_online_server_browser_unavailable", ""),
    "multiplayer is distinguished from a hosted backend": "does not host a game server" in en.get("srceng_online_multiplayer_note", ""),
    "no fake server list or fabricated join endpoint is introduced": "showOnlineFeaturesStatus" in launcher and "https://" not in launcher[launcher.find("private void showOnlineFeaturesStatus"):launcher.find("private void showCompatibilityDiagnostics")],
    "English online resources are complete": keys <= set(en),
    "Arabic online resources are complete": keys <= set(ar),
    "Arabic server-browser limitation is explicit": "غير متاح" in ar.get("srceng_online_server_browser_unavailable", "") and "لا يملك المشغّل" in ar.get("srceng_online_server_browser_unavailable", ""),
    "CI runs Stage 9 verifier": "bash scripts/verify-stage9.sh" in workflow,
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("PASS" if ok else "FAIL") + ": " + name)
if failed:
    raise SystemExit("Stage 9 static verification failed: " + ", ".join(failed))
print("Stage 9 static checks passed: %d online-capability assertions." % len(checks))
PY
