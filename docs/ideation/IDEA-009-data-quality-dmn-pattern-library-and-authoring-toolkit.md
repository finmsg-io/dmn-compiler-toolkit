# IDEA-009: Data Quality DMN Pattern Library & Authoring Toolkit

## Summary

Establish a production-grade, multi-file DMN pattern library and Just-In-Time (JIT) authoring harness specifically tailored for complex **Data Quality (DQ) Validations**.

This initiative serves a dual purpose:
1. **Battle-Testing the Compiler**: Uses complex, real-world multi-file DRGs/DRDs (with BKMs, decision services, custom ItemDefinitions, hit policy combinations, and FEEL logic) to stress-test `dmn-compiler-toolkit` across all execution targets (`DmnInterpreter`, `dmn-generator-java`, `dmn-generator-sparksql`, and `dmn-grpc`).
2. **Reusable Enterprise DMN Patterns**: Provides proven, modular DMN patterns (Field Hygiene, Cross-Field Consistency, Scorecard Weighting, Anomaly Flagging, Violation Struct Reporting) that teams can compose into enterprise decision pipelines.

---

## Key Pillars

### 1. Reusable DMN Design Patterns (Composable DRGs & BKMs)
- **Pattern 1: Field Hygiene & Canonicalization**: Regex validation, ISO date format checking, IBAN/SSN checksums, whitespace trimming, null-handling via reusable BKMs.
- **Pattern 2: Cross-Field Consistency**: Sequence validation (e.g. `startDate <= endDate`), sum/total matching, dependent enum verification.
- **Pattern 3: Data Quality Index (DQI) & Scorecard**: Weighted severity scoring (Critical, Warning, Info) computing overall dataset/record confidence.
- **Pattern 4: Structured Violation Reporting**: Emits standardized `QualityViolation` records containing `field`, `ruleId`, `severity`, `message`, and `suggestedFix`.

### 2. Multi-File DRG & BKM Architecture
- **Root Decision Models**: Import reusable domain BKMs and sub-decisions via namespace imports.
- **Transitive Model Sets**: Multi-level import graphs testing diamond dependencies and shared ItemDefinitions.

### 3. JIT Authoring Acceleration & Testing Tools
- **JIT CLI Engine / Test Harness**: Lightweight CLI tool to validate, compile, and execute authored `.dmn` files against JSON/CSV test scenarios in milliseconds.
- **Scenario Assertion Engine**: Declarative scenario tests asserting decision outputs against expected values for fast feedback during DMN authoring.

### 4. Repository Strategy: Hybrid Approach
- **Phase 1 (Monorepo Integration)**: Host models in `dmn-corpus` within `dmn-compiler-toolkit` for zero-friction CI testing and atomic compiler validation.
- **Phase 2 (Standalone Pattern Repository)**: Extract to `finmsg-io/dmn-dq-patterns` once patterns mature, allowing external enterprise applications to import the pattern library independently.

---

## Decision Gates

| Gate | Criterion | Target Evidence |
| --- | --- | --- |
| Gate 1 | Pattern Specification | Documented 4 core DQ DMN patterns with BKM signatures |
| Gate 2 | JIT Authoring Harness | CLI validator providing sub-10ms feedback on local `.dmn` files |
| Gate 3 | Multi-Backend Parity | 100% output parity between Interpreter, Java Generator, and Spark SQL Generator |
