#!/usr/bin/env python3
"""Compute and verify SHA-256 and SHA-512 checksums for release artifacts."""

from __future__ import annotations

import argparse
import hashlib
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]


def compute_hash(file_path: Path, algorithm: str = "sha256") -> str:
    hasher = getattr(hashlib, algorithm)()
    with file_path.open("rb") as f:
        while chunk := f.read(65536):
            hasher.update(chunk)
    return hasher.hexdigest()


def find_release_artifacts(root_dir: Path) -> list[Path]:
    artifacts: list[Path] = []
    # Collect all release jars and poms from target directories
    for path in sorted(root_dir.glob("**/target/*.jar")):
        if not path.name.endswith("-tests.jar"):
            artifacts.append(path)
    for pom in sorted(root_dir.glob("**/pom.xml")):
        artifacts.append(pom)
    return sorted(set(artifacts))


def generate_checksum_file(artifacts: list[Path], base_dir: Path, algorithm: str = "sha256") -> str:
    lines: list[str] = []
    for artifact in artifacts:
        digest = compute_hash(artifact, algorithm)
        rel_path = artifact.relative_to(base_dir).as_posix()
        lines.append(f"{digest}  {rel_path}")
    return "\n".join(lines) + "\n"


def verify_checksum_file(checksum_file: Path, base_dir: Path, algorithm: str = "sha256") -> list[str]:
    errors: list[str] = []
    text = checksum_file.read_text(encoding="utf-8")
    for line_no, line in enumerate(text.splitlines(), 1):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split(maxsplit=1)
        if len(parts) != 2:
            errors.append(f"{checksum_file}:{line_no}: invalid line format: {line}")
            continue
        expected_hash, rel_path = parts[0], parts[1].lstrip("*")
        file_path = base_dir / rel_path
        if not file_path.is_file():
            errors.append(f"{checksum_file}:{line_no}: missing file: {rel_path}")
            continue
        actual_hash = compute_hash(file_path, algorithm)
        if actual_hash.lower() != expected_hash.lower():
            errors.append(
                f"{checksum_file}:{line_no}: hash mismatch for {rel_path} "
                f"(expected {expected_hash}, got {actual_hash})"
            )
    return errors


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(encoding="utf-8")

    parser = argparse.ArgumentParser(description="Generate or verify release checksums")
    parser.add_argument("--dir", type=Path, default=ROOT, help="Base directory")
    parser.add_argument("--algorithm", choices=["sha256", "sha512"], default="sha256")
    parser.add_argument("--output", type=Path, help="Output checksum file path")
    parser.add_argument("--verify", type=Path, help="Verify existing checksum file")
    args = parser.parse_args()

    if args.verify:
        errors = verify_checksum_file(args.verify, args.dir, args.algorithm)
        if errors:
            print("Checksum verification failed:", file=sys.stderr)
            for error in errors:
                print(f"- {error}", file=sys.stderr)
            return 1
        print(f"Checksum verification succeeded for {args.verify}")
        return 0

    artifacts = find_release_artifacts(args.dir)
    if not artifacts:
        print("No release artifacts found.", file=sys.stderr)
        return 1

    checksum_text = generate_checksum_file(artifacts, args.dir, args.algorithm)
    if args.output:
        args.output.write_text(checksum_text, encoding="utf-8", newline="\n")
        print(f"Generated {len(artifacts)} checksum entries to {args.output}")
    else:
        print(checksum_text, end="")
    return 0


if __name__ == "__main__":
    sys.exit(main())
