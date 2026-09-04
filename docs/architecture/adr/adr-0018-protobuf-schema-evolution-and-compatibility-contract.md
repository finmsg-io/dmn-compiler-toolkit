# ADR-0018 — Protobuf Schema Evolution and Compatibility Contract

<!-- generated-toc:start -->
## Table of contents

- [Context](#contents-section-1)
- [Decision](#contents-section-2)
- [Consequences](#contents-section-3)
<!-- generated-toc:end -->

| Field | Value |
| --- | --- |
| Status | Accepted |
| Decision | ADR-0018 |

<a id="contents-section-1"></a>
## Context

Protobuf schemas in `dmn-protobuf` define the canonical semantic interchange format between the XML frontend, semantic analysis, compiler passes, and code generators. When evolving schemas, backwards and forwards compatibility must be preserved across minor releases to avoid breaking downstream tools and plugins.

---

<a id="contents-section-2"></a>
## Decision

1. **Field Evolution Rules**:
   - New fields may be added as optional fields with new sequential field tag numbers.
   - Field tag numbers and field names must never be renumbered or changed in existing messages.
   - Fields must never be deleted; deprecated fields are marked `[deprecated = true]` and preserved.
   - Enum definitions may add new values at the end; existing enum ordinals must not change.
2. **Breaking Changes**: Any field removal, renumbering, or type mutation is considered a breaking change and requires a major version increment (`2.0.0`).
3. **Automated Verification**: Golden serialized protobuf test vectors in `dmn-tck-runner` guard against accidental schema drift.

---

<a id="contents-section-3"></a>
## Consequences

Advantages

* Safe evolution of compiler intermediate representations.
+ Stable tooling contracts for multi-module and plugin architectures.
* Clear semantic versioning guarantees for users and contributors.

Trade-offs

* Deprecated fields must be retained in proto definitions indefinitely within a major version line.
