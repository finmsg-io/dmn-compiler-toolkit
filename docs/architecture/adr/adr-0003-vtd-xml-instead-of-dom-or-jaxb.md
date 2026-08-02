
# ADR-0003 — VTD-XML Instead of DOM or JAXB

| Field | Value |
| --- | --- |
| Status | Accepted |
| Decision | ADR-0003 |

## Context

XML parsing should be fast, memory efficient, and avoid unnecessary object creation.

---

## Decision

Use VTD-XML as the XML frontend implementation.

---

## Alternatives

* DOM
* SAX
* StAX
* JAXB

---

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

