#!/usr/bin/env bash
set -euo pipefail

# Compatibility entry point for the old script name.
# The canonical Java/APK build lives in the repository-root build.sh.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
exec bash "$ROOT_DIR/build.sh" "$@"
