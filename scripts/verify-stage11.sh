#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"
for shell_script in build.sh scripts/*.sh; do bash -n "$shell_script"; done
python3 - "$ROOT_DIR" <<'PY'
from collections import defaultdict
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

root = Path(sys.argv[1])
def read(path):
    return (root / path).read_text(encoding="utf-8")

xml_paths = [root / "AndroidManifest.xml"] + sorted((root / "res").rglob("*.xml"))
parsed = {}
for path in xml_paths:
    try:
        parsed[path] = ET.parse(path).getroot()
    except ET.ParseError as exc:
        raise SystemExit("Malformed XML in " + str(path.relative_to(root)) + ": " + str(exc))

seen = defaultdict(set)
for path, xml_root in parsed.items():
    if not path.parent.name.startswith("values"):
        continue
    config = path.parent.name
    for node in list(xml_root):
        name = node.get("name")
        if not name:
            continue
        kind = node.get("type") if node.tag == "item" else node.tag
        key = (kind, name)
        if key in seen[config]:
            raise SystemExit("Duplicate resource " + str(key) + " in " + config)
        seen[config].add(key)

resource_names = set()
for path, xml_root in parsed.items():
    folder = path.parent.name
    if folder.startswith("values"):
        for node in list(xml_root):
            name = node.get("name")
            if name:
                kind = node.get("type") if node.tag == "item" else node.tag
                resource_names.add((kind, name))
    else:
        resource_names.add((folder.split("-", 1)[0], path.stem))

xml_reference_pattern = re.compile(r"@(?:(android):)?(\+)?([A-Za-z_][A-Za-z0-9_]*)/([A-Za-z_][A-Za-z0-9_.]*)")
id_definitions = set()
for path in xml_paths:
    source = path.read_text(encoding="utf-8")
    for namespace, plus, kind, name in xml_reference_pattern.findall(source):
        if namespace != "android" and kind == "id" and plus == "+":
            id_definitions.add((kind, name))
resource_names.update(id_definitions)
for path in xml_paths:
    source = path.read_text(encoding="utf-8")
    for namespace, plus, kind, name in xml_reference_pattern.findall(source):
        if namespace == "android" or plus == "+":
            continue
        if (kind, name) not in resource_names:
            raise SystemExit("Undefined XML resource reference @" + kind + "/" + name +
                             " in " + str(path.relative_to(root)))

default_strings_path = root / "res/values/strings.xml"
arabic_strings_path = root / "res/values-ar/strings.xml"
default_strings_root = ET.parse(default_strings_path).getroot()
default_strings = {node.get("name") for node in default_strings_root if node.tag == "string"}
arabic_strings_root = ET.parse(arabic_strings_path).getroot()
arabic_names = {node.get("name") for node in arabic_strings_root if node.tag == "string"}
if not arabic_names.issubset(default_strings):
    raise SystemExit("Arabic strings contain keys absent from the default locale: " +
                     ", ".join(sorted(arabic_names - default_strings)))

string_references = set()
java_sources = list((root / "src").rglob("*.java"))
source_texts = []
for path in java_sources:
    source = path.read_text(encoding="utf-8")
    source_texts.append(source)
    string_references.update(re.findall(r"\bR\.string\.([A-Za-z_][A-Za-z0-9_]*)", source))
missing_strings = string_references - default_strings
if missing_strings:
    raise SystemExit("Undefined R.string references: " + ", ".join(sorted(missing_strings)))

manifest = parsed[root / "AndroidManifest.xml"]
if manifest.get("package") != "com.magd.source.engine":
    raise SystemExit("Manifest package ID is not com.magd.source.engine")
uses_sdk = manifest.find("uses-sdk")
android_ns = "{http://schemas.android.com/apk/res/android}"
if uses_sdk is None or uses_sdk.get(android_ns + "minSdkVersion") != "17":
    raise SystemExit("Manifest minimum SDK changed unexpectedly; review compatibility policy.")
if "target=android-29" not in read("project.properties"):
    raise SystemExit("The checked-in Ant project target is not android-29.")
joined_java = "\n".join(source_texts)
if "com.valvesoftware.source.R" in joined_java:
    raise SystemExit("Stale generated-R import found.")
if "import com.magd.source.engine.R;" not in joined_java:
    raise SystemExit("Canonical generated-R imports were not found.")

lock_lines = {}
for line in read("scripts/dependencies.lock").splitlines():
    line = line.strip()
    if not line or line.startswith("#"):
        continue
    if "=" not in line:
        raise SystemExit("Malformed dependency lock line: " + line)
    key, value = line.split("=", 1)
    lock_lines[key.strip()] = value.strip()
for key in ("NDK_BINARIES_URL", "ANDROID_SDK_URL", "SOURCE_SDK_URL"):
    if not lock_lines.get(key, "").startswith("https://"):
        raise SystemExit("Dependency URL must use HTTPS: " + key)
for key in ("NDK_BINARIES_COMMIT", "ANDROID_SDK_COMMIT", "SOURCE_SDK_COMMIT"):
    if not re.fullmatch(r"[0-9a-f]{40}", lock_lines.get(key, "")):
        raise SystemExit("Dependency revision must be a full immutable SHA: " + key)
if "dependencies.lock" not in read("scripts/fetch-build-deps.sh"):
    raise SystemExit("Build dependency fetcher does not consume dependencies.lock.")

workflow = read(".github/workflows/build.yml")
for required in ("scripts/verify-stage11.sh", "scripts/test-java.sh",
                 "scripts/validate-workflow.py", "actionlint", "shellcheck"):
    if required not in workflow:
        raise SystemExit("Missing Stage 11 CI integration: " + required)

test_source = read("tests/java/me/nillerusr/Stage11TestSuite.java")
for marker in ("LauncherLogic.normalizePath", "LauncherLogic.buildGameArguments",
               "LauncherPreferences.migrate", "GameDiscovery.scan",
               "GameDiscovery.inspectPath", "GameDiscovery.recordLaunch",
               "GameDiscovery.savePaths", "MAX_DEPTH"):
    if marker not in test_source:
        raise SystemExit("JVM regression suite is missing coverage marker: " + marker)

print("Stage 11 static checks passed: XML/resource integrity, Android package/API contract, dependency pinning, CI wiring, and JVM coverage.")
print("Validated " + str(len(xml_paths)) + " Android XML files, " +
      str(len(string_references)) + " R.string references, and " +
      str(len(java_sources)) + " production Java files.")
PY
