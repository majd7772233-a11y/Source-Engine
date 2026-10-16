#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

if [[ -z "${ANDROID_HOME:-}" ]]; then
  if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
    export ANDROID_HOME="$ANDROID_SDK_ROOT"
  elif [[ -d "$ROOT_DIR/android-sdk" ]]; then
    export ANDROID_HOME="$ROOT_DIR/android-sdk"
  else
    echo "ERROR: Set ANDROID_HOME (or ANDROID_SDK_ROOT) to an Android SDK containing the legacy Ant build tools." >&2
    exit 2
  fi
fi

command -v java >/dev/null 2>&1 || { echo "ERROR: Java is not installed or not on PATH." >&2; exit 2; }
command -v ant >/dev/null 2>&1 || { echo "ERROR: Apache Ant is not installed or not on PATH." >&2; exit 2; }
[[ -f "$ANDROID_HOME/tools/ant/build.xml" ]] || {
  echo "ERROR: The selected SDK does not contain tools/ant/build.xml. This project still uses the legacy Ant Android build; install a compatible SDK or migrate the build in a planned stage." >&2
  exit 2
}

ant -Dsdk.dir="$ANDROID_HOME" clean debug
[[ -s "$ROOT_DIR/bin/srceng-debug.apk" ]] || {
  echo "ERROR: Ant finished without producing bin/srceng-debug.apk." >&2
  exit 1
}
echo "Build succeeded: $ROOT_DIR/bin/srceng-debug.apk"
