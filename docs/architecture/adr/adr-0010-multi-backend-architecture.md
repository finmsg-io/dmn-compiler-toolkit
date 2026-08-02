
# ADR-0010 — Multi-Backend Architecture

<!-- generated-toc:start -->
## Table of contents

- [Context](#contents-section-1)
- [Decision](#contents-section-2)
- [Supported Backends](#contents-section-3)
<!-- generated-toc:end -->

| Field | Value |
| --- | --- |
| Status | Accepted |
| Decision | ADR-0010 |

<a id="contents-section-1"></a>
## Context

Future code generators should reuse the same compiler frontend.

---

<a id="contents-section-2"></a>
## Decision

Every backend consumes Runtime IR.

---

<a id="contents-section-3"></a>
## Supported Backends

* Java
* Rust
* Go
* Spark SQL
* LLVM
* WebAssembly

---

