#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "\${BASH_SOURCE[0]}")/.." && pwd)"
LOCK_FILE="$ROOT_DIR/scripts/dependencies.lock"
[[ -f "$LOCK_FILE" ]] || { echo "ERROR: Missing dependency lock: $LOCK_FILE" >&2; exit 2; }
# The lock file is simple KEY=VALUE data maintained with the repository, not user input.
while IFS='=' read -r key value; do
  [[ -z "$key" || "$key" == \#* ]] && continue
  case "$key" in
    NDK_BINARIES_URL|NDK_BINARIES_COMMIT|ANDROID_SDK_URL|ANDROID_SDK_COMMIT|SOURCE_SDK_URL|SOURCE_SDK_COMMIT) ;;
    *) echo "ERROR: Unknown key in dependencies.lock: $key" >&2; exit 2 ;;
  esac
  printf -v "$key" '%s' "$value"
done < "$LOCK_FILE"
for key in NDK_BINARIES_COMMIT ANDROID_SDK_COMMIT SOURCE_SDK_COMMIT; do
  value="$(printenv "$key" 2>/dev/null || true)"
  [[ -n "$value" ]] || eval "value=\${$key}"
  [[ "$value" =~ ^[0-9a-f]{40}$ ]] || { echo "ERROR: $key must be a full 40-character commit SHA." >&2; exit 2; }
done
fetch_one() {
  local name="$1" url="$2" sha="$3"
  [[ -n "$url" && "$sha" =~ ^[0-9a-f]{40}$ ]] || { echo "ERROR: Invalid pinned dependency for $name." >&2; exit 2; }
  rm -rf "$ROOT_DIR/$name"
  git init -q "$ROOT_DIR/$name"
  git -C "$ROOT_DIR/$name" remote add origin "$url"
  git -C "$ROOT_DIR/$name" fetch --depth 1 origin "$sha"
  git -C "$ROOT_DIR/$name" checkout --detach -q FETCH_HEAD
  local actual
  actual="$(git -C "$ROOT_DIR/$name" rev-parse HEAD)"
  [[ "$actual" == "$sha" ]] || { echo "ERROR: $name resolved to $actual, expected $sha." >&2; exit 1; }
  printf '%s=%s\n' "$name" "$actual"
}
fetch_one ndk-binaries "$NDK_BINARIES_URL" "$NDK_BINARIES_COMMIT"
fetch_one android-sdk "$ANDROID_SDK_URL" "$ANDROID_SDK_COMMIT"
fetch_one srcsdk "$SOURCE_SDK_URL" "$SOURCE_SDK_COMMIT"
git -C "$ROOT_DIR/srcsdk" submodule update --init --recursive --depth 1
echo "Pinned Source SDK submodules:"
git -C "$ROOT_DIR/srcsdk" submodule status --recursive
if [[ -n "\${GITHUB_ENV:-}" ]]; then
  {
    printf 'NDK_BINARIES_COMMIT=%s\n' "$NDK_BINARIES_COMMIT"
    printf 'ANDROID_SDK_COMMIT=%s\n' "$ANDROID_SDK_COMMIT"
    printf 'SOURCE_SDK_COMMIT=%s\n' "$SOURCE_SDK_COMMIT"
  } >> "$GITHUB_ENV"
fi
