#!/usr/bin/env python3
"""Validate and summarize multi-threaded scalability JMH benchmark results."""

from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path
import sys
from typing import Any, Dict, List, Optional


def metric(result: dict, name: str) -> str:
    value = result.get("secondaryMetrics", {}).get(name, {}).get("score")
    return "" if value is None else f"{value:.2f}"


def load_benchmark_records(json_paths: List[Path]) -> List[Dict[str, Any]]:
    records: List[Dict[str, Any]] = []
    for path in json_paths:
        if not path.exists():
            continue
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except Exception:
            continue
        if not isinstance(data, list):
            continue
        for entry in data:
            if not isinstance(entry, dict):
                continue
            raw_name = entry.get("benchmark", "")
            method = raw_name.rsplit(".", 1)[-1] if "." in raw_name else raw_name
            threads = int(entry.get("threads", 1))
            mode = entry.get("mode", "thrpt")
            primary = entry.get("primaryMetric", {})
            score = float(primary.get("score", 0.0))
            score_error = float(primary.get("scoreError", 0.0))
            unit = primary.get("scoreUnit", "ops/s")
            
            params = entry.get("params", {})
            scenario = params.get("scenario", "default")

            records.append({
                "benchmark": raw_name,
                "method": method,
                "scenario": scenario,
                "threads": threads,
                "mode": mode,
                "score": score,
                "score_error": score_error,
                "unit": unit,
                "alloc_bytes_per_op": metric(entry, "gc.alloc.rate.norm"),
                "gc_count": metric(entry, "gc.count"),
                "gc_time_ms": metric(entry, "gc.time"),
            })
    return records


def compute_scalability_metrics(records: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    baselines: Dict[tuple, float] = {}
    for r in records:
        if r["threads"] == 1:
            key = (r["benchmark"], r["scenario"], r["mode"])
            baselines[key] = r["score"]

    results: List[Dict[str, Any]] = []
    for r in records:
        key = (r["benchmark"], r["scenario"], r["mode"])
        t1 = baselines.get(key, 0.0)
        n = r["threads"]
        if t1 > 0:
            scaling = r["score"] / t1
            efficiency = (scaling / n) * 100.0
        else:
            scaling = 1.0
            efficiency = 100.0

        item = dict(r)
        item["speedup_vs_1t"] = f"{scaling:.2f}x"
        item["parallel_efficiency"] = f"{efficiency:.1f}%"
        results.append(item)

    return sorted(results, key=lambda x: (x["method"], x["scenario"], x["mode"], x["threads"]))


def generate_markdown(results: List[Dict[str, Any]], env_metadata: Optional[str] = None) -> str:
    lines = [
        "# DMN Multi-Threaded Scalability Benchmark Summary",
        "",
        "Generated deterministically from retained JMH JSON runs.",
        "",
        "| Benchmark Method | Scenario | Threads | Score | Error | Unit | Scaling | Efficiency | B/op | GC (ms) |",
        "| :--- | :--- | ---: | ---: | ---: | :--- | ---: | ---: | ---: | ---: |",
    ]
    for r in results:
        lines.append(
            f"| `{r['method']}` | {r['scenario']} | {r['threads']} | {r['score']:.3f} | ±{r['score_error']:.3f} | {r['unit']} | {r['speedup_vs_1t']} | {r['parallel_efficiency']} | {r['alloc_bytes_per_op'] or '-'} | {r['gc_time_ms'] or '-'} |"
        )
    
    if env_metadata:
        lines.extend([
            "",
            "## Environment Metadata",
            "",
            "```properties",
            env_metadata.strip(),
            "```",
        ])
    lines.append("")
    return "\n".join(lines)


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(encoding="utf-8")

    parser = argparse.ArgumentParser(description="Summarize JMH scalability benchmark results.")
    parser.add_argument("--result-dir", type=Path, default=Path("dmn-benchmarks/results"), help="Directory with JMH JSON results")
    parser.add_argument("--output-md", type=Path, help="Output Markdown path")
    parser.add_argument("--output-csv", type=Path, help="Output CSV path")
    args = parser.parse_args()

    result_dir: Path = args.result_dir
    json_files = sorted(result_dir.glob("*.json"))
    if not json_files:
        print(f"No JSON files found in {result_dir}", file=sys.stderr)
        return 1

    records = load_benchmark_records(json_files)
    if not records:
        print("No benchmark records extracted.", file=sys.stderr)
        return 1

    summary = compute_scalability_metrics(records)

    md_path = args.output_md or (result_dir / "scalability-summary.md")
    csv_path = args.output_csv or (result_dir / "scalability-summary.csv")

    env_path = result_dir / "environment.properties"
    if not env_path.exists():
        env_path = result_dir / "bench-001-smoke-environment.properties"
    env_meta = env_path.read_text(encoding="utf-8") if env_path.exists() else None

    md_content = generate_markdown(summary, env_meta)
    md_path.write_text(md_content, encoding="utf-8")

    if summary:
        fieldnames = list(summary[0].keys())
        with csv_path.open("w", newline="", encoding="utf-8") as f:
            writer = csv.DictWriter(f, fieldnames=fieldnames, lineterminator="\n")
            writer.writeheader()
            writer.writerows(summary)

    print(f"Generated scalability summary to {md_path} and {csv_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
