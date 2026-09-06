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


def parse_method_components(method: str) -> tuple[str, str, str]:
    """Decompose benchmark method name into (engine, invocation_path, model_name)."""
    if "_" in method:
        prefix, model = method.split("_", 1)
    else:
        prefix, model = method, ""

    if prefix == "generatedDirect":
        engine = "Generated Java"
        path = "Direct"
    elif prefix == "generatedAdapter":
        engine = "Generated Java"
        path = "Adapter"
    elif prefix == "generatedEndToEnd":
        engine = "Generated Java"
        path = "End-to-End"
    elif prefix == "interpreterCore":
        engine = "Interpreter"
        path = "Core"
    elif prefix == "interpreterEndToEnd":
        engine = "Interpreter"
        path = "End-to-End"
    elif prefix == "invocationControl":
        engine = "Harness"
        path = "Control"
    else:
        engine = "Unknown"
        path = prefix

    return engine, path, model or method


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
            engine, inv_path, model = parse_method_components(method)
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
                "model": model,
                "engine": engine,
                "path": inv_path,
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

    return sorted(
        results,
        key=lambda x: (
            x.get("model", ""),
            x.get("scenario", ""),
            x.get("mode", ""),
            x.get("engine", ""),
            x.get("path", ""),
            x.get("threads", 1),
        ),
    )


def generate_markdown(results: List[Dict[str, Any]], env_metadata: Optional[str] = None) -> str:
    thrpt_results = [r for r in results if r.get("mode") == "thrpt"]
    avgt_results = [r for r in results if r.get("mode") == "avgt"]

    lines = [
        "# DMN Multi-Threaded Scalability Benchmark Summary",
        "",
        "Generated deterministically from retained JMH JSON runs.",
        "",
        "> [!IMPORTANT]",
        "> **Key Metric Visual Indicators**:",
        "> - 🚀 **Throughput (`thrpt`)**: Measures operations processed per time unit (`ops/µs`, `ops/ns`, `ops/s`). **Higher is Better (⬆️)**.",
        "> - ⏱️ **Average Latency (`avgt`)**: Measures execution duration per operation (`µs/op`, `ns/op`). **Lower is Better (⬇️)**.",
        "",
        "---",
        "",
        "## 1. 🚀 Throughput Benchmarks (`thrpt` — Higher is Better ⬆️)",
        "",
        "Measures how many decision evaluations the engine can process per unit of time under concurrent load.",
        "",
        "| Model | Engine | Invocation Path | Scenario | Threads | Throughput Score [⬆️] | Error (±) | Unit | Speedup (vs 1T) | Parallel Efficiency | Memory (B/op) | GC Time (ms) |",
        "| :--- | :--- | :--- | :--- | ---: | ---: | ---: | :--- | ---: | ---: | ---: | ---: |",
    ]

    for r in thrpt_results:
        engine_str = f"**`{r['engine']}`**" if r.get("engine") == "Generated Java" else f"`{r.get('engine', '-')}`"
        lines.append(
            f"| `{r.get('model', '-')}` | {engine_str} | `{r.get('path', '-')}` | {r['scenario']} | {r['threads']} | **{r['score']:.3f}** | ±{r['score_error']:.3f} | `{r['unit']}` | {r['speedup_vs_1t']} | {r['parallel_efficiency']} | {r['alloc_bytes_per_op'] or '-'} | {r['gc_time_ms'] or '-'} |"
        )

    lines.extend([
        "",
        "---",
        "",
        "## 2. ⏱️ Average Latency Benchmarks (`avgt` — Lower is Better ⬇️)",
        "",
        "Measures elapsed execution duration per single decision call. (Under multi-threaded load, latency per thread increases due to CPU contention while total aggregate throughput scales).",
        "",
        "| Model | Engine | Invocation Path | Scenario | Threads | Avg Latency Score [⬇️] | Error (±) | Unit | Latency Factor (vs 1T) | Memory (B/op) | GC Time (ms) |",
        "| :--- | :--- | :--- | :--- | ---: | ---: | ---: | :--- | ---: | ---: | ---: |",
    ])

    for r in avgt_results:
        engine_str = f"**`{r['engine']}`**" if r.get("engine") == "Generated Java" else f"`{r.get('engine', '-')}`"
        lines.append(
            f"| `{r.get('model', '-')}` | {engine_str} | `{r.get('path', '-')}` | {r['scenario']} | {r['threads']} | **{r['score']:.3f}** | ±{r['score_error']:.3f} | `{r['unit']}` | {r['speedup_vs_1t']} | {r['alloc_bytes_per_op'] or '-'} | {r['gc_time_ms'] or '-'} |"
        )

    lines.extend([
        "",
        "---",
        "",
        "## Metric & Column Legend",
        "",
        "| Column | Description |",
        "| :--- | :--- |",
        "| **Model** | Evaluated DMN decision model (`CreditApproval`, `TrafficViolation`, `ScalarArithmetic`, `Originations`, `RankedLoanProducts`). |",
        "| **Engine** | Target engine backend: **`Generated Java`** (`dmn-generator-java` AOT compiled Java bytecode) vs **`Interpreter`** (`DmnRuntime` deterministic IR interpreter) vs **`Harness`** (Control baseline). |",
        "| **Invocation Path** | **`Direct`**: Strongly-typed direct interface call (`GeneratedDecisionEngine`) with pre-allocated slot inputs.<br>**`Core`**: Direct interpreter execution on pre-allocated slot inputs.<br>**`Adapter`**: Invocation through dynamic reflective wrapper (`Method.invoke`).<br>**`End-to-End`**: Named map input translation + decision evaluation + output unmarshalling.<br>**`Control`**: Test harness iteration and payload selection baseline. |",
        "| **Scenario** | Workload scenario variant (e.g. `default`, `validBaseline`, `singleViolation`, etc.). |",
        "| **Threads** | Number of concurrent worker threads running the benchmark simultaneously. |",
        "| **Throughput Score [⬆️]** | Rate of evaluations processed per time unit (`ops/µs` = million ops/sec, `ops/ns` = billion ops/sec). **Higher is Better.** |",
        "| **Avg Latency Score [⬇️]** | Elapsed duration per evaluation (`µs/op`, `ns/op`). **Lower is Better.** |",
        "| **Speedup (vs 1T)** | Ratio of multi-threaded throughput to single-threaded baseline ($T_N / T_1$). |",
        "| **Parallel Efficiency** | Multi-threaded scaling efficiency ($(\\text{Speedup} / N) \\times 100\\%$). Ideal linear scaling is $100\\%$. |",
        "| **Latency Factor (vs 1T)** | Ratio of multi-threaded latency to single-threaded latency ($L_N / L_1$). Reflects queuing/CPU contention under load. |",
        "| **Memory (B/op)** | Normalized heap memory allocation in **Bytes per Operation** measured by JMH GC profiler. Lower is better; near-zero indicates zero-GC hot execution path. |",
        "| **GC Time (ms)** | Total JVM garbage collection pause time accumulated across measurement iterations. |",
    ])
    
    if env_metadata:
        props: Dict[str, str] = {}
        for line in env_metadata.splitlines():
            line = line.strip()
            if line and "=" in line and not line.startswith("#"):
                k, v = line.split("=", 1)
                props[k.strip()] = v.strip()

        table_rows = []
        if "cpu_processor" in props:
            table_rows.append(f"| **Processor / CPU** | `{props['cpu_processor']}` |")
        if "physical_cores" in props:
            table_rows.append(f"| **Physical Cores** | `{props['physical_cores']} cores` |")
        if "logical_processors" in props:
            table_rows.append(f"| **Logical / Virtual Cores** | `{props['logical_processors']} threads` |")
        if "memory_total_gb" in props:
            table_rows.append(f"| **System Memory (RAM)** | `{props['memory_total_gb']}` |")
        if "computer_model" in props:
            table_rows.append(f"| **Computer Model** | `{props['computer_model']}` |")
        if "os" in props:
            table_rows.append(f"| **Operating System** | `{props['os']}` |")
        if "java" in props:
            table_rows.append(f"| **Java Runtime** | `{props['java']}` |")
        if "git_commit" in props:
            table_rows.append(f"| **Git Commit** | `{props['git_commit']}` ({props.get('git_status', 'clean')}) |")
        if "forks" in props or "threads" in props:
            protocol_desc = f"{props.get('forks', '3')} forks, {props.get('warmup', '5x5s')} warmup, {props.get('measurement', '5x10s')} measurement, threads [{props.get('threads', '1,2,4,8')}], profiler: {props.get('profiler', 'gc')}"
            table_rows.append(f"| **Benchmark Protocol** | `{protocol_desc}` |")

        lines.extend([
            "",
            "## Environment & Hardware Metadata",
            "",
        ])
        if table_rows:
            lines.extend([
                "| Parameter | Value |",
                "| :--- | :--- |",
                *table_rows,
                "",
            ])
        lines.extend([
            "<details><summary>Raw Environment Properties</summary>",
            "",
            "```properties",
            env_metadata.strip(),
            "```",
            "",
            "</details>",
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
    # Prioritize dedicated scalability run files (jmh-[0-9]*-thread.json)
    json_files = sorted(result_dir.glob("jmh-[0-9]*-thread.json"))
    if not json_files:
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
