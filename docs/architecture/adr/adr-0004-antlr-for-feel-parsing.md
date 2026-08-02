
# ADR-0004 — ANTLR for FEEL Parsing

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
| Decision | ADR-0004 |

<a id="contents-section-1"></a>
## Context

FEEL is a complete expression language with a formal grammar.

---

<a id="contents-section-2"></a>
## Decision

ANTLR4 is used to generate the FEEL parser.

---

<a id="contents-section-3"></a>
## Alternatives

* handwritten recursive descent parser
* parser combinators
* JavaCC

---

<a id="contents-section-4"></a>
## Consequences

Advantages

* mature tooling
* grammar readability
* excellent diagnostics
* grammar evolution

Disadvantages

* generated source code
* ANTLR runtime dependency

---

