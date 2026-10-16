#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
bash -n scripts/verify-stage4.sh
python3 - "$ROOT_DIR" <<'PY'
import pathlib, sys
import xml.etree.ElementTree as ET
root = pathlib.Path(sys.argv[1])
storage = (root / "src/me/nillerusr/StorageAccess.java").read_text(encoding="utf-8")
picker = (root / "src/me/nillerusr/DirchActivity.java").read_text(encoding="utf-8")
launcher = (root / "src/me/nillerusr/LauncherActivity.java").read_text(encoding="utf-8")
manifest = ET.parse(root / "AndroidManifest.xml").getroot()
strings_root = ET.parse(root / "res/values/strings.xml").getroot()
workflow = (root / ".github/workflows/build.yml").read_text(encoding="utf-8")
checks = {
    "SAF tree picker": "ACTION_OPEN_DOCUMENT_TREE" in storage and "createTreePickerIntent" in picker,
    "persistable read grant": "FLAG_GRANT_PERSISTABLE_URI_PERMISSION" in storage and "takePersistableUriPermission" in storage,
    "persisted grant revalidation": "getPersistedUriPermissions()" in storage and "permission.isReadPermission()" in storage,
    "provider allowlist avoids fake URI paths": '"com.android.externalstorage.documents".equals(uri.getAuthority())' in storage,
    "safe document ID parsing": "indexOf(':')" in storage and 'volume.contains("/")' in storage,
    "canonical path containment": "getCanonicalPath()" in storage and 'canonicalCandidate.startsWith(canonicalBase + File.separator)' in storage,
    "mounted/removable and read-only media check": "getExternalStorageState(base)" in storage and "!Environment.MEDIA_MOUNTED.equals(state)" in storage and "candidate.canWrite()" in storage,
    "disconnected/revoked storage is revalidated": "resolveFilesystemPath(context, uri)" in storage and "clearSelection(context)" in storage,
    "persist selected URI and real path": "TREE_URI_KEY" in storage and "TREE_PATH_KEY" in storage and "putString(TREE_URI_KEY" in storage,
    "no broad /storage volume guessing": 'new File("/storage/").listFiles()' not in picker and 'new File("/storage/")' not in picker,
    "legacy fallback stays within primary shared storage": "Environment.getExternalStorageDirectory().getAbsolutePath()" in picker,
    "picker returns selected folder to launcher": "setResult(RESULT_OK, result)" in picker and 'getStringExtra("selected_path")' in launcher,
    "path normalization before launch": "gamepath = canonicalPath(gamepath);" in launcher and "getCanonicalPath()" in launcher,
    "path spaces and Unicode passed without shell concatenation": 'putExtra("gamepath", gamepath)' in launcher,
    "scan after folder selection": "scanLibrary(true, true);" in launcher,
    "launch rejects unavailable root": "!root.isDirectory() || !root.canRead()" in launcher,
    "persisted access checked before launch": "StorageAccess.getValidatedSelectedPath(this)" in launcher and "srceng_storage_revoked" in launcher,
    "SAF is documented as native filesystem bridge": "real filesystem path" in strings_root.find("./string[@name='srceng_storage_saf_info']").text,
    "workflow runs Stage 4 verifier": "bash scripts/verify-stage4.sh" in workflow,
}
missing = [name for name, ok in checks.items() if not ok]
if missing:
    raise SystemExit("Stage 4 static checks failed: " + ", ".join(missing))
resources = {item.attrib.get("name") for item in strings_root}
required = [
    "srceng_storage_picker_explanation", "srceng_storage_choose_folder", "srceng_storage_saf_info",
    "srceng_storage_error_title", "srceng_storage_picker_unavailable", "srceng_storage_unsupported_provider",
    "srceng_storage_unavailable", "srceng_storage_revoked", "srceng_storage_selection_cancelled",
]
missing_resources = [name for name in required if name not in resources]
if missing_resources:
    raise SystemExit("Missing Stage 4 strings: " + ", ".join(missing_resources))
print("Stage 4 static checks passed: " + str(len(checks)) + " storage/path assertions.")
PY
