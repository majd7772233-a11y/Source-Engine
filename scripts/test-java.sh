#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"
command -v javac >/dev/null 2>&1 || { echo "ERROR: javac is required for the JVM test suite." >&2; exit 2; }
command -v java >/dev/null 2>&1 || { echo "ERROR: java is required for the JVM test suite." >&2; exit 2; }
TEST_BUILD_DIR="$(mktemp -d)"
trap 'rm -rf "$TEST_BUILD_DIR"' EXIT
mkdir -p "$TEST_BUILD_DIR/classes"
SOURCES_FILE="$TEST_BUILD_DIR/sources.txt"
find tests/java -type f -name '*.java' -print0 | sort -z | while IFS= read -r -d '' source; do printf '"%s"\n' "$source" >> "$SOURCES_FILE"; done
printf '"%s"\n' "$ROOT_DIR/src/me/nillerusr/LauncherLogic.java" "$ROOT_DIR/src/me/nillerusr/LauncherPreferences.java" "$ROOT_DIR/src/me/nillerusr/GameDiscovery.java" >> "$SOURCES_FILE"
javac -encoding UTF-8 -Xlint:all -d "$TEST_BUILD_DIR/classes" @"$SOURCES_FILE"
java -cp "$TEST_BUILD_DIR/classes" me.nillerusr.Stage11TestSuite
