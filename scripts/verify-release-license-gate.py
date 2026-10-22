#!/usr/bin/env python3
"""Fail-closed guard for signed Source Engine releases."""
import datetime as dt
import re
import sys
from pathlib import Path
from urllib.parse import urlparse

DEFAULT_REPORT = Path(__file__).resolve().parents[1] / "docs" / "THIRD-PARTY-LICENSE-AUDIT.md"
SHA1_RE = re.compile(r"^[0-9a-f]{40}$")
DATE_RE = re.compile(r"^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
PLACEHOLDERS = {"", "pending", "tbd", "todo", "unknown", "none", "n/a", "<pending>"}


def read_fields(path: Path) -> dict[str, str]:
    fields: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if ":" not in line:
            continue
        key, value = line.split(":", 1)
        key = key.strip().lstrip("*").strip()
        if key in {"Release clearance", "Review completed by", "Review date", "Review commit", "Evidence URL"}:
            fields[key] = value.strip().strip("*").strip()
    return fields


def validate(path: Path) -> list[str]:
    errors: list[str] = []
    try:
        fields = read_fields(path)
    except OSError as exc:
        return [f"cannot read audit report ({exc.__class__.__name__})"]

    if fields.get("Release clearance", "").upper() != "CLEARED":
        errors.append("Release clearance must be exactly CLEARED")
    reviewer = fields.get("Review completed by", "")
    if reviewer.casefold() in PLACEHOLDERS or len(reviewer) < 3:
        errors.append("a named reviewer is required")
    review_date = fields.get("Review date", "")
    if not DATE_RE.fullmatch(review_date):
        errors.append("review date must use YYYY-MM-DD")
    else:
        try:
            dt.date.fromisoformat(review_date)
        except ValueError:
            errors.append("review date is not a valid calendar date")
    if not SHA1_RE.fullmatch(fields.get("Review commit", "")):
        errors.append("reviewed commit must be a full 40-character lowercase SHA")
    evidence = fields.get("Evidence URL", "")
    parsed = urlparse(evidence)
    if parsed.scheme not in {"http", "https"} or not parsed.netloc or evidence.casefold() in PLACEHOLDERS:
        errors.append("a non-placeholder HTTP(S) evidence URL is required")
    return errors


def main() -> int:
    report = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_REPORT
    problems = validate(report)
    if problems:
        print("RELEASE BLOCKED: third-party license/source-provenance clearance is not complete.")
        for problem in problems:
            print(f"- {problem}")
        print(f"Audit report: {report}")
        return 2
    print(f"License review record fields are complete: {report}")
    print("This guard validates record fields only; reviewers remain responsible for the truth of the clearance.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
