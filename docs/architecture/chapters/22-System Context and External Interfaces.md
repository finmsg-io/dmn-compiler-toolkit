# Chapter 22 — System Context and External Interfaces [FUTURE — UNREVIEWED]

<!-- generated-toc:start -->
## Table of contents

- [Status and purpose](#contents-section-1)
- [Proposed system boundary](#contents-section-2)
- [Proposed external interfaces](#contents-section-3)
- [Open review questions](#contents-section-4)
<!-- generated-toc:end -->

> **Review status: UNREVIEWED PROPOSAL.** This chapter describes candidate boundaries and does not
> commit the project to an interface or integration.

<a id="contents-section-1"></a>
## Status and purpose

This chapter proposes a context view separating compiler-owned behavior from callers, repositories,
build tools, generated artifacts, and execution hosts.

<a id="contents-section-2"></a>
## Proposed system boundary

```text
DMN repository / source provider
            -> DMN Compiler Toolkit
                 -> diagnostics and compiled model
                 -> generated Java / Protobuf / gRPC artifacts
            -> application or service execution host
```

The toolkit should own parsing, semantic analysis, Runtime IR, optimization, reference execution,
and generation. It should not own repository authentication, application business state, service
deployment, or external function implementations.

<a id="contents-section-3"></a>
## Proposed external interfaces

| Interface | Proposed boundary |
| --- | --- |
| Source resolution | `DmnModelResolver` with stable source identity and caller policy |
| Filesystem/classpath | Confined resolver adapters, not implicit global lookup |
| Compiler API | Immutable options, diagnostics, model metadata, optimized Runtime IR |
| Runtime API | Name-based inputs and requested decisions; internal slots remain hidden |
| JAVA/PMML functions | Explicit host-binding SPI with allow-list and conversion policy |
| Generated Java | Deterministic sources with a documented helper-runtime dependency |
| gRPC | Transport adapter around generated Java, not a second execution engine |
| Build tools | Maven/CLI adapters invoking the same compiler facade |

<a id="contents-section-4"></a>
## Open review questions

- Which interfaces are public compatibility commitments in the first release?
- Is a CLI part of the product boundary or only a development adapter?
- Which external repository schemes should be supported by the core distribution?
- How are external functions discovered, authorized, versioned, and isolated?

