#!/usr/bin/env bash
set -euo pipefail
cat >&2 <<'MSG'
ERROR: The legacy deploy-to-nillerusr script is retired.
It embedded GH_TOKEN in a remote URL and force-pushed to an unrelated repository.
Use the verified GitHub Actions artifact from this repository instead.
No network request or repository mutation was performed.
MSG
exit 2
