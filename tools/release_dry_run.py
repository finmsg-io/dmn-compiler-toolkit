#!/usr/bin/env python3
"""Execute a complete release preflight / dry-run check across all quality, governance, and packaging gates."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from typing import List, Dict, Any, Optional

ROOT = Path(__file__).resolve().parents[1]
NAMESPACE = {"m": "http://maven.apache.org/POM/4.0.0"}
SEMVER_TAG = re.compile(r"^v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-rc\.([1-9]\d*))?$")


def check_reactor_pom_versions(simulated_version: Optional[str] = None) -> List[str]:
    errors: List[str] = []
    root_pom = ROOT / "pom.xml"
    if not root_pom.is_file():
        return ["root pom.xml not found"]

    model = ET.parse(root_pom).getroot()
    v_node = model.find("./m:version", NAMESPACE)
    root_version = v_node.text.strip() if v_node is not None and v_node.text else None
    if not root_version:
        return ["root pom.xml missing version"]

    expected_version = simulated_version if simulated_version else root_version
    if not simulated_version and root_version.endswith("-SNAPSHOT"):
        errors.append(f"Root version is a SNAPSHOT: {root_version} (use --simulate-version for non-release checks)")

    modules = [node.text.strip() for node in model.findall("./m:modules/m:module", NAMESPACE) if node.text]
    for mod in modules:
        mod_pom = ROOT / mod / "pom.xml"
        if not mod_pom.is_file():
            errors.append(f"Module pom missing: {mod_pom}")
            continue
        mod_model = ET.parse(mod_pom).getroot()
        parent_v_node = mod_model.find("./m:parent/m:version", NAMESPACE)
        parent_version = parent_v_node.text.strip() if parent_v_node is not None and parent_v_node.text else None
        if parent_version and not simulated_version and parent_version != root_version:
            errors.append(f"{mod} parent version {parent_version} != {root_version}")

    return errors


def check_tck_conformance_gate() -> List[str]:
    errors: List[str] = []
    tck_accounting_file = ROOT / "dmn-tck-runner" / "tck-accounting.json"
    if not tck_accounting_file.is_file():
        errors.append("dmn-tck-runner/tck-accounting.json is missing")
        return errors

    try:
        data = json.loads(tck_accounting_file.read_text(encoding="utf-8"))
        counts = data.get("statusCounts", {})
        passed = counts.get("PASSED", 0)
        failed = counts.get("FAILED", 0)
        skipped = counts.get("SKIPPED", 0)
        if failed > 0 or skipped > 0:
            errors.append(f"TCK conformance gate failed: {failed} failed, {skipped} skipped (expected 100% pass)")
        if passed < 6000:
            errors.append(f"TCK pass count unexpectedly low: {passed} < 6000")
    except Exception as e:
        errors.append(f"Error reading tck-accounting.json: {e}")

    return errors


def check_changelog_section(version: str) -> List[str]:
    errors: List[str] = []
    changelog_file = ROOT / "CHANGELOG.md"
    if not changelog_file.is_file():
        errors.append("CHANGELOG.md is missing")
        return errors

    content = changelog_file.read_text(encoding="utf-8")
    clean_v = version.lstrip("v").replace("-SNAPSHOT", "")
    # Look for [clean_v] or ## [clean_v]
    if f"[{clean_v}]" not in content and f"## {clean_v}" not in content:
        errors.append(f"CHANGELOG.md has no release section for version {clean_v}")

    return errors


def check_documentation_gate() -> List[str]:
    errors: List[str] = []
    script = ROOT / "tools" / "verify_documentation.py"
    if not script.is_file():
        errors.append("tools/verify_documentation.py is missing")
        return errors

    res = subprocess.run([sys.executable, str(script)], capture_output=True, text=True, cwd=str(ROOT))
    if res.returncode != 0:
        errors.append(f"verify_documentation.py failed: {res.stdout.strip()} {res.stderr.strip()}")
    return errors


def run_preflight_checks(simulated_version: Optional[str] = None, tag: Optional[str] = None) -> Dict[str, Any]:
    effective_version = simulated_version or (tag[1:] if tag and tag.startswith("v") else None)
    
    results = {
        "pom_versions": check_reactor_pom_versions(effective_version),
        "tck_conformance": check_tck_conformance_gate(),
        "changelog": check_changelog_section(effective_version or "1.0.0"),
        "documentation": check_documentation_gate(),
    }
    
    all_errors = []
    for gate, gate_errors in results.items():
        all_errors.extend(gate_errors)
    
    results["all_errors"] = all_errors
    results["passed"] = len(all_errors) == 0
    return results


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(encoding="utf-8")

    parser = argparse.ArgumentParser(description="Run complete release train preflight / dry-run.")
    parser.add_argument("--tag", help="Release Git tag (e.g. v1.0.0)")
    parser.add_argument("--simulate-version", default="1.0.0", help="Simulate release version check (default: 1.0.0)")
    args = parser.parse_args()

    print("=" * 70)
    print("DMN COMPILER TOOLKIT - RELEASE TRAIN PREFLIGHT DRY RUN")
    print("=" * 70)

    res = run_preflight_checks(simulated_version=args.simulate_version, tag=args.tag)

    print(f"1. Reactor POM Versions:     {'[PASS]' if not res['pom_versions'] else '[FAIL]'}")
    if res['pom_versions']:
        for e in res['pom_versions']:
            print(f"   - {e}")

    print(f"2. TCK 100% Conformance Gate: {'[PASS]' if not res['tck_conformance'] else '[FAIL]'}")
    if res['tck_conformance']:
        for e in res['tck_conformance']:
            print(f"   - {e}")

    print(f"3. Changelog Release Entry:   {'[PASS]' if not res['changelog'] else '[FAIL]'}")
    if res['changelog']:
        for e in res['changelog']:
            print(f"   - {e}")

    print(f"4. Documentation Consistency: {'[PASS]' if not res['documentation'] else '[FAIL]'}")
    if res['documentation']:
        for e in res['documentation']:
            print(f"   - {e}")

    print("=" * 70)
    if res["passed"]:
        print("✅ ALL PREFLIGHT GATES PASSED. Ready for release staging / publication.")
        return 0
    else:
        print(f"❌ PREFLIGHT FAILED with {len(res['all_errors'])} errors.")
        return 1


if __name__ == "__main__":
    sys.exit(main())
