#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
bash -n build.sh scripts/build-java.sh scripts/workflow.sh scripts/verify-stage0.sh scripts/verify-stage1.sh scripts/verify-apk.sh scripts/fetch-build-deps.sh
python3 - "$ROOT_DIR" <<'PY'
import pathlib, sys
import xml.etree.ElementTree as ET
from xml.sax.saxutils import escape
root = pathlib.Path(sys.argv[1])
workflow = (root / ".github/workflows/build.yml").read_text(encoding="utf-8")
native = (root / "scripts/workflow.sh").read_text(encoding="utf-8")
build = (root / "build.sh").read_text(encoding="utf-8")
verify = (root / "scripts/verify-apk.sh").read_text(encoding="utf-8")
fetch = (root / "scripts/fetch-build-deps.sh").read_text(encoding="utf-8")
lock = (root / "scripts/dependencies.lock").read_text(encoding="utf-8")
deploy = (root / "scripts/upload-to-github.sh").read_text(encoding="utf-8")
discord = (root / "scripts/send-to-discord.py").read_text(encoding="utf-8")
if "if: success()" not in workflow or "actions/upload-artifact" not in workflow: raise SystemExit("Artifact upload must be success-gated.")
for label, source in (("native workflow", native), ("Java build", build)):
    if 'rm -f "$ROOT_DIR/bin/srceng-debug.apk"' not in source: raise SystemExit(f"{label} must remove stale APKs.")
    if "verify-apk.sh" not in source: raise SystemExit(f"{label} must verify the APK.")
if "readelf -h" not in verify or "sha256sum" not in verify or "com.magd.source.engine" not in verify: raise SystemExit("APK verifier lacks ELF, identity, or checksum checks.")
if "https://nillerusr:${GH_TOKEN}@" in deploy or "DISCORD_TOKEN" in discord or "CHANNEL_ID" in discord: raise SystemExit("Retired deployment scripts must not consume credentials.")
if "scripts/fetch-build-deps.sh" not in workflow: raise SystemExit("Workflow must use the dependency lock fetcher.")
for key in ("NDK_BINARIES_COMMIT", "ANDROID_SDK_COMMIT", "SOURCE_SDK_COMMIT"):
    import re
    match = re.search(r"^" + key + r"=([0-9a-f]{40})$", lock, re.M)
    if not match: raise SystemExit(f"Missing immutable SHA for {key}.")
if "git fetch --depth 1 origin \"$sha\"" not in fetch or "submodule update --init --recursive" not in fetch:
    raise SystemExit("Dependency fetcher must check out exact commits and pinned submodules.")
if "readelf -d" not in verify: raise SystemExit("APK verifier must inspect native shared-library dependencies.")
ET.fromstring('<?xml version="1.0"?><resources><string name="x">%s</string></resources>' % escape('a&b<c'))
for path in (root / "build.sh", root / "scripts/workflow.sh"):
    if not path.stat().st_mode & 0o111: raise SystemExit(f"Script lacks executable mode: {path.relative_to(root)}")
print("Stage 1 static checks passed.")
PY
