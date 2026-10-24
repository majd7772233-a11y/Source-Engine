#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
bash -n scripts/verify-stage6.sh
python3 - "$ROOT_DIR" <<'PY'
import pathlib, sys, xml.etree.ElementTree as ET
root = pathlib.Path(sys.argv[1])
theme = (root / "src/me/nillerusr/LiquidGlassTheme.java").read_text(encoding="utf-8")
launcher = (root / "src/me/nillerusr/LauncherActivity.java").read_text(encoding="utf-8")
strings = ET.parse(root / "res/values/strings.xml").getroot()
workflow = (root / ".github/workflows/build.yml").read_text(encoding="utf-8")
resources = {n.attrib.get("name") for n in strings}
checks = {
    "dedicated Liquid Glass theme utility": "final class LiquidGlassTheme" in theme,
    "animated ambient multicolor backdrop": "class AmbientBackdrop extends Drawable" in theme and "RadialGradient" in theme,
    "animation can be disabled": "liquid_motion" in launcher and "new AmbientBackdrop(motionEnabled)" in theme,
    "motion setting is localized": "srceng_liquid_motion" in launcher and "srceng_liquid_motion" in resources,
    "motion preference persists": 'putBoolean("liquid_motion", checked)' in launcher,
    "static fallback avoids Android 12-only blur": "RenderEffect" not in theme and "animate ?" in theme,
    "button pressed/focused/disabled states": "state_pressed" in theme and "state_focused" in theme and "state_enabled" in theme,
    "glass material includes translucent gradients and density-aware edge strokes": "base.setStroke(Math.max(1, (int) density), edge)" in theme and "GradientDrawable.Orientation.TL_BR" in theme,
    "launcher screen uses shared theme": "LiquidGlassTheme.apply(this" in launcher,
    "game cards use consistent material": 'card.setTag("liquid_glass_card")' in launcher and "styleLibraryCard(view, motionEnabled)" in theme,
    "dialogs use shared glass surfaces": "styleGlassDialog(builder.show())" in launcher and "LiquidGlassTheme.panel(0xEE152737" in launcher,
    "text fields remain legible": "edit.setTextColor(TEXT)" in theme and "edit.setHintTextColor" in theme,
    "elevation is API guarded": "if (Build.VERSION.SDK_INT >= 21) card.setElevation" in theme and "if (Build.VERSION.SDK_INT >= 21) button.setElevation" in theme,
    "animation scheduler stops when hidden": "unscheduleSelf(frame)" in theme and "isVisible()" in theme and "LiquidGlassTheme.pause(this)" in launcher and "public static void pause(Activity activity)" in theme,
    "legacy Java source avoids modern syntax": "->" not in theme and "var " not in theme,
    "CI runs Stage 6 verifier": "bash scripts/verify-stage6.sh" in workflow,
}
missing = [name for name, ok in checks.items() if not ok]
if missing:
    raise SystemExit("Stage 6 static checks failed: " + ", ".join(missing))
print("Stage 6 static checks passed: " + str(len(checks)) + " Liquid Glass assertions.")
PY
