# Development Slices: Spark SQL Native Coverage Expansion

Latest interval-native full checkpoint: 3,391 cases; 3,357 passed; 1,363 native (50.20% executable); 1,318 fallback; 18 failed; 16 errors; 676 provisional expected rejections. All 14 interval cases pass natively; +12 native/-12 fallback versus the preceding run. Evidence: `dmn-tck-runner/target/interval-native-full-accounting.json`. Slice remains in progress. Prior metrics below are historical.

Latest full native implementation checkpoint: 3,391 cases; 3,357 passed; 1,351 native (49.76% executable); 1,330 fallback; 18 failed; 16 errors; 676 provisional expected rejections. Evidence: `dmn-tck-runner/target/interval-boolean-full-accounting.json`. Slice remains in progress; native fallback conversion is the priority. Previous metrics below are historical.

Latest verified full checkpoint: 3,391 cases; 3,326 passed; 1,332 native; 1,318 fallback; 34 failed; 31 errors; 676 provisional expected rejections. Native coverage: 49.06% of 2,715 executable cases. S-SPARK-01 remains in progress. See the canonical status/handover and `dmn-tck-runner/target/string-join-full-accounting.json` for evidence; prior metrics below are historical.

## Table of contents

- [Purpose](#purpose)
- [Target Objectives](#target-objectives)
- [Slice Roadmap to $\ge$ 95% Native Passes](#slice-roadmap-to-ge-95-native-passes)
- [Active Slices](#active-slices)

## Purpose

These evidence-backed specifications define bounded vertical development increments to expand native Spark SQL CTE coverage in `dmn-generator-sparksql`, migrating cases away from hybrid UDF fallback while preserving 100% semantic conformance.

## Target Objectives

- **Target Native Passes:** $\ge$ 95.0% of executable TCK cases ($\ge$ 2,580 / 2,715 for the 2026-10-04 baseline).
- **Target Fallback Passes:** $\le$ 5.0% of executable TCK cases ($\le$ 135 / 2,715). The 676 expected model rejection passes are reported separately from the 3,391 total cases.
- **Zero Regression:** 0 compilation errors, 0 runtime errors.

## Slice Roadmap to $\ge$ 95% Native Passes

| Slice | Focus Area | Status | Target Contribution |
| :--- | :--- | :--- | :--- |
| [S-SPARK-01](S-SPARK-01-numeric-native-promotion.md) | Numbers & Decimals Promotion | `in progress` | +1,200 to +1,400 native passes ($\rightarrow \sim 60\text{--}65\%$) |
| S-SPARK-02 | Decision Tables & Hit Policies | `proposed` | +500 to +600 native passes ($\rightarrow \sim 75\text{--}80\%$) |
| S-SPARK-03 | Temporal Types (Date, Time, DateTime) | `proposed` | +350 to +450 native passes ($\rightarrow \sim 88\text{--}92\%$) |
| S-SPARK-04 | Lists, Contexts & Built-in Functions | `proposed` | +150 to +250 native passes ($\rightarrow \mathbf{\ge 95.0\%}$) |

## Active Slices

| Slice | Status | Outcome |
| :--- | :--- | :--- |
| [S-SPARK-01 — Promote Number / Decimal Types to Native Spark SQL Execution](S-SPARK-01-numeric-native-promotion.md) | `in progress` | `0035` passes focused checks. Full baseline completed without OOM: 1,310 native / 1,191 fallback / 112 failed / 102 errors / 676 expected rejections. Native coverage is 48.25% of 2,715 executable cases; conformance restoration is the next gate. |
