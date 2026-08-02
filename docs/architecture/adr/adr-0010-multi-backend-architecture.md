
# ADR-0010 — Multi-Backend Architecture

| Field | Value |
| --- | --- |
| Status | Accepted |
| Decision | ADR-0010 |

## Context

Future code generators should reuse the same compiler frontend.

---

## Decision

Every backend consumes Runtime IR.

---

## Supported Backends

* Java
* Rust
* Go
* Spark SQL
* LLVM
* WebAssembly

---

