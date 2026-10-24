#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
bash -n scripts/verify-stage17.sh
python3 - "$ROOT_DIR" <<'PY'
import pathlib, sys
import xml.etree.ElementTree as ET
root = pathlib.Path(sys.argv[1])
def read(path): return (root / path).read_text(encoding="utf-8")
def strings(path):
    tree = ET.parse(root / path).getroot()
    return {node.attrib.get("name"): "".join(node.itertext()) for node in tree.findall("string")}
launcher = read("src/me/nillerusr/LauncherActivity.java")
layout = read("res/layout/activity_launcher.xml")
manifest_text = read("AndroidManifest.xml")
manifest = ET.parse(root / "AndroidManifest.xml").getroot()
valve = read("src/com/valvesoftware/ValveActivity2.java")
env = read("src/me/nillerusr/EnvironmentOverrides.java")
tests = read("tests/java/me/nillerusr/EnvironmentOverridesTestSuite.java")
runner = read("scripts/test-java.sh")
workflow = read(".github/workflows/build.yml")
inventory = read("docs/FEATURE-TRUTH-INVENTORY.md")
online_doc = read("docs/ONLINE-FEATURES.md")
troubleshooting = read("docs/USER-SETUP-TROUBLESHOOTING.md")
en, ar = strings("res/values/strings.xml"), strings("res/values-ar/strings.xml")
sdl = read("src/org/libsdl/app/SDLActivity.java")
android = "{http://schemas.android.com/apk/res/android}"
application = manifest.find("application")
assert application is not None, "manifest application missing"
components = {node.get(android + "name") for node in list(application.findall("activity")) + list(application.findall("service"))}
source_java = "\n".join(p.read_text(encoding="utf-8") for p in (root / "src").rglob("*.java"))
checks = {
 "inventory traces core user-visible controls and privacy": all(x in inventory for x in ("button_gamedir", "scanCancelButton", "showGameDetails", "selectGame", "startSource", "edit_cmdline", "edit_env", "edit_gamepath", "liquid_motion", "showCompatibilityDiagnostics", "showOnlineFeaturesStatus", "RECORD_AUDIO", "allowAudioPlaybackCapture")),
 "folder selection opens directory picker": 'android:id="@+id/button_gamedir"' in layout and "startActivityForResult(intent, REQUEST_STORAGE_PICKER)" in launcher,
 "scan, rescan and cancellation controls have live consumers": "scanLibrary(true, true)" in launcher and "scanLibrary(false, false)" in launcher and "token.cancel()" in launcher,
 "game card Use Play Remove and Details are wired": "selectGame(entry)" in launcher and "startSource(view)" in launcher and "saved.remove(entry.path)" in launcher and "showGameDetails(entry)" in launcher,
 "advanced panel, motion, diagnostics and online controls are wired": "advancedToggle.setOnClickListener" in launcher and 'putBoolean("liquid_motion", checked).apply()' in launcher and "showCompatibilityDiagnostics();" in launcher and "showOnlineFeaturesStatus();" in launcher,
 "saved settings are forwarded into native launch": all(x in launcher for x in ('putString("argv", argv)', 'putString("gamepath", gamepath)', 'putString("env", env)', 'intent.putExtra("argv"', 'intent.putExtra("env"')),
 "native bridge delegates overrides to pure Java helper": "EnvironmentOverrides.apply(rawEnvironment" in valve and "EnvironmentOverrides.ERROR_NONE" in valve and "EnvironmentOverrides.ERROR_APPLY_FAILED" in valve and "ERROR_INVALID = 1" in env,
 "entire list parsed before any native setter call": "Result parsed = parse(raw);" in env and env.index("Result parsed = parse(raw);") < env.index("setter.set(entry.getName(), entry.getValue())"),
 "invalid or reserved later values prevent any mutation": "invalidInputNeverMutatesEnvironment" in tests and "if (!parsed.isSuccess() || parsed.size() == 0) return parsed;" in env,
 "duplicate names are rejected before applying entries": "duplicateNamesAreRejected" in tests and "isReserved(name) || !seenNames.add(name)" in env,
 "launcher Android and dynamic-linker variables are reserved": all(x in env for x in ("VALVE_GAME_PATH", "APP_DATA_PATH", "APP_LIB_PATH", "EXTRAS_VPK_PATH", "APP_MOD_LIB", "LD_LIBRARY_PATH", "LD_PRELOAD", "ANDROID_ROOT", "ANDROID_DATA", "PATH")),
 "input count entry and NUL bounds have regression coverage": all(x in env for x in ("MAX_INPUT_LENGTH = 8192", "MAX_ENTRIES = 64", "MAX_ENTRY_LENGTH = 2048")) and all(x in tests for x in ("boundsAndSyntaxAreEnforced", "NUL rejected", "more than 64 entries rejected")),
 "native setter failure stops subsequent assignments and names the key": "setterFailureStopsFollowingAssignments" in tests and "getFailedName()" in valve and "ERROR_APPLY_FAILED" in env,
 "environment errors exist in both locales": all(x in en and x in ar for x in ("srceng_environment_invalid", "srceng_environment_apply_failed")),
 "dead updater marker checker and service are removed": not (root / "src/me/nillerusr/UpdateSystem.java").exists() and not (root / "src/me/nillerusr/UpdateService.java").exists() and "me.nillerusr.UpdateService" not in components and "srceng_update" not in en and "srceng_update" not in ar,
 "no orphaned update checkbox fields remain": "check_updates" not in launcher and "useVolumeButtons" not in launcher,
 "no in-app updating is claimed in end-user docs": "there is no in-app update checker" in online_doc.lower() and "no in-app update checker" in troubleshooting.lower(),
 "online panel reports Android connectivity but no fake server browser": "getActiveNetworkInfo()" in launcher and "active.isConnected()" in launcher and "In-app server browser: unavailable" in en.get("srceng_online_server_browser_unavailable", "") and "غير متاح" in ar.get("srceng_online_server_browser_unavailable", ""),
 "microphone remains optional and separate from startup storage permission": "android.permission.RECORD_AUDIO" in manifest_text and 'applyPermissions( new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE }, REQUEST_PERMISSIONS );' in launcher and "Manifest.permission.RECORD_AUDIO" not in launcher and "nativePermissionResult(requestCode, granted)" in sdl,
 "playback capture opt-in is documented as external capture": 'android:allowAudioPlaybackCapture="true"' in manifest_text and "external recorder selected by the user" in inventory.lower() and "does not create a screen-recording/streaming session" in inventory.lower(),
 "no app-owned MediaProjection capture implementation is present": "MediaProjection" not in source_java and "AudioPlaybackCaptureConfiguration" not in source_java,
 "pure Java parser test suite is part of canonical test runner": "EnvironmentOverrides.java" in runner and "EnvironmentOverridesTestSuite" in runner,
 "canonical workflow requires Stage 17 verifier": "bash scripts/verify-stage17.sh" in workflow,
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items(): print(("PASS" if ok else "FAIL") + ": " + name)
if failed: raise SystemExit("Stage 17 checks failed: " + "; ".join(failed))
print("Stage 17 static checks passed: %d assertions." % len(checks))
PY
