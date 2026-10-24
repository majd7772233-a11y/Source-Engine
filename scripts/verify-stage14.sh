#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
bash -n scripts/verify-stage14.sh
python3 - "$ROOT_DIR" <<'PY'
import pathlib, re, sys
import xml.etree.ElementTree as ET
root=pathlib.Path(sys.argv[1])
discovery=(root/"src/me/nillerusr/GameDiscovery.java").read_text(encoding="utf-8")
launcher=(root/"src/me/nillerusr/LauncherActivity.java").read_text(encoding="utf-8")
tests=(root/"tests/java/me/nillerusr/Stage14GameDiscoveryTestSuite.java").read_text(encoding="utf-8")
runner=(root/"scripts/test-java.sh").read_text(encoding="utf-8")
workflow=(root/".github/workflows/build.yml").read_text(encoding="utf-8")
def strings(path): return {n.get("name"):"".join(n.itertext()) for n in ET.parse(root/path).getroot() if n.tag=="string"}
en=strings("res/values/strings.xml"); ar=strings("res/values-ar/strings.xml")
checks={
 "root failure classification":all(x in discovery for x in ("enum RootStatus","INVALID_INPUT","UNAVAILABLE","NOT_DIRECTORY","UNREADABLE","ACCESS_DENIED")),
 "cooperative scanner cancellation":all(x in discovery for x in ("class CancellationToken","token.isCancelled()","boolean cancelled")),
 "only fully complete multi-root scans may replace a snapshot":all(x in discovery for x in ("canCommitSnapshot(List<ScanResult> results","results.isEmpty()","!result.isComplete()")),
 "zero candidates requires complete traversal":"return isComplete() && entries.isEmpty()" in discovery,
 "launcher wires cancel UI and scan token":all(x in launcher for x in ("currentScanCancellation","token.cancel()","scanCancelButton","scanResults")),
 "only complete branch redraws the existing library":"if (completed) {" in launcher and "renderGameLibrary();" in launcher and "GameDiscovery.canCommitSnapshot(" in launcher,
 "last history remains attempt-only":"Last attempt: %1$s" in en.get("srceng_library_last","") and "آخر محاولة تشغيل" in ar.get("srceng_library_last",""),
 "JVM suite covers missing roots, cancellation, depth and multi-root snapshots":all(x in tests for x in ("testCompleteEmptyIsNotFailure","testUnavailableRootIsDistinct","testCancelledScanCannotCommit","testDepthLimitedScanCannotCommit","testSnapshotRequiresEveryRootToComplete")),
 "JVM suite is invoked":"Stage14GameDiscoveryTestSuite" in runner,
 "verifier is invoked in CI":"bash scripts/verify-stage14.sh" in workflow}
bad=[k for k,v in checks.items() if not v]
if bad: raise SystemExit("Stage 14 static checks failed: "+", ".join(bad))
for locale,values in (("English",en),("Arabic",ar)):
    for key in ("srceng_library_scan_cancel","srceng_library_scan_cancelling","srceng_library_scan_cancelled","srceng_library_scan_no_roots","srceng_library_scan_incomplete"):
        if key not in values: raise SystemExit("Missing Stage 14 resource "+key+" in "+locale)
    if not re.search(r"%4\$d",values["srceng_library_scan_incomplete"]): raise SystemExit(locale+" scan summary lacks unavailable-root count")
print("Stage 14 static checks passed: "+str(len(checks))+" assertions.")
PY