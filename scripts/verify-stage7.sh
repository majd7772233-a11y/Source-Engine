#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
bash -n scripts/verify-stage7.sh
python3 - "$ROOT_DIR" <<'PY'
import pathlib, sys, xml.etree.ElementTree as ET
root = pathlib.Path(sys.argv[1])
overlay = (root / "src/org/libsdl/app/TouchControlsOverlay.java").read_text(encoding="utf-8")
activity = (root / "src/org/libsdl/app/SDLActivity.java").read_text(encoding="utf-8")
workflow = (root / ".github/workflows/build.yml").read_text(encoding="utf-8")
en = ET.parse(root / "res/values/strings.xml").getroot()
ar = ET.parse(root / "res/values-ar/strings.xml").getroot()
en_names = {n.attrib.get("name") for n in en}
ar_names = {n.attrib.get("name") for n in ar}
checks = {
    "dedicated in-game touch overlay": "final class TouchControlsOverlay extends View" in overlay,
    "keyboard-mapped movement directions": all("KeyEvent.KEYCODE_" + k in overlay for k in ("W", "A", "S", "D")),
    "action keys are wired to SDL native input": "SDLActivity.onNativeKeyDown(key)" in overlay and "SDLActivity.onNativeKeyUp(key)" in overlay,
    "fire button uses mouse down/up rather than a wrong keyboard key": "SDLActivity.onNativeMouse(1, MotionEvent.ACTION_DOWN" in overlay and "SDLActivity.onNativeMouse(1, MotionEvent.ACTION_UP" in overlay,
    "multi-pointer buttons tracked independently": "pointerKeys" in overlay and "ACTION_POINTER_DOWN" in overlay and "ACTION_POINTER_UP" in overlay,
    "cancel/pause releases held keys": "ACTION_CANCEL" in overlay and "releaseAll()" in activity and "mTouchControls.releaseAll()" in activity,
    "per-game profile storage": "getSharedPreferences(PREFS" in overlay and 'prefix + "preset"' in overlay and 'prefix + "x" + i' in overlay,
    "size control is configurable": "srceng_controls_size" in overlay and "size = actual / 100.0f" in overlay,
    "opacity control is configurable": "srceng_controls_opacity" in overlay and "opacity = actual / 100.0f" in overlay,
    "joystick sensitivity is configurable": "srceng_controls_sensitivity" in overlay and "sensitivity" in overlay and "updateJoystick()" in overlay,
    "presets, drag positioning, and reset": "choosePreset()" in overlay and "editMode" in overlay and "resetControls()" in overlay,
    "controls can be hidden and restored": "srceng_controls_hide" in overlay and "srceng_controls_show" in overlay and "controlsVisible" in overlay,
    "per-game identity uses game path and directory": '"gamepath"' in activity and '"gamedir"' in activity and "controlsProfile" in activity,
    "overlay is layered over SDL surface": "mLayout.addView(mSurface);" in activity and "mLayout.addView(mTouchControls" in activity,
    "multitouch splitting enabled": "setMotionEventSplittingEnabled(true)" in activity,
    "no Java 7+ syntax in overlay": "->" not in overlay and "try (" not in overlay,
    "English strings complete": all(n in en_names for n in ("srceng_controls_title", "srceng_controls_layout", "srceng_controls_size", "srceng_controls_opacity", "srceng_controls_sensitivity", "srceng_controls_edit", "srceng_controls_reset", "srceng_controls_attack", "srceng_controls_jump", "srceng_controls_use", "srceng_controls_reload")),
    "Arabic strings complete": all(n in ar_names for n in ("srceng_controls_title", "srceng_controls_layout", "srceng_controls_size", "srceng_controls_opacity", "srceng_controls_sensitivity", "srceng_controls_edit", "srceng_controls_reset", "srceng_controls_attack", "srceng_controls_jump", "srceng_controls_use", "srceng_controls_reload")),
    "CI runs Stage 7 verifier": "bash scripts/verify-stage7.sh" in workflow,
}
missing = [name for name, ok in checks.items() if not ok]
if missing:
    raise SystemExit("Stage 7 static checks failed: " + ", ".join(missing))
print("Stage 7 static checks passed: " + str(len(checks)) + " touch/input assertions.")
PY
