#!/usr/bin/env python3
"""Conservatively select full reactor/TCK verification from changed paths."""
import argparse
import re
import subprocess
import sys


def needs_heavy(paths):
    for path in paths:
        if path.startswith(("docs/", "prompts/", "tools/", ".github/")):
            continue
        if path in {"mkdocs.yml", "requirements-docs.txt"}:
            continue
        if "/" not in path and path.endswith(".md"):
            continue
        return True
    return False


def inspect(base):
    if not re.fullmatch(r"[0-9a-fA-F]{40}", base or "") or set(base) == {"0"}:
        return True
    try:
        changed = subprocess.check_output(["git", "diff", "--no-renames", "--name-only", "-z", base, "HEAD"])
    except (OSError, subprocess.CalledProcessError):
        return True
    return needs_heavy([p.decode("utf-8", errors="replace") for p in changed.split(b"\0") if p])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="")
    args = parser.parse_args()
    heavy = inspect(args.base)
    print("heavy=" + str(heavy).lower())
    print("Full reactor/TCK required" if heavy else "Documentation/tooling checks only", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
