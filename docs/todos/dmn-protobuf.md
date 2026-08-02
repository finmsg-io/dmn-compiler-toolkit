# `dmn-protobuf` TODO

Last reviewed: 2026-08-02

The protobuf schemas are the stable compiler-facing semantic contracts. Current
schemas cover the implemented DMN/FEEL subset, replaceable text/parsed FEEL nodes,
imports, item-definition components, constraints, source locations, and extensions.

## Current work

| Priority | Work item | Completion evidence |
| --- | --- | --- |
| P1 | Enforce protobuf compatibility in CI using descriptor or breaking-change checks | An incompatible schema change fails CI and an intentional migration is documented |
| P1 | Complete the import/source identity needed by the compiler facade without embedding filesystem policy | Multi-file facade tests resolve sources while protobuf remains transport-neutral |
| P2 | Define a shared diagnostic envelope or conversion contract across compiler phases | XML, FEEL, semantic, and facade diagnostics expose stable severity and source identity |
| P2 | Improve source spans beyond element-level locations where parser and frontend data permit | Diagnostics can identify relevant FEEL ranges without breaking existing messages |
| P2 | Add schema-evolution round-trip fixtures for every supported replaceable node | Binary and JSON round trips preserve text/parsed alternatives and unknown fields as intended |
| P3 | Document ownership and immutability expectations for protobuf messages returned by passes | Public compiler documentation states copying and field-preservation guarantees |

Recursive item-definition components and constraints are implemented and are no
longer TODOs. Resolved symbol/type bindings remain side tables unless a separate ADR
establishes a durable serialization use case.

Historical context: [protobuf assessment](../audits/assessment-dmn-protobuf.md).
