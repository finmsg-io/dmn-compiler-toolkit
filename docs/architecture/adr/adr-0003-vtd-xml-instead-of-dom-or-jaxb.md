
# ADR-0003 — VTD-XML Instead of DOM or JAXB

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
| Decision | ADR-0003 |

<a id="contents-section-1"></a>
## Context

XML parsing should be fast, memory efficient, and avoid unnecessary object creation.

---

<a id="contents-section-2"></a>
## Decision

Use VTD-XML as the XML frontend implementation.

---

<a id="contents-section-3"></a>
## Alternatives

* DOM
* SAX
* StAX
* JAXB

---

<a id="contents-section-4"></a>
## Consequences

Advantages

* zero-copy navigation
* low memory usage
* fast XPath support
* no object graph creation

Disadvantages

* smaller community
* lower familiarity

---

