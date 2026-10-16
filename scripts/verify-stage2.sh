#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
bash -n scripts/verify-stage2.sh
python3 - "$ROOT_DIR" <<'PY'
import pathlib, sys
import xml.etree.ElementTree as ET
root = pathlib.Path(sys.argv[1])
launcher = (root / "src/me/nillerusr/LauncherActivity.java").read_text(encoding="utf-8")
valve = (root / "src/com/valvesoftware/ValveActivity2.java").read_text(encoding="utf-8")
assets = (root / "src/me/nillerusr/ExtractAssets.java").read_text(encoding="utf-8")
sdl = (root / "src/org/libsdl/app/SDLActivity.java").read_text(encoding="utf-8")
manifest = ET.parse(root / "AndroidManifest.xml").getroot()
strings = ET.parse(root / "res/values/strings.xml").getroot()
checks = {
    "launcher forwards current path": 'putExtra("gamepath", gamepath)' in launcher,
    "launcher forwards current arguments": 'putExtra("argv", cmdArgs.getText().toString().trim())' in launcher,
    "launcher forwards current environment": 'putExtra("env", EnvEdit.getText().toString())' in launcher,
    "launcher guards repeated launch": "if (launchInProgress) return;" in launcher,
    "launcher validates path": "!root.isDirectory() || !root.canRead()" in launcher,
    "pre-23 permission API guarded": "if (Build.VERSION.SDK_INT < 23)" in sdl and "Before Android 6.0" in sdl,
    "empty permission callback guarded": "grantResults==null||grantResults.length==0" in sdl,
    "specific gameinfo validation": 'new File(selected,"gameinfo.txt")' in valve,
    "null-safe directory listing": "File[] fs=d.listFiles();if(fs==null)return false;" in valve,
    "game directory traversal rejected": 'n.equals("..")||n.contains("/")||n.contains("\\\\")' in valve,
    "native init errors stop startup": "nativeInitError=ValveActivity2.initNatives(this,intent)" in sdl and "if(nativeInitError!=null)" in sdl,
    "VPK extraction returns success": "public static boolean extractVPK(Context context, boolean force)" in assets,
    "VPK output synced before version": "os.getFD().sync()" in assets and "pakversion" in assets,
    "native env errors checked": "setenv failed for" in valve,
    "SDL state reset after failure": "Failed startup must not poison the next launch" in sdl,
    "SDL new intent handled": "protected void onNewIntent(Intent intent)" in sdl,
}
missing = [name for name, ok in checks.items() if not ok]
if missing:
    raise SystemExit("Stage 2 static checks failed: " + ", ".join(missing))
if manifest.attrib.get("package") != "com.magd.source.engine":
    raise SystemExit("Unexpected application ID.")
resource_names = {item.attrib.get("name") for item in strings}
for name in ("srceng_launcher_error_empty_path", "srceng_launcher_error_invalid_path",
             "srceng_launcher_error_start_failed", "srceng_launcher_error_permission_result"):
    if name not in resource_names:
        raise SystemExit("Missing startup diagnostic string: " + name)
# All resource references introduced by Stage 2 must resolve in the values file.
for name in resource_names:
    if not name:
        raise SystemExit("Unnamed string resource found.")
print("Stage 2 static checks passed: " + str(len(checks)) + " startup/lifecycle assertions.")
PY
