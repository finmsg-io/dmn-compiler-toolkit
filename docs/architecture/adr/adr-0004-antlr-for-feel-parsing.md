
# ADR-0004 — ANTLR for FEEL Parsing

| Field | Value |
| --- | --- |
| Status | Accepted |
| Decision | ADR-0004 |

## Context

FEEL is a complete expression language with a formal grammar.

---

## Decision

ANTLR4 is used to generate the FEEL parser.

---

## Alternatives

* handwritten recursive descent parser
* parser combinators
* JavaCC

---

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

