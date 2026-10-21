#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
for shell_script in build.sh scripts/*.sh; do bash -n "$shell_script"; done
python3 - "$ROOT_DIR" <<'PY'
import pathlib
import sys
import xml.etree.ElementTree as ET

root = pathlib.Path(sys.argv[1])
ns = "{http://schemas.android.com/apk/res/android}"
manifest = ET.parse(root / "AndroidManifest.xml").getroot()
application = manifest.find("application")
if application is None:
    raise SystemExit("FAIL: application element is missing")

def source(path):
    return (root / path).read_text(encoding="utf-8")

launcher = source("src/me/nillerusr/LauncherActivity.java")
workflow_script = source("scripts/workflow.sh")
sdl = source("src/org/libsdl/app/SDLActivity.java")
extract = source("src/me/nillerusr/ExtractAssets.java")
valve = source("src/com/valvesoftware/ValveActivity2.java")
updater = source("src/me/nillerusr/UpdateSystem.java")
service = source("src/me/nillerusr/UpdateService.java")
discovery = source("src/me/nillerusr/GameDiscovery.java")
workflow = source(".github/workflows/build.yml")
environment_method = valve[valve.index("private static String applyUserEnvironment"):valve.index("public static String initNatives")]

components = {c.get(ns + "name"): c for c in list(application.findall("activity")) + list(application.findall("service"))}
checks = {
    "manifest does not force a debuggable production application": application.get(ns + "debuggable", "false").lower() != "true",
    "launcher is explicitly exported as the entry point": components.get("me.nillerusr.LauncherActivity") is not None and components["me.nillerusr.LauncherActivity"].get(ns + "exported") == "true",
    "directory picker is not externally callable": components.get("me.nillerusr.DirchActivity") is not None and components["me.nillerusr.DirchActivity"].get(ns + "exported") == "false",
    "native SDL activity is not externally callable": components.get("org.libsdl.app.SDLActivity") is not None and components["org.libsdl.app.SDLActivity"].get(ns + "exported") == "false",
    "legacy updater service is not externally callable": components.get("me.nillerusr.UpdateService") is not None and components["me.nillerusr.UpdateService"].get(ns + "exported") == "false",
    "launcher runtime permission method handles pre-23 and null/empty inputs": "Build.VERSION.SDK_INT < 23 || permissions == null || permissions.length == 0" in launcher,
    "launcher permission denial resets button and progress UI": "launcherProgress.setVisibility(View.GONE)" in launcher and "launchButton.setText(R.string.srceng_launcher_launch)" in launcher,
    "SDL runtime permission method guards API level and empty arrays": "Build.VERSION.SDK_INT < 23 || permissions == null || permissions.length == 0" in sdl,
    "SDL empty permission results fail safely": "grantResults == null || grantResults.length == 0" in sdl and "srceng_launcher_error_permission_result" in sdl,
    "startup validation does not log full user-supplied path/error text": 'Log.e(TAG, "Startup validation failed; details are shown in the launcher.")' in valve and 'error.replace(\'\\n\', \' \')' not in valve,
    "runtime asset extraction has no shell chmod or command interpolation": "Runtime.getRuntime().exec" not in extract and "chmod(" not in extract,
    "launch arguments and selected paths have explicit size and NUL guards": "arguments.length() > 4096" in valve and "path.length() > 4096" in valve and "customVpk.length() > 8192" in valve,
    "environment overrides have per-entry and count limits": "rawEnvironment.length() > 8192" in valve and "entries.length > 64" in valve and "item.length() > 2048" in valve,
    "optional data directory creation and write access are checked": "!directory.mkdirs()" in launcher and "!directory.canWrite()" in launcher and "gameRootForEngine.isEmpty()" in valve,
    "native startup stops when required environment values fail": "if (!setRequiredEnvironment(" in valve and "return \"The native runtime rejected a required startup setting." in valve,
    "native build metadata is passed as arguments and XML escaped": "escape(commit)" in workflow_script and "escape(branch)" in workflow_script and ' "$COMMIT" "$BUILD_BRANCH" ' in workflow_script,
    "runtime asset is written to a temporary file before replacement": 'VPK_NAME + TEMP_SUFFIX' in extract and "temporary.renameTo(destination)" in extract,
    "runtime asset output is flushed and synced before installation": "output.flush()" in extract and "output.getFD().sync()" in extract,
    "runtime asset cache validates saved byte count rather than trusting nonempty files": 'PAK_BYTES_KEY' in extract and "destination.length() == expectedBytes" in extract,
    "failed extraction cleans up incomplete temporary output": "!installed && temporary.exists() && !temporary.delete()" in extract,
    "updater pins HTTPS deployment host and validates branch": "https://raw.githubusercontent.com/nillerusr/srceng-deploy/" in updater and "SAFE_BRANCH" in updater and "raw.githubusercontent.com" in updater,
    "updater has finite network timeouts and bounded response": "setConnectTimeout(8000)" in updater and "setReadTimeout(8000)" in updater and "value.length() + line.length() > 128" in updater,
    "updater rejects redirects and validates version as a commit hash": "setInstanceFollowRedirects(false)" in updater and "COMMIT_SHA.matcher(remote).matches()" in updater,
    "legacy updater cannot launch an arbitrary URL or install an APK": "Legacy update notification ignored" in service and "Uri.parse" not in service and "startActivity" not in service,
    "storage permission denial offers a supported Android settings recovery": "showStoragePermissionHelp();" in launcher and "ACTION_APPLICATION_DETAILS_SETTINGS" in launcher and "srceng_storage_permission_required" in launcher,
    "permission recovery strings are present in both locales": all(name in {item.get("name") for item in ET.parse(root / "res/values/strings.xml").getroot()} and name in {item.get("name") for item in ET.parse(root / "res/values-ar/strings.xml").getroot()} for name in ("srceng_storage_permission_required", "srceng_storage_open_settings", "srceng_storage_settings_unavailable")),
    "launcher startup asks only for required shared-storage permission": 'applyPermissions( new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE }, REQUEST_PERMISSIONS );' in launcher and 'Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO' not in launcher,
    "SDL native initialization is not blocked by optional microphone access": 'applyPermissions( new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE }, REQUEST_PERMISSIONS );' in sdl and '&& checkSelfPermission(Manifest.permission.RECORD_AUDIO)' not in sdl,
    "optional SDL permission results are returned to native code": "nativePermissionResult(requestCode, granted)" in sdl,
    "environment overrides are validated before any process mutation": environment_method.index("String[] values = new String[entries.length]") < environment_method.index("setRequiredEnvironment(names[i], values[i])"),
    "invalid/reserved environment entries fail instead of being silently skipped": "srceng_environment_invalid" in environment_method and "Ignoring malformed environment override" not in environment_method and "Ignoring reserved environment override" not in environment_method,
    "environment validation errors have localized resources": 'R.string.srceng_environment_apply_failed' in valve and 'R.string.srceng_environment_invalid' in valve,
    "game discovery handles unreadable directory listings": "listFiles()" in discovery and "children == null" in discovery,
    "CI runs Stage 10 reliability/security assertions": "bash scripts/verify-stage10.sh" in workflow,
}
for name, ok in checks.items():
    print(("PASS" if ok else "FAIL") + ": " + name)
failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit("Stage 10 verification failed: " + "; ".join(failed))
print("Stage 10 static checks passed: %d assertions." % len(checks))
PY
