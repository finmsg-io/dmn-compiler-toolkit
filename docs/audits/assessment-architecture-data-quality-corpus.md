# Data Quality DMN Corpus Architecture Assessment

Assessment date: 2026-08-07  
Status: **IMPLEMENTED** (100% Production Ready)

## Executive Summary

The Production Data Quality DMN Corpus provides reference DMN 1.5 decision models specifically designed for enterprise data hygiene, cross-field business logic validation, and Data Quality Index (DQI) scoring.

## Architectural Flow

```text
DataFaker Payload Generator (BenchmarkDataGenerator)
   │
   ▼
DmnCompiler Facade
   │
   ├── dq-field-validation.dmn (Field Hygiene & Format Rules)
   ├── dq-cross-field-consistency.dmn (Multi-Field Business Rules)
   └── dq-scoring.dmn (DQI Index & Quality Status Scoring)
   │
   ├────────────────────────┬────────────────────────┐
   ▼                        ▼                        ▼
DmnRuntime               DmnJavaGenerator         DmnBenchmarks
(Interpreter)            (AOT Java Engine)        (JMH Workloads)
   │                        │                        │
   └────────────────────────┴────────────────────────┘
                            │
                            ▼
              Dual-Engine Value Parity & DQI Score Assertions
```

## Core Design Principles

1. **Realistic Master Data Validation**: Models validate real-world master data fields (email, IBAN, SSN, ISO dates, invoice subtotal/tax/total arithmetic, employment age consistency).
2. **Modular DMN Decision Graphs**: Separates field-level hygiene decisions from cross-field business consistency and aggregate DQI scoring.
3. **Structured Violation Contexts**: Decision outputs produce structured context lists containing violation codes, field names, and severity levels.
4. **Dual-Engine Value Parity**: Verified across both `DmnRuntime` interpreter and `dmn-generator-java` AOT bytecode generator.
