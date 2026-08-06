# Development slices

## Table of contents

- [Purpose](#purpose)
- [Slices](#slices)

## Purpose

These evidence-backed specifications define bounded vertical development increments. A slice
records its outcome, scope, constraints, acceptance scenarios, verification plan, and completion
evidence.

## Slices

| Slice | Status | Outcome |
| --- | --- | --- |
| [P1.3 — Load transitive imports deterministically](P1.3-load-transitive-imports.md) | done | Bounded recursive loading with identity caching and stable graph order |
| [P1.4 — Diagnose invalid import structures deterministically](P1.4-diagnose-invalid-import-structures.md) | done | Stable import-graph diagnostics and partial results |
| [P1.5 — Add shared diagnostic context](P1.5-add-shared-diagnostic-context.md) | done | Shared severity, phase, source, and model identity |
| [P1.6 — Introduce a whole-model-set semantic result](P1.6-whole-model-set-semantic-result.md) | done | Source-addressable linked semantic models and shared diagnostics |
| [P1.7 — Expose the immutable compiler facade](P1.7-immutable-compiler-facade.md) | done | One-call compilation through optimized Runtime IR |
| [P2.1–P2.9 — Real multi-file DMN corpus](P2.1-P2.9-real-multi-file-corpus.md) | done | 9 multi-file XML test scenarios and end-to-end business model test |
| [P3.1–P3.7 — Runtime semantic baseline & FEEL conformance](P3.1-P3.7-runtime-semantic-baseline.md) | done | 3-valued logic, null propagation, decision table hit policies, multi-word FEEL names |
| [P4.1–P4.4 — Stable compiled-model Protobuf-native API](P4.1-P4.4-stable-compiled-model-api.md) | done | Protobuf-backed evaluation request/response, name-based execution, and decision pruning |
| [P5.1 — High-performance pure Java code generator](P5.1-pure-java-generator.md) | done | Zero-reflection Java code generation with slot array indexing and 100% interpreter parity |
| [P6.1–P6.4 — 100% OMG DMN 1.5 TCK Compliance](P6.1-P6.4-100-omg-dmn-1.5-tck-compliance.md) | done | Complete FEEL 1.5 built-in catalog, decision table hit policies, TCK ingestion engine, and 100% dual-engine parity |
| [TCK.2–3a — Establish a scalar TCK smoke runner](TCK.2-3a-scalar-smoke-runner.md) | done | TCK-defined scalar cases compile and execute through the interpreter |
