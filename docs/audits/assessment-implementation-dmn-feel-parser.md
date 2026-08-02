# `dmn-feel-parser` assessment

<!-- generated-toc:start -->
## Table of contents

- [Executive conclusion](#contents-section-1)
- [Verified strengths](#contents-section-2)
- [Findings and recommended actions](#contents-section-3)
  - [P1 — Align diagnostics with the compiler contract](#contents-section-4)
  - [P1 — Add real linked-model parsing fixtures](#contents-section-5)
  - [P2 — Expand standards-oriented conformance evidence](#contents-section-6)
  - [P2 — Make generated-parser drift detectable](#contents-section-7)
  - [P2 — Add explicit resource limits](#contents-section-8)
  - [P3 — Reduce coordinated traversal risk](#contents-section-9)
- [Recommended sequence](#contents-section-10)
<!-- generated-toc:end -->

Assessment date: 2026-08-02

<a id="contents-section-1"></a>
## Executive conclusion

`dmn-feel-parser` is a strong parser baseline for the currently modeled FEEL subset. It has
separate grammar, parse-tree, protobuf-AST, and whole-model transformation layers; consumes the
entire input at every public grammar entry point; preserves the input protobuf model; and reports
multiple syntax errors with owning DMN paths and source locations.

It is suitable for continued compiler-facade integration. It should not yet be presented as a
complete DMN 1.5 FEEL implementation: standards coverage is fixture-led, parser limits are not
explicit, diagnostics are not yet compatible with the planned shared compiler contract, and the
checked-in generated ANTLR sources can drift from their grammars.

| Dimension | Assessment |
| --- | --- |
| Grammar and AST baseline | strong for the modeled subset |
| Whole-model parsing | implemented and immutable |
| Diagnostics | multi-error and model-aware; shared identity missing |
| Build reproducibility | functional; generated-source drift is possible |
| Test maturity | good: 47 passing tests |
| Full FEEL conformance | incomplete |

<a id="contents-section-2"></a>
## Verified strengths

- Production dependencies are limited to `dmn-protobuf` and the ANTLR runtime; the XML frontend
  is test-scoped.
- Four public roots parse expressions, unary tests, textual expressions, and types through rules
  that require `EOF`, preventing silent acceptance of trailing input.
- `FeelAstBuilder` removes ANTLR types from the protobuf AST consumed by later stages.
- `DmnFeelParser` returns a copied model, replaces valid text nodes, retains invalid text nodes,
  and collects diagnostics instead of failing at the first malformed expression.
- Traversal covers decisions, tables, BKMs, invocations, item constraints, contexts, relations,
  lists, and boxed functions in the currently modeled protobuf surface.
- The 47 passing tests cover grammar shape, AST precedence, positive and negative syntax,
  multi-error model diagnostics, immutability, boxed expressions, and `TrafficViolation.dmn`.

<a id="contents-section-3"></a>
## Findings and recommended actions

<a id="contents-section-4"></a>
### P1 — Align diagnostics with the compiler contract

`FeelDiagnostic` has line, character, and message, while `DmnFeelDiagnostic` adds path, source text,
and protobuf `SourceLocation`. Neither carries severity, stable code, phase, or source-model
identity. The compiler facade would otherwise need lossy message parsing and ad hoc identities.

Recommendation: retain parser-local diagnostics but add stable syntax codes and adapt them into the
shared compiler diagnostic shape with phase and `DmnSourceId`.

<a id="contents-section-5"></a>
### P1 — Add real linked-model parsing fixtures

The only real DMN fixture is `TrafficViolation.dmn`; model-rich tests otherwise construct protobuf
objects. Add valid and invalid imported `.dmn` repositories and assert that source identity and
location survive XML and FEEL parsing for every model.

<a id="contents-section-6"></a>
### P2 — Expand standards-oriented conformance evidence

The suite covers important syntax families but is not a versioned FEEL conformance matrix. Add
positive, negative, lexical-edge, whitespace/comment, name, temporal literal, type, and recovery
fixtures. Publish the supported syntax boundary beside the executable matrix.

<a id="contents-section-7"></a>
### P2 — Make generated-parser drift detectable

ANTLR Java sources are checked into `src/gen/java`, while regeneration is optional through the
`generate-code` profile. A normal build can therefore pass with stale generated code.

Recommendation: add CI that regenerates and fails on a non-empty diff, or generate deterministically
in every build and stop committing generated Java. Keep exactly one authoritative policy.

<a id="contents-section-8"></a>
### P2 — Add explicit resource limits

The XML frontend has input limits, but FEEL parsing has no documented length, token, nesting, or
diagnostic limits. Deep or adversarial expressions can consume disproportionate CPU or memory.
Introduce limits at the compiler boundary and focused rejection tests.

<a id="contents-section-9"></a>
### P3 — Reduce coordinated traversal risk

`DmnFeelParser` and `FeelAstBuilder` are about 562 and 611 lines. Their size is manageable, but a
protobuf or grammar variant requires coordinated edits across grammar, builder, model traversal,
semantic analysis, and Runtime IR. Add schema-evolution/exhaustiveness tests before decomposing
them into focused visitors.

<a id="contents-section-10"></a>
## Recommended sequence

1. Integrate source-aware coded diagnostics with the compiler facade.
2. Add real multi-file parsing fixtures.
3. Establish generated-source drift checking.
4. Expand the versioned FEEL syntax matrix and document its boundary.
5. Add parser limits, then refactor traversal only where tests protect behavior.

