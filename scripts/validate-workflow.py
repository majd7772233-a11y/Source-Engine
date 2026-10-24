#!/usr/bin/env python3
"""Validate the Actions workflow structure, trigger policy, pinned actions, and quality gates."""
from pathlib import Path
import re

try:
    import yaml
except ImportError as exc:
    raise SystemExit("PyYAML is required; install python3-yaml before validating the workflow.") from exc

ROOT = Path(__file__).resolve().parents[1]
WORKFLOW_PATH = ROOT / ".github/workflows/build.yml"
try:
    workflow = yaml.load(WORKFLOW_PATH.read_text(encoding="utf-8"), Loader=yaml.BaseLoader)
except Exception as exc:
    raise SystemExit("Workflow YAML could not be parsed: " + str(exc)) from exc

def fail(message):
    raise SystemExit("Workflow validation failed: " + message)

if not isinstance(workflow, dict):
    fail("top-level workflow must be a mapping")
if not isinstance(workflow.get("jobs"), dict) or "build" not in workflow["jobs"]:
    fail("build job is missing")
triggers = workflow.get("on")
if not isinstance(triggers, dict):
    fail("workflow trigger mapping is missing")
for event in ("push", "pull_request", "workflow_dispatch"):
    if event not in triggers:
        fail("required trigger is missing: " + event)
if "android-fixes" not in (triggers["push"].get("branches") or []):
    fail("push trigger does not target android-fixes")
if "android-fixes" not in (triggers["pull_request"].get("branches") or []):
    fail("pull_request trigger does not target android-fixes")

steps = workflow["jobs"]["build"].get("steps")
if not isinstance(steps, list):
    fail("build job steps must be a list")
all_step_text = "\n".join(str(step) for step in steps)
runs = "\n".join(str(step.get("run", "")) for step in steps if isinstance(step, dict))
for stage in range(0, 17):
    command = "scripts/verify-stage" + str(stage) + ".sh"
    if command not in runs:
        fail("CI does not invoke " + command)
for required in ("scripts/test-java.sh", "scripts/validate-workflow.py",
                 "shellcheck", "actionlint", "Fetch native build dependencies at locked revisions"):
    if required not in all_step_text:
        fail("CI is missing quality gate: " + required)

for step in steps:
    uses = step.get("uses") if isinstance(step, dict) else None
    if not uses or str(uses).startswith("./") or str(uses).startswith("docker://"):
        continue
    if not re.search(r"@[0-9a-fA-F]{40}$", str(uses)):
        fail("third-party action is not pinned to a full commit SHA: " + str(uses))

debug_upload = next((step for step in steps if isinstance(step, dict) and step.get("name") == "Upload debug test APK and build report"), None)
if not isinstance(debug_upload, dict):
    fail("debug APK artifact upload step is missing")
debug_upload_condition = str(debug_upload.get("if", ""))
debug_upload_name = str((debug_upload.get("with") or {}).get("name", ""))
if "success()" not in debug_upload_condition or debug_upload_name != "source-engine-android-debug-test":
    fail("debug test artifact upload must require a successful build and use the test-only artifact name")
if not any(isinstance(step, dict) and step.get("if") == "success()" for step in steps):
    fail("expected-output processing must remain success-gated")
if not any(isinstance(step, dict) and isinstance(step.get("with"), dict)
           and step["with"].get("if-no-files-found") == "error" for step in steps):
    fail("artifact upload must fail when expected outputs are absent")

names = [str(step.get("name", "")) for step in steps]
try:
    unit_index = names.index("Run JVM tests and lint workflow")
    dependency_index = names.index("Fetch native build dependencies at locked revisions")
except ValueError:
    fail("unit-test/lint step or locked dependency step is missing")
if unit_index >= dependency_index:
    fail("Java tests and workflow lint must run before expensive native dependency fetch")
print("Workflow validation passed: YAML structure, canonical triggers, SHA-pinned actions, stage verifiers, and success-gated artifacts.")
