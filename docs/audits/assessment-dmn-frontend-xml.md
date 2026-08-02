
# `dmn-frontend-xml` assessment

<!-- generated-toc:start -->
## Table of contents

- [Executive conclusion](#contents-section-1)
- [Verified strengths](#contents-section-2)
- [Findings and recommended actions](#contents-section-3)
  - [P1 — Define and correct caller-owned OutputStream behavior](#contents-section-4)
  - [P2 — Make bounded path reads genuinely streaming](#contents-section-5)
  - [P2 — Reduce accidental public API surface](#contents-section-6)
  - [P2 — Formalize the XML security boundary](#contents-section-7)
  - [P3 — Remove dead and misleading exception types](#contents-section-8)
  - [P3 — Clean test and build hygiene](#contents-section-9)
- [Deferred model extensions](#contents-section-10)
- [Recommended sequence](#contents-section-11)
<!-- generated-toc:end -->

Assessment date: 2026-08-02

<a id="contents-section-1"></a>
## Executive conclusion

`dmn-frontend-xml` is complete and usable as the XML boundary for the current
`dmn-protobuf` semantic subset. It is not a complete implementation of every DMN XML construct,
and the documentation correctly places DMNDI, artifacts, business-context metadata, recursive
item components, and arbitrary-depth extensions behind protobuf/model-design decisions.

The module is suitable for continued compiler and runtime work. No remaining issue requires
blocking Runtime IR or runtime development. Before describing the frontend as a hardened public
library, the stream-ownership contract and a few API/security-maintenance items should be closed.

Indicative status:

| Dimension | Assessment |
| --- | --- |
| Current protobuf-subset coverage | 100% |
| Reader/writer symmetry for that subset | complete |
| Namespace and QName correctness | complete |
| Diagnostics and bounded-input baseline | strong |
| Test coverage of declared completion gate | complete |
| Full DMN specification coverage | intentionally incomplete |
| Public-library/API maturity | good, with cleanup remaining |

<a id="contents-section-2"></a>
## Verified strengths

- Reader and writer cover definitions metadata, imports, item definitions, five modeled DRG
  variants, requirements, services, decision tables, invocations, and modeled boxed expressions.
- QName `typeRef` values are resolved in element scope and preserve their namespace on writing.
- DMN vocabulary dispatch is namespace-aware; foreign extensions do not collide with DMN names.
- DMN 1.2 through 1.6 namespaces, prefixed roots, and namespace shadowing are tested.
- `DmnReadResult` provides structured diagnostics while legacy convenience APIs remain available.
- Input-size and element-depth limits, malformed input, DTD/entity rejection, UTF-8/UTF-16 cases,
  and semantic read-write-read conformance are tested.
- Optional system ID, line, column, and byte-offset metadata is available.
- The frontend emits FEEL source text and does not leak VTD/XML types into compiler stages.
- The current suite contains 36 passing tests: 13 writer, 12 reader, 10 conformance/security,
  and one version-detector test. The complete seven-module reactor also passes.

<a id="contents-section-3"></a>
## Findings and recommended actions

<a id="contents-section-4"></a>
### P1 — Define and correct caller-owned `OutputStream` behavior

`DmnWriter.write(OutputStream, Definitions)` constructs `XmlEmitter` in try-with-resources.
Closing `XmlEmitter` closes its `XMLStreamWriter`, which can close or otherwise finalize the
caller-supplied stream. Java APIs normally leave caller-owned streams open, while
`write(Path, ...)` owns and closes the stream it creates.

Recommendation: explicitly adopt one ownership rule, document it, and test it. Prefer flushing
but not closing caller-provided streams; retain closing behavior only in the `Path` overload.

<a id="contents-section-5"></a>
### P2 — Make bounded path reads genuinely streaming

`readResult(Path, ...)` checks `Files.size` and then calls `Files.readAllBytes`. A file that grows
between those operations can allocate beyond the configured limit before the byte-array overload
rejects it. The `InputStream` overload already implements bounded incremental reading.

Recommendation: open the path as an input stream and delegate to the bounded stream overload.
The initial size check can remain as a fast rejection, but should not be the enforcement boundary.

<a id="contents-section-6"></a>
### P2 — Reduce accidental public API surface

Most element-specific readers and writers are public, despite the intended stable boundary being
`DmnXmlReader`, `DmnWriter`, `DmnReadOptions`, and `DmnReadResult`. This makes future internal
refactoring appear source-incompatible to consumers.

Recommendation: make implementation classes package-private where practical and document the
supported public packages/API. Treat this as compatibility cleanup, not a mapping blocker.

<a id="contents-section-7"></a>
### P2 — Formalize the XML security boundary

DTD/entity rejection currently includes a pre-parse byte signature check and hostile-input tests.
This is a useful baseline, but the security guarantee depends partly on scanning serialized bytes
rather than a parser feature that disables DTD/entity processing.

Recommendation: document the exact VTD-XML parser behavior and supported input encodings, add
tests for all accepted encodings, and keep the scanner as defense in depth. If VTD cannot provide
a parser-level prohibition, state that constraint explicitly in the public read contract.

<a id="contents-section-8"></a>
### P3 — Remove dead and misleading exception types

`NamespaceException` and `NavigationException` are empty ordinary classes and do not extend
`Exception` or `RuntimeException`. They appear unfinished or dead and weaken confidence in the
exception taxonomy.

Recommendation: remove them if unused, or implement and integrate them intentionally.

<a id="contents-section-9"></a>
### P3 — Clean test and build hygiene

- `DmnVersionDetectorTest` prints an entire model to standard output, making reactor logs noisy.
- The VTD dependency repeats `2.13.4` instead of using the parent-managed version.
- The active TODO says 36 tests, which is currently correct, but raw `@Test` counting undercounts
  parameterized cases; report counts from Surefire when updating status documents.

<a id="contents-section-10"></a>
## Deferred model extensions

These are not frontend defects until the semantic protobuf model is extended:

- DMNDI diagrams;
- artifacts and associations;
- organization units and performance indicators;
- decision questions and allowed answers;
- recursive item-definition components;
- arbitrary-depth extension XML and mixed-content fidelity;
- formatting, comments, attribute order, and lexical-preserving round trips.

<a id="contents-section-11"></a>
## Recommended sequence

1. Fix and test output-stream ownership.
2. Route path reading through the bounded streaming implementation.
3. Document the security/encoding contract and add any missing encoding fixtures.
4. Remove dead exception shells and test logging.
5. Reduce internal class visibility as a deliberate API-compatibility pass.
6. Keep broader DMN constructs deferred until protobuf ownership and semantics are decided.

After items 1–4, the module can reasonably be declared production-ready for its documented
subset. Full DMN XML completeness remains a separate product-scope decision.

