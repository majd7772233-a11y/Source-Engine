#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"
bash -n scripts/verify-stage15.sh
python3 - "$ROOT_DIR" <<'PY'
import pathlib, sys, xml.etree.ElementTree as ET
root = pathlib.Path(sys.argv[1])
overlay = (root / "src/org/libsdl/app/TouchControlsOverlay.java").read_text(encoding="utf-8")
state = (root / "src/org/libsdl/app/TouchInputState.java").read_text(encoding="utf-8")
activity = (root / "src/org/libsdl/app/SDLActivity.java").read_text(encoding="utf-8")
manager = (root / "src/org/libsdl/app/SDLControllerManager.java").read_text(encoding="utf-8")
tests = (root / "tests/java/org/libsdl/app/TouchInputStateTestSuite.java").read_text(encoding="utf-8")
runner = (root / "scripts/test-java.sh").read_text(encoding="utf-8")
workflow = (root / ".github/workflows/build.yml").read_text(encoding="utf-8")
smoke = (root / "docs/DEVICE-SMOKE-TEST.md").read_text(encoding="utf-8")
en_text = (root / "res/values/strings.xml").read_text(encoding="utf-8")
en = {n.attrib.get("name") for n in ET.parse(root / "res/values/strings.xml").getroot() if n.tag == "string"}
ar = {n.attrib.get("name") for n in ET.parse(root / "res/values-ar/strings.xml").getroot() if n.tag == "string"}
menu = overlay[overlay.index("private void showSettings()"):overlay.index("private void choosePreset()")]
config = activity[activity.index("public void onConfigurationChanged"):activity.index("protected void onNewIntent")]
stop = activity[activity.index("protected void onStop"):activity.index("protected void onStart")]
checks = {
 "input state is independently testable": "final class TouchInputState" in state and "interface Sink" in state,
 "action buttons use reference-counted state": "inputState.pressButton(pointer, key)" in overlay and "inputState.releaseButton(pointer)" in overlay,
 "movement uses the same state": "inputState.setJoystickKey(key, pressed)" in overlay,
 "key ownership is reference counted": "buttonCounts" in state and "joystickKeys" in state and "wasDown == wanted" in state,
 "release-all clears key and pointer state": "nativeKeys.clear()" in state and "pointerKeys.clear()" in state,
 "fire has balanced mouse down/up": "onNativeMouse(1, MotionEvent.ACTION_DOWN" in overlay and "onNativeMouse(1, MotionEvent.ACTION_UP" in overlay,
 "settings dialog releases all input": "releaseAll();" in menu,
 "outside secondary pointer preserves active gesture": "action == MotionEvent.ACTION_POINTER_DOWN && hasActiveControlGesture()" in overlay,
 "edit drag is single-pointer owned": "secondary pointer must not hijack" in overlay and "if (dragControl >= 0) return true;" in overlay,
 "resize and detach release active input": "oldw > 0 && oldh > 0 && (w != oldw || h != oldh)" in overlay and "protected void onDetachedFromWindow()" in overlay,
 "activity pause/destroy release input": "protected void onPause()" in activity and "protected void onDestroy()" in activity and "mTouchControls.releaseAll()" in activity,
 "focus loss releases overlay input": "if (!hasFocus && !mBrokenLibraries && mTouchControls != null)" in activity,
 "configuration change releases stale input": "mTouchControls.releaseAll();" in config,
 "multi-window stop releases input": "if (!mBrokenLibraries && mTouchControls != null) mTouchControls.releaseAll();" in stop,
 "physical keyboard/mouse paths remain present": "onNativeKeyDown(keyCode)" in activity and "InputDevice.SOURCE_MOUSE" in activity,
 "controller buttons remain integrated": "SDLControllerManager.onNativePadDown(deviceId, keyCode)" in activity and "SDLControllerManager.onNativePadUp(deviceId, keyCode)" in activity,
 "controller hotplug preserves native add/remove": "nativeAddJoystick" in manager and "nativeRemoveJoystick" in manager,
 "JVM tests cover pointer overlap/reuse/cancellation": all(x in tests for x in ("testTwoPointersSharingAKey","testJoystickAndButtonShareAKey","testPointerReuseReleasesPreviousKey","testCancelReleasesEveryHeldKeyExactlyOnce")),
 "JVM runner invokes Stage 15 suite": "src/org/libsdl/app/TouchInputState.java" in runner and "org.libsdl.app.TouchInputStateTestSuite" in runner,
 "Stage 15 verifier is wired in CI": "bash scripts/verify-stage15.sh" in workflow,
 "mapping explicitly says digital rather than analogue": "WASD" in en_text and "not an analogue gamepad" in en_text,
 "English and Arabic mapping notes exist": "srceng_controls_mapping_note" in en and "srceng_controls_mapping_note" in ar,
 "controls still persist and reset": all(x in overlay for x in ("save()","choosePreset()","adjustValue(","resetControls()","controlsVisible")),
 "device checklist covers multitouch and interruptions": all(x in smoke for x in ("movement + fire","controller connect/disconnect","background/restore","reposition/resize controls"))
}
missing = [k for k, ok in checks.items() if not ok]
if missing:
 raise SystemExit("Stage 15 checks failed: " + ", ".join(missing))
print("Stage 15 static checks passed: " + str(len(checks)) + " assertions.")
PY
