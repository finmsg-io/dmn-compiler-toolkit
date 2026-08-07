# Chapter 15 — Whole-Model Semantic Analysis [IMPLEMENTATION-ALIGNED]

<!-- generated-toc:start -->
## Table of contents

- [15.1 Purpose](#contents-section-1)
- [15.2 Scope & Symbol Resolution](#contents-section-2)
- [15.3 Type Inference & Validation](#contents-section-3)
- [15.4 DRG Dependency Ordering & Topological Sort](#contents-section-4)
<!-- generated-toc:end -->

<a id="contents-section-1"></a>
## 15.1 Purpose

The Whole-Model Semantic Analysis stage (`dmn-semantic-analysis`) validates the semantic correctness of loaded DMN model sets.

It takes definitions with parsed FEEL AST nodes (`DmnFeelParser`) and resolves all symbol declarations, imports, types, and DRG execution dependencies before Runtime IR lowering.

<a id="contents-section-2"></a>
## 15.2 Scope & Symbol Resolution

- Manages hierarchical lexical scopes (global models, decision requirements, BKM parameters, boxed contexts, function definitions).
- Resolves local and cross-model symbol references (`DmnSymbolBinding`).
- Detects unknown, ambiguous, duplicate, or out-of-scope declarations with precise diagnostic paths.

<a id="contents-section-3"></a>
## 15.3 Type Inference & Validation

- Performs static type analysis and inference over FEEL expressions (`FeelTypeAnalyzer`, `DmnTypeAnalyzer`).
- Validates declared item definitions, type references (`typeRef`), decision table input/output types, and function signature overloads.

<a id="contents-section-4"></a>
## 15.4 DRG Dependency Ordering & Topological Sort

- Constructs whole-model Decision Requirement Graphs (`DmnDependencyAnalyzer`).
- Detects cyclic dependencies across decisions and BKMs.
- Computes deterministic execution topological order across multi-model sets (`DmnModelSetSemanticResult`).
