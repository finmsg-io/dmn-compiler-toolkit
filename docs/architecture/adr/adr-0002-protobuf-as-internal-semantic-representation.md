
# ADR-0002 — Protobuf as Internal Semantic Representation

| Field | Value |
| --- | --- |
| Status | Accepted |
| Decision | ADR-0002 |

## Context

The Semantic Model must be:

* language neutral
* immutable
* serializable
* versionable

---

## Decision

Use Protocol Buffers as the canonical schema for the Semantic Model.

Generated language bindings become immutable compiler data structures.

---

## Alternatives

* Java records
* JAXB classes
* JSON
* Jackson
* Custom POJOs

---

## Consequences

Advantages

* language independence
* compact serialization
* schema evolution
* code generation support

Disadvantages

* build-time code generation
* protobuf dependency

---

