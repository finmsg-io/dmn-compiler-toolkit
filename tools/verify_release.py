#!/usr/bin/env python3
"""Validate Release R0 tag/version authority and attached Maven artifacts."""

from __future__ import annotations

import argparse
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
NAMESPACE = {"m": "http://maven.apache.org/POM/4.0.0"}
SEMVER_TAG = re.compile(r"^v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-rc\.([1-9]\d*))?$")


def text(root: ET.Element, path: str) -> str | None:
    node = root.find(path, NAMESPACE)
    return node.text.strip() if node is not None and node.text else None


def root_model() -> tuple[ET.Element, str, list[str]]:
    model = ET.parse(ROOT / "pom.xml").getroot()
    version = text(model, "./m:version")
    if version is None:
        raise ValueError("root pom.xml has no project version")
    modules = [node.text.strip() for node in model.findall("./m:modules/m:module", NAMESPACE) if node.text]
    return model, version, modules


def validate(tag: str, require_artifacts: bool = False) -> list[str]:
    errors: list[str] = []
    match = SEMVER_TAG.fullmatch(tag)
    _, version, modules = root_model()
    if match is None:
        errors.append(f"release tag must be strict SemVer vMAJOR.MINOR.PATCH or vMAJOR.MINOR.PATCH-rc.N: {tag}")
        tag_version = None
    else:
        tag_version = tag[1:]
    if tag_version is not None and version != tag_version:
        errors.append(f"tag version {tag_version} does not match root Maven version {version}")
    if version.endswith("-SNAPSHOT"):
        errors.append(f"release Maven version must not be a snapshot: {version}")

    for module in modules:
        pom = ROOT / module / "pom.xml"
        model = ET.parse(pom).getroot()
        parent_version = text(model, "./m:parent/m:version")
        project_version = text(model, "./m:version")
        if parent_version is not None and parent_version != version:
            errors.append(f"{pom.relative_to(ROOT)} parent version {parent_version} does not match {version}")
        if project_version is not None and project_version != version:
            errors.append(f"{pom.relative_to(ROOT)} project version {project_version} does not match {version}")

        if require_artifacts:
            deploy_skip = text(model, "./m:properties/m:maven.deploy.skip")
            if deploy_skip and deploy_skip.lower() == "true":
                continue
            artifact_id = text(model, "./m:artifactId")
            packaging = text(model, "./m:packaging") or "jar"
            if packaging == "jar" and artifact_id:
                target = ROOT / module / "target"
                classifiers = ["sources", "javadoc"]
                javadoc_skip = text(model, "./m:properties/m:maven.javadoc.skip")
                if javadoc_skip and javadoc_skip.lower() == "true":
                    classifiers.remove("javadoc")
                source_skip = text(model, "./m:properties/m:maven.source.skip")
                if source_skip and source_skip.lower() == "true":
                    classifiers.remove("sources")
                for classifier in classifiers:
                    artifact = target / f"{artifact_id}-{version}-{classifier}.jar"
                    if not artifact.is_file() or artifact.stat().st_size == 0:
                        errors.append(f"missing or empty release artifact: {artifact.relative_to(ROOT)}")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--tag", required=True)
    parser.add_argument("--artifacts", action="store_true")
    args = parser.parse_args()
    errors = validate(args.tag, args.artifacts)
    if errors:
        print("Release validation failed:")
        print("\n".join(f"- {error}" for error in errors))
        return 1
    print(f"Release authority verified for {args.tag} across the Maven reactor.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
