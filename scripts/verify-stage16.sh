#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"
bash -n scripts/verify-stage16.sh
python3 - "$ROOT_DIR" <<'PY'
from collections import Counter
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

root = pathlib.Path(sys.argv[1])
android = "{http://schemas.android.com/apk/res/android}"
manifest = ET.parse(root / "AndroidManifest.xml").getroot()
application = manifest.find("application")
assert application is not None, "manifest application node is missing"
assert application.attrib.get(android + "supportsRtl") == "true", (
    "application must opt in to Android RTL layout mirroring"
)
directory = next(
    (node for node in application.findall("activity")
     if node.attrib.get(android + "name") == "me.nillerusr.DirchActivity"),
    None,
)
assert directory is not None, "directory chooser activity is missing"
assert directory.attrib.get(android + "label") == "@string/srceng_app_name", (
    "directory chooser label must come from a localized resource"
)

def load_strings(path):
    xml = ET.parse(path).getroot()
    nodes = xml.findall("string")
    names = [node.attrib.get("name") for node in nodes]
    assert None not in names, f"{path}: string without a name"
    assert len(names) == len(set(names)), f"{path}: duplicate string keys"
    return {node.attrib["name"]: "".join(node.itertext()) for node in nodes}

default = load_strings(root / "res/values/strings.xml")
arabic = load_strings(root / "res/values-ar/strings.xml")
missing = sorted(set(default) - set(arabic))
extra = sorted(set(arabic) - set(default))
assert not missing, "Arabic translations missing for: " + ", ".join(missing)
assert not extra, "Arabic-only string keys: " + ", ".join(extra)
placeholder_pattern = re.compile(r"%(?:\d+\$)?[-#+ 0,(<]*\d*(?:\.\d+)?[bBhHsScCdoxXeEfgGaA%n]")
mismatches = []
for key in sorted(default):
    expected = Counter(placeholder_pattern.findall(default[key]))
    actual = Counter(placeholder_pattern.findall(arabic[key]))
    if expected != actual:
        mismatches.append(f"{key}: default={dict(expected)}, Arabic={dict(actual)}")
assert not mismatches, "Locale format-placeholder mismatch: " + "; ".join(mismatches)
assert arabic.get("srceng_library_empty_title") == "أضف لعبتك الأولى", (
    "Arabic empty-library action should use the corrected imperative"
)
assert "عدد المجلدات المرشحة: %1$d" in arabic.get("srceng_library_scan_done", ""), (
    "Arabic scan completion count wording is missing"
)

launcher = (root / "src/me/nillerusr/LauncherActivity.java").read_text(encoding="utf-8")
start = launcher.find("private void showCompatibilityDiagnostics()")
end = launcher.find("private static final class Api21", start)
assert start >= 0 and end > start, "cannot isolate compatibility diagnostics"
diagnostics = launcher[start:end]
assert r'report.append("\\n")' not in diagnostics, (
    "compatibility diagnostics must not append literal backslash-n text"
)
assert r'report.append("\\n\\n")' not in diagnostics, (
    "compatibility diagnostics must not append literal escaped blank lines"
)
assert r'report.append("\n")' in diagnostics, "real line breaks are missing from the report"
assert r'report.append("\n\n")' in diagnostics, "section breaks are missing from the report"

# Accessibility and visual implementation must be source-verified, not inferred from locale parity.
assert "LiquidGlassTheme.markHeading(title)" in launcher, "library title is not marked as an accessibility heading"
assert "LiquidGlassTheme.markHeading(name)" in launcher, "game card titles are not marked as accessibility headings"
assert "announceForAccessibility" in launcher, "dynamic scan/advanced state announcements are missing"
assert "srceng_library_card_accessibility" in launcher, "game cards lack a meaningful TalkBack summary"
glass = (root / "src/me/nillerusr/LiquidGlassTheme.java").read_text(encoding="utf-8")
assert "setMinWidth(Math.max(button.getMinimumWidth(), dp(button, 48)))" in glass, "glass-styled actions lack a minimum touch width"
assert "new LayerDrawable(new Drawable[] { base, sheen })" in glass, "layered glass sheen is missing"
assert "Build.VERSION.SDK_INT >= 28" in glass and "view.setAccessibilityHeading(true)" in glass, "accessibility heading API is not guarded"
assert "liquid_motion" in launcher and "motionToggle" in launcher, "motion-off preference is not wired"

workflow = (root / ".github/workflows/build.yml").read_text(encoding="utf-8")
assert "bash scripts/verify-stage16.sh" in workflow, "canonical CI does not run the Stage 16 verifier"
for path in (
    root / "res/values/strings.xml",
    root / "res/values-ar/strings.xml",
    root / "res/layout/activity_launcher.xml",
    root / "res/layout/activity_directory_choice.xml",
):
    ET.parse(path)
print(
    "Stage 16 static checks passed: RTL declaration, localized activity label, "
    f"{len(default)} default/Arabic string keys, placeholder parity, corrected Arabic copy, "
    "compatibility-report line breaks, accessible headings and scan announcements, "
    "48dp minimum action targets, localized game-card summaries, and layered glass surfaces."
)
PY
