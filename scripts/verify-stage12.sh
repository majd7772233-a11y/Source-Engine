#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"
bash -n scripts/verify-stage12.sh
python3 - "$ROOT_DIR" <<'PY'
import pathlib
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

root = pathlib.Path(sys.argv[1])
workflow = (root / ".github/workflows/build.yml").read_text(encoding="utf-8")
version_script = root / "scripts/prepare-release-version.py"
version_text = version_script.read_text(encoding="utf-8")
readme = (root / "README.md").read_text(encoding="utf-8")
changelog = root / "CHANGELOG.md"
manifest = root / "AndroidManifest.xml"
strings = (root / "res/values/strings.xml").read_text(encoding="utf-8")
checks = {
    "version helper is wired before packaging": "Prepare release version" in workflow and workflow.index("Prepare release version") < workflow.index("Build, verify APK, and record checksums"),
    "version helper only accepts explicit semantic versions": "VERSION_PATTERN.fullmatch(version)" in version_text and "minor and patch release components" in version_text,
    "deterministic positive Android versionCode": "major * 1_000_000 + minor * 1_000 + patch" in version_text and "2_147_483_647" in version_text,
    "release tag trigger is explicit": "tags: [ 'v*' ]" in workflow,
    "manual release is opt-in": "sign_release" in workflow and "release_version" in workflow and "default: false" in workflow,
    "release uses a non-debuggable Ant release variant": "clean release" in workflow and "application-debuggable" in workflow,
    "keystore secret name is used": "secrets.MY_KEYSTORE_JKS" in workflow,
    "keystore password secret name is used": "secrets.KEYSTORE_PASSWORD" in workflow and "--ks-pass env:KEYSTORE_PASSWORD" in workflow,
    "alias secret name is used": "secrets.KEY_ALIAS" in workflow and '--ks-key-alias "$KEY_ALIAS"' in workflow,
    "private-key password secret name is used": "secrets.KEY_PASSWORD" in workflow and "--key-pass env:KEY_PASSWORD" in workflow,
    "base64 keystore is decoded without printing secret": "base64 --decode" in workflow and "set +x" in workflow,
    "temporary keystore is private and removed": 'chmod 600 "$keystore"' in workflow and 'rm -f "$keystore"' in workflow,
    "signature is verified after signing": "apksigner verify --verbose --print-certs" in workflow,
    "release package/version identity is verified": "package: name='com.magd.source.engine'" in workflow and "versionName='$RELEASE_VERSION'" in workflow,
    "debug APK artifact remains separate": "source-engine-android-debug" in workflow and "source-engine-android-release-" in workflow,
    "debug artifact is preserved before release clean": workflow.count("Build and sign non-debuggable release APK") == 1 and workflow.index("Upload verified debug APK") < workflow.index("Build and sign non-debuggable release APK") < workflow.index("Upload signed release APK"),
    "release checksum and signing report are uploaded": "build-reports/SHA256SUMS" in workflow and "build-reports/release-signature.txt" in workflow and "release-metadata.txt" in workflow,
    "stage verifier is wired into CI": "bash scripts/verify-stage12.sh" in workflow,
    "release signing instructions document secret names": all(name in readme for name in ("MY_KEYSTORE_JKS", "KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")),
    "release notes exist": changelog.is_file() and "## Unreleased" in changelog.read_text(encoding="utf-8"),
    "source manifest retains canonical app ID": 'package="com.magd.source.engine"' in manifest.read_text(encoding="utf-8"),
    "source manifest does not force debug mode": 'android:debuggable="true"' not in manifest.read_text(encoding="utf-8"),
    "launcher retains user-visible readiness disclosure": "srceng_library_compatibility" in strings,
}
missing = [name for name, ok in checks.items() if not ok]
if missing:
    raise SystemExit("Stage 12 static checks failed: " + ", ".join(missing))

for pattern in ("*.jks", "*.keystore", "*.p12", "debug.keystore"):
    matches = [p for p in root.rglob(pattern) if ".git" not in p.parts and not any(part in {"bin", "build-reports", "obj", "gen"} for part in p.parts)]
    if matches:
        raise SystemExit("Signing material must never be committed: " + ", ".join(str(p.relative_to(root)) for p in matches))

helper = [sys.executable, str(version_script)]
with tempfile.TemporaryDirectory(prefix="source-engine-version-test-") as temp:
    temp_path = pathlib.Path(temp)
    valid_manifest = temp_path / "AndroidManifest.xml"
    shutil.copyfile(manifest, valid_manifest)
    valid = subprocess.run(helper + ["1.12.3", str(valid_manifest)], text=True, capture_output=True)
    if valid.returncode != 0:
        raise SystemExit("Release version helper rejected a valid version: " + valid.stderr)
    root_xml = ET.parse(valid_manifest).getroot()
    ns = "{http://schemas.android.com/apk/res/android}"
    if root_xml.get(ns + "versionName") != "1.12.3" or root_xml.get(ns + "versionCode") != "1012003":
        raise SystemExit("Release version helper generated incorrect manifest version fields")

    for bad_version in ("1.12", "01.2.3", "1.1000.0", "1.2.3-beta", "999999.0.0"):
        invalid_manifest = temp_path / ("invalid-" + bad_version.replace(".", "_").replace("-", "_") + ".xml")
        shutil.copyfile(manifest, invalid_manifest)
        before = invalid_manifest.read_text(encoding="utf-8")
        result = subprocess.run(helper + [bad_version, str(invalid_manifest)], text=True, capture_output=True)
        if result.returncode == 0 or invalid_manifest.read_text(encoding="utf-8") != before:
            raise SystemExit("Release version helper did not safely reject " + bad_version)

print("Stage 12 checks passed: " + str(len(checks)) + " release/signing assertions plus valid/invalid version tests.")
PY
