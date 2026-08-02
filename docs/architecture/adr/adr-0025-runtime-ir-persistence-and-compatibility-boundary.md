
# ADR-0025 — Runtime IR Persistence and Compatibility Boundary

| Field | Value |
| --- | --- |
| Status | Accepted |
| Decision | ADR-0025 |

## Context

Runtime IR is implemented as immutable Java records and an optimized companion model. The
architecture anticipates caching and distributed deployment, but no persistent cache, artifact
loader, or compiler/runtime transport currently exists. Treating Java serialization or the
semantic protobuf schema as a persistence format would freeze implementation details before a
consumer defines the required lifetime and interoperability constraints.

---

## Decision

Runtime IR is process-local until a concrete durable cache or deployment/transport consumer is
implemented. Java record shapes, enum ordinals, and expression traversal ordinals are not wire
compatibility contracts.

When persistence is required, define a separate versioned Runtime IR schema. It must use explicit
numeric identifiers, capability negotiation, deterministic serialization, integrity metadata,
bounded decoding, and major/minor compatibility rules. It must not reuse the semantic protobuf
model or Java native serialization.

---

## Consequences

Advantages

* Runtime IR can evolve with the evaluator and generators without premature wire compatibility.
* A future schema is designed against a real cache or deployment lifetime.
* Semantic and executable representations remain independently versioned.

Trade-offs

* Runtime IR cannot currently be persisted or exchanged between processes.
* A future persistence consumer must fund schema, migration, conformance, and security work.

---

