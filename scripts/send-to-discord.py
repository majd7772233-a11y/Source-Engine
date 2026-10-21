#!/usr/bin/env python3
"""Retired notifier: the build no longer requires Discord credentials or dependencies."""
import sys
print("ERROR: Discord notifications are retired; use the GitHub Actions run and artifact.", file=sys.stderr)
raise SystemExit(2)
