#!/usr/bin/env python3
"""Apply a deterministic Android versionCode/versionName for a release build."""
import pathlib
import re
import sys

VERSION_PATTERN = re.compile(r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)")
ANDROID_PACKAGE = 'package="com.magd.source.engine"'


def main():
    if len(sys.argv) not in (2, 3):
        print("Usage: prepare-release-version.py MAJOR.MINOR.PATCH [AndroidManifest.xml]", file=sys.stderr)
        return 2

    version = sys.argv[1]
    match = VERSION_PATTERN.fullmatch(version)
    if not match:
        print("ERROR: release version must be MAJOR.MINOR.PATCH without leading zeroes.", file=sys.stderr)
        return 2

    major, minor, patch = (int(part) for part in match.groups())
    if minor > 999 or patch > 999:
        print("ERROR: minor and patch release components must be between 0 and 999.", file=sys.stderr)
        return 2

    version_code = major * 1_000_000 + minor * 1_000 + patch
    if not 1 <= version_code <= 2_147_483_647:
        print("ERROR: calculated Android versionCode is outside the supported positive 32-bit range.", file=sys.stderr)
        return 2

    manifest = pathlib.Path(sys.argv[2]) if len(sys.argv) == 3 else pathlib.Path(__file__).resolve().parents[1] / "AndroidManifest.xml"
    if not manifest.is_file():
        print(f"ERROR: Android manifest not found: {manifest}", file=sys.stderr)
        return 2

    text = manifest.read_text(encoding="utf-8")
    if ANDROID_PACKAGE not in text:
        print("ERROR: refusing to version a manifest with an unexpected application ID.", file=sys.stderr)
        return 2
    if 'android:debuggable="true"' in text:
        print("ERROR: the source manifest explicitly forces debuggable=true.", file=sys.stderr)
        return 2

    text, code_count = re.subn(r'(android:versionCode=")[^"]+(")', rf'\g<1>{version_code}\2', text, count=1)
    text, name_count = re.subn(r'(android:versionName=")[^"]+(")', rf'\g<1>{version}\2', text, count=1)
    if code_count != 1 or name_count != 1:
        print("ERROR: expected exactly one versionCode and versionName attribute in AndroidManifest.xml.", file=sys.stderr)
        return 2

    manifest.write_text(text, encoding="utf-8", newline="\n")
    print(f"Prepared release version {version} (Android versionCode {version_code}).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
