#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK="${1:-$ROOT_DIR/bin/srceng-debug.apk}"
[[ -s "$APK" ]] || { echo "ERROR: APK missing or empty: $APK" >&2; exit 1; }
for tool in unzip readelf sha256sum; do command -v "$tool" >/dev/null 2>&1 || { echo "ERROR: Missing required tool: $tool" >&2; exit 2; }; done
if [[ -z "${AAPT:-}" ]]; then SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$ROOT_DIR/android-sdk}}"; AAPT="$(find "$SDK/build-tools" -maxdepth 2 -type f -name aapt -print 2>/dev/null | sort -V | tail -n 1)"; fi
[[ -n "$AAPT" && -x "$AAPT" ]] || { echo "ERROR: Unable to locate executable aapt." >&2; exit 2; }
unzip -t "$APK" >/dev/null
BADGING="$("$AAPT" dump badging "$APK")"
printf '%s\n' "$BADGING" | grep -Fq "package: name='com.magd.source.engine'" || { echo "ERROR: Wrong APK application ID." >&2; exit 1; }
FILES="$(unzip -Z1 "$APK")"
for entry in AndroidManifest.xml classes.dex resources.arsc; do printf '%s\n' "$FILES" | grep -Fxq "$entry" || { echo "ERROR: Missing APK entry: $entry" >&2; exit 1; }; done
for lib in libtierhook.so libmain.so libRegal.so libclient.so libserver.so; do printf '%s\n' "$FILES" | grep -Fxq "lib/armeabi-v7a/$lib" || { echo "ERROR: Missing ARMv7 library: $lib" >&2; exit 1; }; done
unexpected="$(printf '%s\n' "$FILES" | awk '/^lib\// && $0 !~ /^lib\/armeabi-v7a\// { print }')"
[[ -z "$unexpected" ]] || { echo "ERROR: Unexpected native ABI entries: $unexpected" >&2; exit 1; }
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
count=0
while IFS= read -r entry; do
  [[ "$entry" == lib/armeabi-v7a/*.so ]] || continue
  name="${entry##*/}"; unzip -p "$APK" "$entry" > "$tmp/$name"
  [[ -s "$tmp/$name" ]] || { echo "ERROR: Empty library: $entry" >&2; exit 1; }
  header="$(readelf -h "$tmp/$name" 2>&1)" || { echo "ERROR: Invalid ELF library: $entry" >&2; exit 1; }
  printf '%s\n' "$header" | grep -Eq 'Machine:[[:space:]]+ARM' || { echo "ERROR: Library is not ARM ELF: $entry" >&2; exit 1; }
  count=$((count + 1))
done <<< "$FILES"
(( count >= 5 )) || { echo "ERROR: Expected at least five ARMv7 libraries; found $count." >&2; exit 1; }
sha="$(sha256sum "$APK" | awk '{print $1}')"; bytes="$(wc -c < "$APK" | tr -d ' ')"
commit="${COMMIT:-$(git -C "$ROOT_DIR" rev-parse HEAD 2>/dev/null || printf unknown)}"
branch="${BUILD_BRANCH:-$(git -C "$ROOT_DIR" branch --show-current 2>/dev/null || printf unknown)}"
mkdir -p "$ROOT_DIR/build-reports"
{
  printf 'application_id=com.magd.source.engine\nabi=armeabi-v7a\nnative_libraries_verified=%s\napk_bytes=%s\napk_sha256=%s\ncommit=%s\nbranch=%s\n' "$count" "$bytes" "$sha" "$commit" "$branch"
  for key in NDK_BINARIES_COMMIT ANDROID_SDK_COMMIT SOURCE_SDK_COMMIT; do value="$(printenv "$key" 2>/dev/null || printf not-recorded)"; printf '%s=%s\n' "$key" "$value"; done
} > "$ROOT_DIR/build-reports/build-metadata.txt"
printf '%s  %s\n' "$sha" "$(basename "$APK")" > "$ROOT_DIR/build-reports/SHA256SUMS"
echo "APK verification passed: com.magd.source.engine; armeabi-v7a ($count ELF libraries); $bytes bytes; SHA-256 $sha"
