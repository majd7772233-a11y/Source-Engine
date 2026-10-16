#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

bash -n build.sh scripts/build-java.sh scripts/workflow.sh

python3 - "$ROOT_DIR" <<'PY'
import pathlib
import sys
import xml.etree.ElementTree as ET

root = pathlib.Path(sys.argv[1])
expected_package = "com.magd.source.engine"
android_ns = "{http://schemas.android.com/apk/res/android}"
manifest = ET.parse(root / "AndroidManifest.xml").getroot()

if manifest.get("package") != expected_package:
    raise SystemExit(f"Unexpected manifest package: {manifest.get('package')!r}")
application = manifest.find("application")
if application is None:
    raise SystemExit("AndroidManifest.xml has no application element")
if application.get(android_ns + "debuggable", "false").lower() == "true":
    raise SystemExit("The application must not force android:debuggable=true")

for component in list(application.findall("activity")) + list(application.findall("service")):
    name = component.get(android_ns + "name", "")
    if not name or name.startswith("."):
        raise SystemExit(f"Manifest component name is not fully qualified: {name!r}")
    source = root / "src" / (name.replace(".", "/") + ".java")
    if not source.is_file():
        raise SystemExit(f"Manifest component has no matching Java source: {name} ({source})")

java_root = root / "src"
stale = []
resource_imports = []
for source in java_root.rglob("*.java"):
    text = source.read_text(encoding="utf-8", errors="replace")
    if "import com.valvesoftware.source.R;" in text:
        stale.append(str(source.relative_to(root)))
    if "import com.magd.source.engine.R;" in text:
        resource_imports.append(str(source.relative_to(root)))
if stale:
    raise SystemExit("Stale generated-R imports found: " + ", ".join(stale))
if not resource_imports:
    raise SystemExit("No generated-R imports for the canonical application package were found")

for script in ("build.sh", "scripts/workflow.sh"):
    text = (root / script).read_text(encoding="utf-8")
    for key in ('name="last_commit"', 'name="deploy_branch"'):
        if key not in text:
            raise SystemExit(f"{script} does not generate required resource {key}")

print(f"Stage 0 source checks passed: package={expected_package}; {len(resource_imports)} canonical R-import files; manifest components resolved; shell syntax valid.")
PY
