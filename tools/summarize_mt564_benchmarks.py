#!/usr/bin/env python3
"""Validate MT564 JMH JSON and generate deterministic CSV/Markdown summaries."""

from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path


THREADS = (1, 2, 4, 8)
SCENARIOS = ("validBaseline", "singleViolation", "multipleViolations", "largeStructure")
METHODS = (
    "interpreterCore_Mt564",
    "generatedDirect_Mt564",
    "interpreterEndToEnd_Mt564",
    "generatedEndToEnd_Mt564",
)


def metric(result: dict, name: str) -> str:
    value = result.get("secondaryMetrics", {}).get(name, {}).get("score")
    return "" if value is None else f"{value:.6f}"


def load_rows(result_dir: Path) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    for threads in THREADS:
        path = result_dir / f"mt564-jmh-{threads}-thread.json"
        results = json.loads(path.read_text(encoding="utf-8"))
        expected = len(SCENARIOS) * len(METHODS) * 2
        if len(results) != expected:
            raise ValueError(f"{path}: expected {expected} records, found {len(results)}")
        for result in results:
            method = result["benchmark"].rsplit(".", 1)[-1]
            scenario = result.get("params", {}).get("scenario")
            if result["threads"] != threads or method not in METHODS or scenario not in SCENARIOS:
                raise ValueError(f"{path}: unexpected benchmark metadata in {method}/{scenario}")
            primary = result["primaryMetric"]
            rows.append({
                "threads": str(threads),
                "scenario": scenario,
                "method": method,
                "mode": result["mode"],
                "score": f"{primary['score']:.6f}",
                "score_error": f"{primary['scoreError']:.6f}",
                "unit": primary["scoreUnit"],
                "allocation_bytes_per_op": metric(result, "gc.alloc.rate.norm"),
                "gc_count": metric(result, "gc.count"),
                "gc_time_ms": metric(result, "gc.time"),
            })
    return sorted(rows, key=lambda row: (row["scenario"], row["method"], row["mode"], int(row["threads"])))


def write_csv(rows: list[dict[str, str]], path: Path) -> None:
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=rows[0].keys(), lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)


def write_markdown(rows: list[dict[str, str]], result_dir: Path, path: Path) -> None:
    metadata = (result_dir / "mt564-environment.properties").read_text(encoding="utf-8").strip()
    lines = [
        "# DQ-004 MT564 reference benchmark",
        "",
        "Generated deterministically from the retained JMH JSON. Scores are observations from one host,",
        "not service-capacity or SLA claims. A regression requires confirmation against established variance.",
        "",
        "| Scenario | Method | Mode | Threads | Score | Error | Unit | B/op | GC count | GC time (ms) |",
        "| --- | --- | --- | ---: | ---: | ---: | --- | ---: | ---: | ---: |",
    ]
    for row in rows:
        lines.append("| {scenario} | `{method}` | {mode} | {threads} | {score} | {score_error} | {unit} | "
                     "{allocation_bytes_per_op} | {gc_count} | {gc_time_ms} |".format(**row))
    lines.extend([
        "",
        "`largeStructure` currently measures normalized-message mapping and report adaptation. The current",
        "MT564 rules read three scalar fields and do not traverse its synthetic 128-element option collection.",
        "",
        "## Environment",
        "",
        "```properties",
        metadata,
        "```",
        "",
    ])
    path.write_text("\n".join(lines), encoding="utf-8", newline="\n")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("result_dir", type=Path)
    args = parser.parse_args()
    rows = load_rows(args.result_dir)
    write_csv(rows, args.result_dir / "mt564-summary.csv")
    write_markdown(rows, args.result_dir, args.result_dir / "mt564-summary.md")


if __name__ == "__main__":
    main()
