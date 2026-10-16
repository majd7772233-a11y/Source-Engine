#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
export NDK_HOME="${NDK_HOME:-$ROOT_DIR/ndk-binaries}"
export LIBPATH="${LIBPATH:-$ROOT_DIR/libs/armeabi-v7a}"
export NDK_TOOLCHAIN_VERSION="${NDK_TOOLCHAIN_VERSION:-4.9}"
export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$ROOT_DIR/android-sdk}}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export COMMIT="${COMMIT:-$(git rev-parse HEAD)}"
export BUILD_BRANCH="${BUILD_BRANCH:-$(git branch --show-current 2>/dev/null || printf local)}"
for required in "$NDK_HOME" "$ANDROID_HOME" "$ROOT_DIR/srcsdk" "$LIBPATH"; do [[ -d "$required" ]] || { echo "ERROR: Required build directory missing: $required" >&2; exit 2; }; done
for tool in make ant java git python3 unzip sha256sum readelf; do command -v "$tool" >/dev/null 2>&1 || { echo "ERROR: Missing required tool: $tool" >&2; exit 2; }; done
[[ -f "$ANDROID_HOME/tools/ant/build.xml" ]] || { echo "ERROR: $ANDROID_HOME lacks tools/ant/build.xml." >&2; exit 2; }
AAPT="$(find "$ANDROID_HOME/build-tools" -maxdepth 2 -type f -name aapt -print 2>/dev/null | sort -V | tail -n 1)"
[[ -n "$AAPT" && -x "$AAPT" ]] || { echo "ERROR: No executable aapt under $ANDROID_HOME/build-tools." >&2; exit 2; }
build() {
  local dir="$1" library="$2" output="$ROOT_DIR/$dir/$library"
  rm -f "$output" "$LIBPATH/$library"
  (cd "$ROOT_DIR/$dir" && make NDK=1 NDK_PATH="$NDK_HOME" APP_API_LEVEL=19 CFG=debug NDK_VERBOSE=1 -j"$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 2)")
  [[ -s "$output" ]] || { echo "ERROR: Native build did not produce $dir/$library" >&2; exit 1; }
  cp "$output" "$LIBPATH/$library"
  [[ -s "$LIBPATH/$library" ]] || { echo "ERROR: Failed to install $library into $LIBPATH." >&2; exit 1; }
  echo "Verified native output: $library ($(wc -c < "$LIBPATH/$library") bytes)"
}
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
build "jni/src/tierhook" "libtierhook.so"
build "srcsdk/main" "libmain.so"
build "srcsdk/gl4es" "libRegal.so"
build "srcsdk/vinterface_wrapper/client" "libclient.so"
build "srcsdk/vinterface_wrapper/server" "libserver.so"
mkdir -p "$HOME/.android" "$ROOT_DIR/bin"
rm -f "$ROOT_DIR/bin/srceng-debug.apk" "$ROOT_DIR/bin/srceng-debug-unaligned.apk"
ant -Dsdk.dir="$ANDROID_HOME" clean debug
[[ -s "$ROOT_DIR/bin/srceng-debug.apk" ]] || { echo "ERROR: Ant did not produce bin/srceng-debug.apk." >&2; exit 1; }
AAPT="$AAPT" bash "$ROOT_DIR/scripts/verify-apk.sh" "$ROOT_DIR/bin/srceng-debug.apk"
