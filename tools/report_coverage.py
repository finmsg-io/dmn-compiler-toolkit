"""Report default-reactor handwritten-code coverage after a clean verify run.

This reads JaCoCo XML, not source/test line-count estimates. It does not set or
approve coverage thresholds. Opt-in TCK results must be reported separately.
"""

import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]


def coverage_counter(report, kind):
    counters = report.findall(f"counter[@type='{kind}']")
    if not counters:
        return {"covered": 0, "missed": 0, "total": 0, "percent": None}
    if len(counters) != 1:
        raise ValueError(f"duplicate {kind} report counters")
    covered = int(counters[0].attrib["covered"])
    missed = int(counters[0].attrib["missed"])
    if min(covered, missed) < 0:
        raise ValueError(f"negative {kind} counter")
    total = covered + missed
    return {"covered": covered, "missed": missed, "total": total,
            "percent": round(100 * covered / total, 2) if total else None}


def collect(root):
    pom = ET.parse(root / "pom.xml").getroot()
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    modules = [node.text for node in pom.findall("./m:modules/m:module", ns)]
    result = []
    for name in modules:
        module = root / name
        handwritten = list((module / "src/main/java").rglob("*.java"))
        if not handwritten:
            result.append({"module": name, "status": "N/A",
                           "reason": "No handwritten Java under src/main/java (generated-only or test-only module)"})
            continue
        report_path = module / "target/site/jacoco/jacoco.xml"
        if not report_path.is_file():
            raise ValueError(f"{name}: missing JaCoCo XML; run a clean default-reactor verify")
        report = ET.parse(report_path).getroot()
        if report.tag != "report":
            raise ValueError(f"{name}: not a JaCoCo report")
        tests = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
        reports = list((module / "target/surefire-reports").glob("TEST-*.xml"))
        if not reports:
            raise ValueError(f"{name}: missing test outcomes for coverage baseline")
        for path in reports:
            suite = ET.parse(path).getroot()
            for key in tests:
                tests[key] += int(suite.attrib.get(key, 0))
        if tests["failures"] or tests["errors"]:
            raise ValueError(f"{name}: failing tests cannot establish the baseline")
        classes = {node.attrib["name"] for node in report.findall("package/class")}
        if not classes:
            raise ValueError(f"{name}: no handwritten classes in the coverage report")
        for node in report.findall("package/class"):
            line = node.find("counter[@type='LINE']")
            if line is not None and int(line.get("covered", 0)) + int(line.get("missed", 0)) > 0:
                package = node.attrib["name"].rsplit("/", 1)[0]
                source = module / "src/main/java" / package / node.attrib.get("sourcefilename", "")
                if not source.is_file():
                    raise ValueError(f"{name}: generated/unclassified executable class remains: {node.attrib['name']}")
        if name == "dmn-feel-parser":
            generated = {"FeelLexer", "FeelParser", "FeelParserVisitor", "FeelParserBaseVisitor"}
            if any(c.rsplit("/", 1)[-1].split("$", 1)[0] in generated for c in classes):
                raise ValueError("dmn-feel-parser: generated ANTLR classes are still in the report")
            if "io/finmsg/dmn/feel/parser/FeelParserFacade" not in classes:
                raise ValueError("dmn-feel-parser: handwritten FeelParserFacade was accidentally excluded")
        result.append({"module": name, "status": "measured", "line": coverage_counter(report, "LINE"),
                       "branch": coverage_counter(report, "BRANCH"), "testOutcomes": tests})
    return {"scope": "default reactor, module-own test JVMs, handwritten Java, clean verify; not downstream-aggregated; excludes opt-in TCK",
            "command": "mvn --batch-mode --no-transfer-progress clean verify", "modules": result}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, help="Write the machine-readable baseline")
    args = parser.parse_args()
    try:
        result = collect(ROOT)
    except (OSError, ET.ParseError, ValueError, KeyError) as exc:
        parser.exit(1, f"Coverage measurement failed: {exc}\n")
    if args.output:
        args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    for module in result["modules"]:
        if module["status"] == "N/A":
            print(f"{module['module']}: N/A - {module['reason']}")
        else:
            line = module["line"]["percent"]
            branch = module["branch"]["percent"]
            line_text = f"{line}%" if line is not None else "N/A"
            branch_text = f"{branch}%" if branch is not None else "N/A"
            print(f"{module['module']}: line={line_text}, branch={branch_text}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
