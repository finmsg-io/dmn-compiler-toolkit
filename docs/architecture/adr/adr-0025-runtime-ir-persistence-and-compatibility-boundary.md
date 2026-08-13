
# ADR-0025 — Runtime IR Persistence and Compatibility Boundary

<!-- generated-toc:start -->
## Table of contents

- [Context](#contents-section-1)
- [Decision](#contents-section-2)
- [Consequences](#contents-section-3)
<!-- generated-toc:end -->

| Field | Value |
| --- | --- |
| Status | Accepted |
| Decision | ADR-0025 |

<a id="contents-section-1"></a>
## Context

Runtime IR is implemented as immutable Java records and an optimized companion model. The
architecture anticipates caching and distributed deployment, but no persistent cache, artifact
loader, or compiler/runtime transport currently exists. Treating Java serialization or the
semantic protobuf schema as a persistence format would freeze implementation details before a
consumer defines the required lifetime and interoperability constraints.

---

<a id="contents-section-2"></a>
## Decision

Runtime IR is process-local until a concrete durable cache or deployment/transport consumer is
implemented. Java record shapes, enum ordinals, and expression traversal ordinals are not wire
compatibility contracts.

When persistence is required, define a separate versioned Runtime IR schema. It must use explicit
numeric identifiers, capability negotiation, deterministic serialization, integrity metadata,
bounded decoding, and major/minor compatibility rules. It must not reuse the semantic protobuf
model or Java native serialization.

---

<a id="contents-section-3"></a>
## Consequences

Advantages

* Runtime IR can evolve with the evaluator and generators without premature wire compatibility.
* A future schema is designed against a real cache or deployment lifetime.
* Semantic and executable representations remain independently versioned.

Trade-offs

* Runtime IR cannot currently be persisted or exchanged between processes.
* A future persistence consumer must fund schema, migration, conformance, and security work.

---

