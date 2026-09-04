#!/usr/bin/env python3
"""Extract categorized release notes from CHANGELOG.md for a given SemVer tag."""

from __future__ import annotations

import argparse
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]


def extract_notes(changelog_path: Path, tag: str) -> str:
    text = changelog_path.read_text(encoding="utf-8")
    version = tag.lstrip("v")

    if tag.lower() == "unreleased":
        pattern = re.compile(r"^##\s+\[Unreleased\]\s*\n(.*?)(?=^##\s+\[|\Z)", re.MULTILINE | re.DOTALL)
    else:
        escaped_ver = re.escape(version)
        pattern = re.compile(
            r"^##\s+\[" + escaped_ver + r"\](?:\s+[-—]\s+[^\n]+)?\s*\n(.*?)(?=^##\s+\[|\Z)",
            re.MULTILINE | re.DOTALL,
        )

    match = pattern.search(text)
    if not match:
        raise ValueError(f"No release notes found in {changelog_path} for version {version} (tag: {tag})")

    notes = match.group(1).strip()
    if not notes:
        raise ValueError(f"Release notes for {tag} in {changelog_path} are empty")

    return notes


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(encoding="utf-8")

    parser = argparse.ArgumentParser(description="Extract release notes from CHANGELOG.md")
    parser.add_argument("--tag", required=True, help="Release tag (e.g. v1.0.0 or unreleased)")
    parser.add_argument("--changelog", type=Path, default=ROOT / "CHANGELOG.md", help="Path to CHANGELOG.md")
    parser.add_argument("--output", type=Path, help="Optional output file path")
    args = parser.parse_args()

    try:
        notes = extract_notes(args.changelog, args.tag)
    except Exception as exc:
        print(f"Error extracting release notes: {exc}", file=sys.stderr)
        return 1

    header = f"# Release {args.tag}\n\n"
    full_output = header + notes + "\n"

    if args.output:
        args.output.write_text(full_output, encoding="utf-8", newline="\n")
        print(f"Release notes written to {args.output}")
    else:
        print(full_output)

    return 0


if __name__ == "__main__":
    sys.exit(main())
