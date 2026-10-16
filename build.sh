#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "\${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"
if [[ -z "\${ANDROID_HOME:-}" ]]; then
  if [[ -n "\${ANDROID_SDK_ROOT:-}" ]]; then export ANDROID_HOME="$ANDROID_SDK_ROOT"
  elif [[ -d "$ROOT_DIR/android-sdk" ]]; then export ANDROID_HOME="$ROOT_DIR/android-sdk"
  else echo "ERROR: Set ANDROID_HOME or ANDROID_SDK_ROOT to a compatible legacy SDK." >&2; exit 2; fi
fi
for tool in java ant python3 unzip sha256sum readelf; do command -v "$tool" >/dev/null 2>&1 || { echo "ERROR: Missing required tool: $tool" >&2; exit 2; }; done
[[ -f "$ANDROID_HOME/tools/ant/build.xml" ]] || { echo "ERROR: $ANDROID_HOME lacks tools/ant/build.xml." >&2; exit 2; }
AAPT="$(find "$ANDROID_HOME/build-tools" -maxdepth 2 -type f -name aapt -print 2>/dev/null | sort -V | tail -n 1)"
[[ -n "$AAPT" && -x "$AAPT" ]] || { echo "ERROR: No executable aapt under $ANDROID_HOME/build-tools." >&2; exit 2; }
export COMMIT="\${COMMIT:-$(git rev-parse HEAD)}"
export BUILD_BRANCH="\${BUILD_BRANCH:-$(git branch --show-current 2>/dev/null || printf local)}"
mkdir -p "$ROOT_DIR/res/values" "$ROOT_DIR/bin"
python3 - "$ROOT_DIR/res/values/build_info.xml" "$COMMIT" "$BUILD_BRANCH" <<'PY'
import sys
from xml.sax.saxutils import escape
path, commit, branch = sys.argv[1:]
with open(path, "w", encoding="utf-8", newline="\n") as f:
    f.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n')
    f.write('  <string name="last_commit">%s</string>\n' % escape(commit))
    f.write('  <string name="deploy_branch">%s</string>\n' % escape(branch))
    f.write('</resources>\n')
PY
rm -f "$ROOT_DIR/bin/srceng-debug.apk" "$ROOT_DIR/bin/srceng-debug-unaligned.apk"
ant -Dsdk.dir="$ANDROID_HOME" clean debug
[[ -s "$ROOT_DIR/bin/srceng-debug.apk" ]] || { echo "ERROR: Ant did not produce bin/srceng-debug.apk." >&2; exit 1; }
AAPT="$AAPT" bash "$ROOT_DIR/scripts/verify-apk.sh" "$ROOT_DIR/bin/srceng-debug.apk"
