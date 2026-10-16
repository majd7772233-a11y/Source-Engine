#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

export NDK_HOME="${NDK_HOME:-$ROOT_DIR/ndk-binaries}"
export LIBPATH="${LIBPATH:-$ROOT_DIR/libs/armeabi-v7a}"
export NDK_TOOLCHAIN_VERSION="${NDK_TOOLCHAIN_VERSION:-4.9}"
export ANDROID_HOME="${ANDROID_HOME:-$ROOT_DIR/android-sdk}"
export COMMIT="${COMMIT:-$(git rev-parse HEAD)}"
export DEPLOY_BRANCH="${DEPLOY_BRANCH:-$(git branch --show-current 2>/dev/null || echo local)}"

for required in "$NDK_HOME" "$ANDROID_HOME" "$ROOT_DIR/srcsdk" "$LIBPATH"; do
  [[ -d "$required" ]] || { echo "ERROR: Required build directory is missing: $required" >&2; exit 2; }
done
command -v make >/dev/null 2>&1 || { echo "ERROR: make is required." >&2; exit 2; }
command -v ant >/dev/null 2>&1 || { echo "ERROR: Apache Ant is required." >&2; exit 2; }
command -v java >/dev/null 2>&1 || { echo "ERROR: Java is required." >&2; exit 2; }

build() {
  local dir="$1" library="$2"
  local previous="$PWD"
  cd "$ROOT_DIR/$dir"
  make NDK=1 NDK_PATH="$NDK_HOME" APP_API_LEVEL=19 CFG=debug NDK_VERBOSE=1 -j"$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 2)"
  [[ -s "$library" ]] || { echo "ERROR: Native build did not produce $dir/$library" >&2; exit 1; }
  cp "$library" "$LIBPATH/"
  echo "Installed $library"
  cd "$previous"
}

generate_resources() {
  local res="$ROOT_DIR/res/values/build_info.xml"
  mkdir -p "$(dirname "$res")"
  {
    printf '%s\n' '<?xml version="1.0" encoding="utf-8"?>' '<resources>'
    printf '  <string name="last_commit">%s</string>\n' "$COMMIT"
    printf '  <string name="deploy_branch">%s</string>\n' "$DEPLOY_BRANCH"
    printf '%s\n' '</resources>'
  } > "$res"
}

build "jni/src/tierhook" "libtierhook.so"

build "srcsdk/main" "libmain.so"
build "srcsdk/gl4es" "libRegal.so"
build "srcsdk/vinterface_wrapper/client" "libclient.so"
build "srcsdk/vinterface_wrapper/server" "libserver.so"

generate_resources
mkdir -p "$HOME/.android"
# Let the Android tools create/use the local debug key; never copy a repository key into the user's signing directory.
ant debug
[[ -s "$ROOT_DIR/bin/srceng-debug.apk" ]] || { echo "ERROR: Expected APK was not produced." >&2; exit 1; }
printf '%s' "$COMMIT" > "$ROOT_DIR/version"
echo "Build succeeded: $ROOT_DIR/bin/srceng-debug.apk"
