
# ADR-0002 — Protobuf as Internal Semantic Representation

<!-- generated-toc:start -->
## Table of contents

- [Context](#contents-section-1)
- [Decision](#contents-section-2)
- [Alternatives](#contents-section-3)
- [Consequences](#contents-section-4)
<!-- generated-toc:end -->

| Field | Value |
| --- | --- |
| Status | Accepted |
| Decision | ADR-0002 |

<a id="contents-section-1"></a>
## Context

The Semantic Model must be:

* language neutral
* immutable
* serializable
* versionable

---

<a id="contents-section-2"></a>
## Decision

Use Protocol Buffers as the canonical schema for the Semantic Model.

Generated language bindings become immutable compiler data structures.

---

<a id="contents-section-3"></a>
## Alternatives

* Java records
* JAXB classes
* JSON
* Jackson
* Custom POJOs

---

<a id="contents-section-4"></a>
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

