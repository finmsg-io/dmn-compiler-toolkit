# `dmn-protobuf` TODO

<!-- generated-toc:start -->
## Table of contents

- [Current status](#contents-section-1)
<!-- generated-toc:end -->

Last reviewed: 2026-08-07

The protobuf schemas are the stable compiler-facing semantic contracts (`common.proto`, `core.proto`, `types.proto`, `feel_text.proto`, `feel_parsed.proto`, `feel.proto`, `decision_table.proto`, `drg.proto`, `model.proto`). Schemas cover the implemented DMN/FEEL subset, replaceable text/parsed FEEL nodes, imports, item-definition components, constraints, source locations, and extension structures.

<a id="contents-section-1"></a>
## Current status

| Priority | Work item | Status | Evidence |
| --- | --- | --- | --- |
| P0 | Core DMN & FEEL Protobuf schemas | `done` | `io.finmsg.dmn.model` generated Java classes |
| P0 | Replaceable text/parsed `oneof` node contracts | `done` | `feel.proto` (`Feel`, `ExpressionNode`, `BoxedExpression`) |
| P1 | Multi-file import & source identity contracts | `done` | `DmnSourceId`, `Definitions.imports` |
| P1 | Zero production dependencies on external frameworks | `done` | Pure protobuf contract model |
