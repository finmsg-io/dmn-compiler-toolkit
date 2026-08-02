<a id="contents-section-1"></a>
# Audits

<!-- generated-toc:start -->
## Table of contents

- [Audits](#contents-section-1)
<!-- generated-toc:end -->

Audits are dated snapshots of implementation evidence, risks, and recommendations.
They are retained for historical context and are not active work queues. Current
actions belong in the [module TODOs](../todos/index.md) or the
[living development plan](../development-plan.md).

| Audit | Scope |
| --- | --- |
| [Protobuf assessment](assessment-dmn-protobuf.md) | Semantic protobuf contracts |
| [XML frontend assessment](assessment-dmn-frontend-xml.md) | Production-readiness findings |
| [XML frontend completeness](dmn-frontend-xml-completeness.md) | Supported XML subset and completion evidence |
| [Semantic-analysis assessment](assessment-dmn-semantic-analysis.md) | Semantic compiler baseline and risks |
| [Runtime IR assessment](assessment-dmn-runtime-ir.md) | Lowering contract and optimization boundary |
| [Runtime assessment](assessment-dmn-runtime.md) | Interpreter correctness and readiness |

Audit statements may become stale as implementation advances. Each audit retains
its assessment date; consult current TODOs and executable tests before acting on it.
