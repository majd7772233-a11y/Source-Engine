#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
bash -n scripts/verify-stage5.sh
python3 - "$ROOT_DIR" <<'PY'
import pathlib, re, sys
import xml.etree.ElementTree as ET
root = pathlib.Path(sys.argv[1])
launcher = (root / "src/me/nillerusr/LauncherActivity.java").read_text(encoding="utf-8")
layout = ET.parse(root / "res/layout/activity_launcher.xml").getroot()
strings_root = ET.parse(root / "res/values/strings.xml").getroot()
workflow = (root / ".github/workflows/build.yml").read_text(encoding="utf-8")
ns = "{http://schemas.android.com/apk/res/android}"
ids = {node.attrib.get(ns + "id") for node in layout.iter()}
resources = {node.attrib.get("name") for node in strings_root}
checks = {
    "library-first home layout": "setupGameLibrary(body)" in launcher and "body.addView(section, 0" in launcher,
    "primary folder picker is visible": "@+id/button_gamedir" in (root / "res/layout/activity_launcher.xml").read_text(encoding="utf-8") and "findViewById(R.id.button_gamedir)" in launcher,
    "advanced settings start collapsed": "advancedPanel.setVisibility(View.GONE)" in launcher and "R.id.advanced_panel" in launcher,
    "advanced settings remain wired": "edit_cmdline" in (root / "res/layout/activity_launcher.xml").read_text(encoding="utf-8") and "edit_env" in (root / "res/layout/activity_launcher.xml").read_text(encoding="utf-8") and "edit_gamepath" in (root / "res/layout/activity_launcher.xml").read_text(encoding="utf-8"),
    "game details are actionable": "showGameDetails(entry)" in launcher and "srceng_library_details" in launcher,
    "not-ready entries cannot launch": "play.setEnabled(entry.launchable)" in launcher and "selectedEntry == null || !selectedEntry.launchable" in launcher,
    "launch progress feedback": "srceng_library_launching" in launcher and "launcherProgress.setVisibility(View.VISIBLE)" in launcher,
    "scan progress feedback": "srceng_library_scanning" in launcher and "scanProgress.setVisibility(View.VISIBLE)" in launcher and "scanProgress.setVisibility(View.GONE)" in launcher,
    "scan failure does not commit partial library state": bool(re.search(
        r"if \\(scanFailure\\[0\\] == null\\) \\{\\s*mPref\\.edit\\(\\)\\.putStringSet\\(LIBRARY_ROOTS, new HashSet<String>\\(validRoots\\)\\).*?GameDiscovery\\.savePaths\\(LauncherActivity\\.this, discovered\\);\\s*\\}",
        launcher, re.S)),
    "touch targets meet 48dp baseline": "b.setMinHeight(dp(48))" in launcher and 'android:minHeight="48dp"' in (root / "res/layout/activity_launcher.xml").read_text(encoding="utf-8"),
    "long names and paths do not overflow": "name.setMaxLines(2)" in launcher and "path.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE)" in launcher,
    "card has accessibility description": "setContentDescription(getString(R.string.srceng_library_open_details" in launcher,
    "polite scan status for accessibility": "setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE)" in launcher,
    "picker result still triggers a scan": "scanLibrary(true, true);" in launcher,
    "game launch args and env still forwarded": 'putExtra("argv", cmdArgs.getText().toString().trim())' in launcher and 'putExtra("env", EnvEdit.getText().toString())' in launcher,
    "last launch and remove persistence retained": "GameDiscovery.recordLaunch(this, selectedEntry.path)" in launcher and "hidden.add(entry.path)" in launcher,
    "CI runs Stage 5 verifier": "bash scripts/verify-stage5.sh" in workflow,
}
missing = [name for name, ok in checks.items() if not ok]
if missing:
    raise SystemExit("Stage 5 static checks failed: " + ", ".join(missing))
required = [
    "srceng_library_header_subtitle", "srceng_library_intro", "srceng_library_empty_title",
    "srceng_library_advanced", "srceng_library_advanced_hide", "srceng_library_details_title",
    "srceng_library_details_action", "srceng_library_open_details", "srceng_library_launching",
    "srceng_library_scan_failed", "srceng_library_details", "srceng_storage_choose_folder",
]
missing = [name for name in required if name not in resources]
if missing:
    raise SystemExit("Missing Stage 5 localized resources: " + ", ".join(missing))
print("Stage 5 static checks passed: " + str(len(checks)) + " launcher UX assertions.")
PY
