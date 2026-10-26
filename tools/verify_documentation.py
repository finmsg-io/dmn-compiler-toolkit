"""Fail CI when canonical documentation facts drift from repository metadata."""

from pathlib import Path
from collections import Counter
import json
import re
import sys
import xml.etree.ElementTree as ET
from documentation_links import documentation_link_errors


ROOT = Path(__file__).resolve().parents[1]
SPARK_BACKEND = "optimized-generated-sparksql-hybrid"
TCK_REVISION = "20274cd2ba9cad805db6114f331c743f4b2603a1"


def validate_spark_evidence(data: dict, page: str) -> list[str]:
    """Validate only the maintained Spark checkpoint, never dated history."""
    try:
        if data["revision"] != TCK_REVISION or TCK_REVISION not in page:
            raise ValueError("pinned TCK revision mismatch")
        if data["backends"] != [SPARK_BACKEND]:
            raise ValueError("expected the optimized Spark hybrid backend only")
        outcomes = data["outcomes"]
        denominator = data["inventory"]["decodedCases"]
        if denominator != 3391 or len(outcomes) != denominator:
            raise ValueError("expected all 3,391 pinned official cases")
        keys = set()
        counts = Counter()
        for outcome in outcomes:
            key = (outcome["entryId"], outcome["caseId"], outcome["backend"])
            if key in keys:
                raise ValueError(f"duplicate case key: {key}")
            keys.add(key)
            if outcome["backend"] != SPARK_BACKEND:
                raise ValueError("outcome backend does not match declared backend")
            if not outcome["entryId"].startswith(("compliance-level-2/", "compliance-level-3/")):
                raise ValueError("outcome is outside the declared CL2/CL3 roots")
            if not outcome["caseId"]:
                raise ValueError("empty case ID")
            counts[outcome["status"]] += 1
        if dict(counts) != {k: v for k, v in data["statusCounts"].items() if v != 0}:
            raise ValueError("status totals do not match case outcomes")
        # This published checkpoint specifically claims every case executed natively.
        if any(o["status"] != "PASSED" or o["diagnostic"] != "NATIVE" for o in outcomes):
            raise ValueError("all-native achievement requires every case to pass with NATIVE routing")
        block = page.split("### Native Spark SQL checkpoint - 2026-10-26\n", 1)[1]
        block = block.split("### Historical interpreter/Java baseline", 1)[0]
        expected = {"Native passed": (denominator, "100.00%"), "Fallback passed": (0, "0.00%"),
                    "Failed": (0, "0.00%"), "Compilation blocked": (0, "0.00%"),
                    "Execution / compilation errors": (0, "0.00%"), "Unknown routes": (0, "0.00%")}
        rows = {}
        for line in block.splitlines():
            if line.startswith("|"):
                cells = [cell.strip().replace("**", "") for cell in line.strip("|").split("|")]
                if len(cells) == 3 and cells[0] in expected:
                    if cells[0] in rows:
                        raise ValueError(f"duplicate metric row: {cells[0]}")
                    rows[cells[0]] = (int(cells[1].replace(",", "")), cells[2])
        if rows != expected:
            raise ValueError("current Spark result table does not match the retained ledger")
    except (KeyError, IndexError, TypeError, ValueError, AttributeError) as exc:
        return [f"docs/assets/tck-accounting-sparksql-native.json / docs/tck-conformance.md: {exc}"]
    return []


def spark_evidence_errors() -> list[str]:
    try:
        data = json.loads((ROOT / "docs/assets/tck-accounting-sparksql-native.json").read_text(encoding="utf-8"))
        page = (ROOT / "docs/tck-conformance.md").read_text(encoding="utf-8")
    except (OSError, ValueError) as exc:
        return [f"Spark conformance evidence unavailable or malformed: {exc}"]
    return validate_spark_evidence(data, page)


def reactor_modules() -> list[str]:
    root = ET.parse(ROOT / "pom.xml").getroot()
    namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
    return [node.text for node in root.findall("./m:modules/m:module", namespace) if node.text]


def documented_modules() -> list[str]:
    text = (ROOT / "docs" / "modules.md").read_text(encoding="utf-8")
    return re.findall(r"^## `([^`]+)`$", text, flags=re.MULTILINE)


def main() -> int:
    errors: list[str] = spark_evidence_errors()
    link_errors, page_count = documentation_link_errors(ROOT)
    errors.extend(link_errors)
    expected = reactor_modules()
    documented = documented_modules()
    if documented != expected:
        errors.append(
            "docs/modules.md module order does not match pom.xml:\n"
            f"  pom.xml: {expected}\n  docs:    {documented}"
        )

    living_pages = [
        ROOT / "docs" / "index.md",
        ROOT / "docs" / "modules.md",
        ROOT / "docs" / "todos" / "index.md",
    ]
    forbidden = re.compile(r"\bTCK certified\b|\bOMG certified\b", re.IGNORECASE)
    for page in living_pages:
        for line_number, line in enumerate(page.read_text(encoding="utf-8").splitlines(), 1):
            if forbidden.search(line):
                errors.append(f"{page.relative_to(ROOT)}:{line_number}: unsupported certification claim")

    if errors:
        print("Documentation verification failed:")
        print("\n".join(f"- {error}" for error in errors))
        return 1
    print(f"Documentation facts verified for {len(expected)} reactor modules; "
          "current Spark checkpoint matches 3,391 unique native passing outcomes. "
          f"Publication/orphan policy enforced; local links/fragments checked on {page_count} maintained pages and all navigation targets. "
          "Historical/proposal/ADR bodies and remote URLs are outside the link-check scope; "
          "architectural claims are not proved by these checks.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
