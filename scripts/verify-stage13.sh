#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
bash -n scripts/verify-stage13.sh
python3 - "$ROOT_DIR" <<'PY'
import pathlib,sys
import xml.etree.ElementTree as ET
r=pathlib.Path(sys.argv[1]);s=(r/"src/me/nillerusr/StorageAccess.java").read_text();p=(r/"src/me/nillerusr/DirchActivity.java").read_text();l=(r/"src/me/nillerusr/LauncherActivity.java").read_text();t=(r/"scripts/test-java.sh").read_text();w=(r/".github/workflows/build.yml").read_text()
strings={q:{x.attrib.get("name") for x in ET.parse(r/q).getroot().findall("string")} for q in ("res/values/strings.xml","res/values-ar/strings.xml")}
checks={
 "picker requests persisted read/write tree grants":all(x in s for x in ("ACTION_OPEN_DOCUMENT_TREE","FLAG_GRANT_READ_URI_PERMISSION","FLAG_GRANT_WRITE_URI_PERMISSION","FLAG_GRANT_PERSISTABLE_URI_PERMISSION","FLAG_GRANT_PREFIX_URI_PERMISSION")),
 "returned write grant is required":"ERROR_WRITE_PERMISSION_MISSING" in s and "grants & Intent.FLAG_GRANT_WRITE_URI_PERMISSION" in s,
 "persisted read/write grants are verified":"hasPersistedReadWriteGrant(context, uri)" in s and "persistedGrantFlags(context, uri)" in s,
 "read-only media is explicitly rejected":"MEDIA_MOUNTED_READ_ONLY.equals(state)" in s and "new PathResult(ERROR_NOT_WRITABLE, null)" in s,
 "revoked and stale selections are cleared":"ERROR_GRANT_REVOKED" in s and "validateSelectedTree(Context context)" in s and "clearSelection(context);" in s,
 "canonical path containment remains enforced":"getCanonicalPath()" in s and "ERROR_PATH_OUTSIDE_VOLUME" in s,
 "document tree IDs cannot traverse between sibling folders":"hasTraversalSegment(relative)" in s and '".." .equals(segment)' not in s and '"..".equals(segment)' in s,
 "diagnostics include permission/volume/access/failure evidence":all(x in s for x in ("returnedRead=","returnedWrite=","persistedRead=","persistedWrite=","volumeState=","directoryReadable=","directoryWritable=","mappingReason=","selectionResult=")),
 "diagnostics do not print full URI":"uri.toString()" not in s[s.index("public static String diagnosticSummary"):s.index("private static boolean hasFlag")],
 "picker logs privacy-safe diagnostics":"SRCAPK_STORAGE" in p and "StorageAccess.diagnosticSummary" in p,
 "launch validation maps concrete failure reasons":"StorageAccess.validateSelectedTree(this)" in l and "storageValidationMessage(validation.error)" in l,
 "launch validation keeps unsupported-provider and malformed-document errors actionable":all(x in l for x in ("case StorageAccess.ERROR_UNSUPPORTED_PROVIDER","R.string.srceng_storage_unsupported_provider","case StorageAccess.ERROR_INVALID_DOCUMENT_ID","R.string.srceng_storage_invalid_document_id")),
 "launch validation cannot be skipped by a partial URI/path preference pair":"StorageAccess.hasStoredTreeSelection(this)" in l and "public static boolean hasStoredTreeSelection(Context context)" in s and "TREE_URI_KEY" in s and "TREE_PATH_KEY" in s,
 "JVM suite compiles bridge and runs Stage 13 tests":"src/me/nillerusr/StorageAccess.java" in t and "Stage13StorageAccessTestSuite" in t and "selectionState()" in (r/"tests/java/me/nillerusr/Stage13StorageAccessTestSuite.java").read_text(),
 "successful replacement stores the new selection before scoped grant pruning":s.index("preferences.edit().putString(TREE_URI_KEY, uri.toString())") < s.index("releaseSupersededTreeGrants(context, uri)") and "static void releaseSupersededTreeGrants" in s[s.index("private static final class Api19"):s.index("/** API 21+")] and "replacementReleasesOldGrant()" in (r/"tests/java/me/nillerusr/Stage13StorageAccessTestSuite.java").read_text(),
 "grant pruning targets only obsolete external-storage tree URIs":"\"com.android.externalstorage.documents\".equals(grantedUri.getAuthority())" in s and "path.startsWith(\"/tree/\")" in s and "Api19.releaseSupersededTreeGrants(context, null)" in s and "orphan external-storage tree grant pruned" in (r/"tests/java/me/nillerusr/Stage13StorageAccessTestSuite.java").read_text(),
 "API-19 UriPermission types remain isolated from the API-17-compatible outer class": "android.content.UriPermission" not in s[:s.index("private static final class Api19")] and "private static void releaseSupersededTreeGrants" not in s and "static void releaseSupersededTreeGrants" in s[s.index("private static final class Api19"):s.index("/** API 21+")],
 "workflow invokes Stage 13 verifier":"bash scripts/verify-stage13.sh" in w,
 "failed replacements retain previous selection and grant":"failedReplacementPreservesSelection()" in (r/"tests/java/me/nillerusr/Stage13StorageAccessTestSuite.java").read_text() and "all failed replacements retain active URI" in (r/"tests/java/me/nillerusr/Stage13StorageAccessTestSuite.java").read_text(),
 "selection cleanup prunes only external-storage tree grants":"clearSelectionScopesGrantCleanup()" in (r/"tests/java/me/nillerusr/Stage13StorageAccessTestSuite.java").read_text() and "clear leaves unrelated provider grants untouched" in (r/"tests/java/me/nillerusr/Stage13StorageAccessTestSuite.java").read_text(),
 "new missing-write message is localized in English and Arabic":all("srceng_storage_no_write_grant" in v for v in strings.values())}
bad=[k for k,v in checks.items() if not v]
if bad:raise SystemExit("Stage 13 static checks failed: "+", ".join(bad))
print("Stage 13 static checks passed: "+str(len(checks))+" assertions.")
PY
