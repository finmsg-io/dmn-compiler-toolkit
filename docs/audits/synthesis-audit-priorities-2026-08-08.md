# Audit Synthesis and Priority Roadmap (2026-08-08)

> **Synthesis & Direction:** This document records the synthesized priority order derived from the independent ChatGPT and Gemini 3.6 Flash audit assessments conducted on 2026-08-08. It defines the immediate action queue for hardening the DMN Compiler Toolkit for production release.

Assessment Date: 2026-08-08  
Status: Active Priority Queue  

---

## Synthesis: Priority Execution Queue

Based on the independent assessments by ChatGPT (Codex) and Gemini 3.6 Flash, the overlapping findings are prioritized in the following execution order:

### 1. Fix the TCK silent-skip gate (Immediate Severity Fix)
- **Rationale:** A missing `tck-official` submodule should fail the build rather than silently passing with a skipped test. Fixing this ensures headline TCK conformance claims are strictly enforced in CI.
- **Scope:** Update [`OfficialTckSuiteTest.java`](file:///c:/00-finmsg.io/dmn-compiler-toolkit/dmn-tck-runner/src/test/java/io/finmsg/dmn/tck/OfficialTckSuiteTest.java) to fail hard when required test resources are absent.

### 2. Lock down CI publishing (Production Risk Fix)
- **Rationale:** Publishing releases directly on every `main` branch push poses a high production release risk. Releases must be tag-gated.
- **Scope:** Update [`.github/workflows/ci.yml`](file:///c:/00-finmsg.io/dmn-compiler-toolkit/.github/workflows/ci.yml) so release deployment triggers only on `v*` version tags.

### 3. Reconcile documentation from a single source of truth
- **Rationale:** [`README.md`](file:///c:/00-finmsg.io/dmn-compiler-toolkit/README.md) and [`docs/development-plan.md`](file:///c:/00-finmsg.io/dmn-compiler-toolkit/docs/development-plan.md) contain severe status drift (e.g. reporting 12 modules instead of 14, listing completed features under "Current focus").
- **Scope:** Align module counts, status tables, and roadmap across all documentation files.

### 4. Add an open-source License
- **Rationale:** The repository currently has no `LICENSE` file, blocking legitimate external enterprise adoption.
- **Scope:** Add an Apache License 2.0 `LICENSE` file and update documentation references.

### 5. Document the Backend Parity Matrix
- **Rationale:** The primary architectural gap is formalizing edge-case behavior (null handling, date/time, numeric scaling) across `DmnRuntime`, `dmn-generator-java`, and `dmn-generator-sparksql`.
- **Scope:** Create `docs/architecture/backend-parity-matrix.md` mapping supported features and edge-case behaviors across backends.
