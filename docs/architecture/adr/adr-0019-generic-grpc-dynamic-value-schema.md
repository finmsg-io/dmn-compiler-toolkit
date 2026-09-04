# ADR-0019 — Generic gRPC Dynamic-Value Schema

<!-- generated-toc:start -->
## Table of contents

- [Context]##ontents-section-1)
- [Decision]##ontents-section-2)
- [Consequences](#contents-section-3)
<!-- generated-toc:end -->

| Field | Value |
| --- | --- |
| Status | Accepted |
| Decision | ADR-0019 |

<a id="contents-section-1"></a>
## Context

A generic gRPC service allows clients to invoke arbitrary DMN models dynamically without requiring pre-compiled proto schemas for each specific decision model. A dynamic schema must represent all DMN/FEEL primitive types, temporal values, lists, and contexts while preserving numerical precision.

---

<a id="contents-section-2"></a>
## Decision

1. **Polymorphic Value Schema**: Define `io.finmsg.dmn.grpc.Value` using a Protobuf `oneof value_kind` containing:
   - `null_value` (bool)
   - `boolean_value`(bool)
   - `number_value` (string – preserving arbitrary-precision `BigDecimal` representation without IEEE-754 floating-point inaccuracy)
   - `string_value` (string)
   - `date_value`, `time_value`, `date_time_value`, `duration_value` (ISO-8601 strings)
   - `list_value` (`ListValue` with repeated `Value`)
   - `context_value` (`ContextValue` with string-keyed `map<string, Value>`)
2. **Evaluation Service Contract**: Standardize `DmnEvaluationService.Evaluate(DmnEvaluationRequest) -> DmnEvaluationResponse`, carrying model identification(`model_namespace`, `model_name`), named input map, output map, and structured evaluation diagnostics.
3. **Bi-directional Conversion**: Implement `DmnGrpcValueConverter` to translate seamlessly between Protobuf `Value` structures and native Java runtime objects / `DmnCompiledModel`.

---

<a id="contents-section-3"></a>
## Consequences

Advantages

* Single gRPC service contract capable of evaluating any DMN model dynamically.
* Exact decimal representation avoids floating point rounding artifacts in financial decisions.
* Clean recursive structure for arbitrary nested context and list types.

Trade-offs

* Dynamic schema incurs slightly more serialization overhead compared to strongly-typed generated Protobuf messages (`dmn-grpc` typed mode).
