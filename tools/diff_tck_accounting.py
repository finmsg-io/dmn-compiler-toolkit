#!/usr/bin/env python3
"""Compare two TCK accounting JSON files and report regressions or improvements."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys


def diff_accounting(baseline_data: dict, current_data: dict) -> dict:
    baseline_outcomes = {(o["entryId"], o["caseId"], o["backend"]): o for o in baseline_data.get("outcomes", [])}
    current_outcomes = {(o["entryId"], o["caseId"], o["backend"]): o for o in current_data.get("outcomes", [])}

    regressions = []
    improvements = []
    added = []
    removed = []

    for key, curr in current_outcomes.items():
        if key not in baseline_outcomes:
            added.append(curr)
        else:
            base = baseline_outcomes[key]
            if base["status"] == "PASSED" and curr["status"] != "PASSED":
                regressions.append({"key": key, "before": base["status"], "after": curr["status"], "diagnostic": curr.get("diagnostic", "")})
            elif base["status"] != "PASSED" and curr["status"] == "PASSED":
                improvements.append({"key": key, "before": base["status"], "after": curr["status"]})

    for key, base in baseline_outcomes.items():
        if key not in current_outcomes:
            removed.append(base)

    return {
        "regressions": regressions,
        "improvements": improvements,
        "added": added,
        "removed": removed,
        "baseline_passed": baseline_data.get("statusCounts", {}).get("PASSED", 0),
        "current_passed": current_data.get("statusCounts", {}).get("PASSED", 0),
    }


def format_diff_report(diff: dict) -> str:
    lines = ["# TCK Accounting Comparison Report", ""]
    lines.append(f"- **Baseline Passed**: {diff['baseline_passed']}")
    lines.append(f"- **Current Passed**: {diff['current_passed']}")
    lines.append(f"- **Net Improvement**: +{diff['current_passed'] - diff['baseline_passed']}")
    lines.append("")

    if diff["regressions"]:
        lines.append(f"## ❌ Regressions ({len(diff['regressions'])})")
        for reg in diff["regressions"]:
            eid, cid, backend = reg["key"]
            lines.append(f"- `{eid}` [case {cid}] ({backend}): {reg['before']} ➔ **{reg['after']}**")
            if reg["diagnostic"]:
                lines.append(f"  - *Diagnostic*: {reg['diagnostic']}")
        lines.append("")
    else:
        lines.append("## \u2705 No Regressions Detected\n")

    if diff["improvements"]:
        lines.append(f"## 🎉 Improvements ({len(diff['improvements'])})")
        for imp in diff["improvements"][:20]:
            eid, cid, backend = imp["key"]
            lines.append(f"- `{eid}` [case {cid}] ({backend}): {imp['before']} ➔ **{imp['after']}**")
        if len(diff["improvements"]) > 20:
            lines.append(f"... and {len(diff['improvements']) - 20} more improvements")
        lines.append("")

    return "\n".join(lines) + "\n"


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(encoding="utf-8")

    parser = argparse.ArgumentParser(description="Compare two TCK accounting JSON runs")
    parser.add_argument("--baseline", type=Path, required=True, help="Baseline tck-accounting.json")
    parser.add_argument("--current", type=Path, required=True, help="Current tck-accounting.json")
    parser.add_argument("--output", type=Path, help="Optional Markdown report output path")
    args = parser.parse_args()

    if not args.baseline.is_file():
        print(f"Error: baseline {args.baseline} not found.", file=sys.stderr)
        return 1
    if not args.current.is_file():
        print(f"Error: current {args.current} not found.", file=sys.stderr)
        return 1

    base_data = json.loads(args.baseline.read_text(encoding="utf-8"))
    curr_data = json.loads(args.current.read_text(encoding="utf-8"))

    diff = diff_accounting(base_data, curr_data)
    report = format_diff_report(diff)

    if args.output:
        args.output.write_text(report, encoding="utf-8", newline="\n")
        print(f"Diff report written to {args.output}")
    else:
        print(report)

    return 1 if diff["regressions"] else 0


if __name__ == "__main__":
    sys.exit(main())
