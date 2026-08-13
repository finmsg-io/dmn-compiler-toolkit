# Backend Capability and Parity Matrix

> **Normative Specification:** This document formalizes feature support, type mappings, and semantic parity guarantees across all DMN execution backends: the reference interpreter (`dmn-runtime`), ahead-of-time Java generator (`dmn-generator-java`), Spark/Databricks SQL CTE generator (`dmn-generator-sparksql`), and static compiler optimizer (`dmn-optimizer`).

Status: Living Architectural Specification  
Last Reviewed: 2026-08-08  

---

## 1. Execution Backend Overview

| Backend | Module | Primary Execution Target | Optimization Engine | Reflection Dependency |
| --- | --- | --- | --- | :---: |
| **Interpreter** | [`dmn-runtime`](file:///c:/00-finmsg.io/dmn-compiler-toolkit/dmn-runtime) | In-process JVM decision evaluation | Dynamic lexical frames & IR walking | Zero |
| **Java Emitter** | [`dmn-generator-java`](file:///c:/00-finmsg.io/dmn-compiler-toolkit/dmn-generator-java) | Microsecond JVM microservices & gRPC | Direct Java bytecode compilation | Zero |
| **Spark SQL Emitter** | [`dmn-generator-sparksql`](file:///c:/00-finmsg.io/dmn-compiler-toolkit/dmn-generator-sparksql) | Distributed Spark / Databricks batch/streaming | Catalyst / Photon / Whole-Stage Codegen | Zero (Zero UDF) |
| **Static Optimizer** | [`dmn-optimizer`](file:///c:/00-finmsg.io/dmn-compiler-toolkit/dmn-optimizer) | Compile-time IR transformation | Algebraic simplification & rule pruning | Zero |

---

## 2. FEEL Type & Value Representation

| FEEL Type | `DmnRuntime` | `dmn-generator-java` | `dmn-generator-sparksql` | Parity Status |
| --- | --- | --- | --- | :---: |
| **`string`** | `java.lang.String` | `java.lang.String` | `StringType` / `VARCHAR` | **Full Parity** |
| **`number`** | `java.math.BigDecimal` (DECIMAL128) | `java.math.BigDecimal` | `DecimalType(38, 18)` | **Full Parity** |
| **`boolean`** | `java.lang.Boolean` (3-valued) | `boolean` / `Boolean` | `BooleanType` | **Full Parity** |
| **`date`** | `java.time.LocalDate` | `java.time.LocalDate` | `DateType` | **Full Parity** |
| **`time`** | `java.time.LocalTime` / `OffsetTime` | `java.time.LocalTime` | `TimestampType` | **Full Parity** |
| **`date and time`** | `java.time.ZonedDateTime` | `java.time.ZonedDateTime` | `TimestampType` | **Full Parity** |
| **`years and months duration`** | `java.time.Period` | `java.time.Period` | `YearMonthIntervalType` | **Full Parity** |
| **`days and time duration`** | `java.time.Duration` | `java.time.Duration` | `DayTimeIntervalType` | **Full Parity** |
| **`list<T>`** | `java.util.List<Object>` | `java.util.List<T>` | `ArrayType(ElementType)` | **Full Parity** |
| **`context`** | `java.util.Map<String, Object>` | Generated Record / `Map` | `StructType(Fields)` | **Full Parity** |

---

## 3. Decision Table Hit Policy Support

| Hit Policy | `DmnRuntime` | `dmn-generator-java` | `dmn-generator-sparksql` | Notes |
| --- | :---: | :---: | :---: | --- |
| **`UNIQUE` (U)** | Supported | Supported | Supported (`CASE WHEN`) | Returns single matching row or null |
| **`FIRST` (F)** | Supported | Supported | Supported (`CASE WHEN`) | Returns first matching row |
| **`PRIORITY` (P)** | Supported | Supported | Supported | Evaluated by priority order |
| **`ANY` (A)** | Supported | Supported | Supported | Asserts all matching rules output identical result |
| **`COLLECT` (C)** | Supported | Supported | Supported (`ARRAY()`) | Returns list of all rule matches |
| **`COLLECT +`** | Supported | Supported | Supported (`AGGREGATE`) | Sum of outputs |
| **`COLLECT <` / `>`** | Supported | Supported | Supported (`MIN`/`MAX`) | Min or max of outputs |
| **`COLLECT #`** | Supported | Supported | Supported (`SIZE`) | Count of matches |
| **`RULE ORDER` (R)** | Supported | Supported | Supported (`ARRAY`) | List of outputs in rule order |
| **`OUTPUT ORDER` (O)** | Supported | Supported | Supported (`ARRAY`) | List of outputs sorted by output order |

---

## 4. Null Handling & Error Semantics

| Scenario | FEEL Specification | `DmnRuntime` | `dmn-generator-java` | `dmn-generator-sparksql` |
| --- | --- | --- | --- | --- |
| **Division by zero** | Returns `null` | Returns `null` | Returns `null` (ArithmeticCatch) | Returns `null` (`TRY_DIVIDE` / Spark standard) |
| **Null binary predicate** | Returns `null` / `false` | 3-valued boolean logic | 3-valued boolean logic | 3-valued SQL logic (`IS NULL`) |
| **Out-of-bounds list index** | Returns `null` | Returns `null` | Returns `null` | Returns `null` (`element_at`) |
| **Type mismatch** | Returns `null` + Diagnostic | Diagnostic + `null` | Diagnostic + `null` | Returns `null` |

---

## 5. Verification & Parity Enforcement

Backend parity is continuously verified through the following automated test suites:
- **OMG DMN TCK Engine:** [`OfficialTckSuiteTest`](file:///c:/00-finmsg.io/dmn-compiler-toolkit/dmn-tck-runner/src/test/java/io/finmsg/dmn/tck/OfficialTckSuiteTest.java) executes 3,611 compliant test cases across both `DmnRuntime` and `dmn-generator-java`.
- **Spark SQL Integration Suite:** [`SparkSqlDmnIntegrationTest`](file:///c:/00-finmsg.io/dmn-compiler-toolkit/dmn-generator-sparksql/src/test/java/io/finmsg/dmn/generator/sparksql/SparkSqlDmnIntegrationTest.java) asserts SQL CTE result equivalence against the reference interpreter.
