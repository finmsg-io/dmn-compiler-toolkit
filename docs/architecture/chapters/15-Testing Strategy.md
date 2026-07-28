# Chapter 15 --- Testing Strategy \[PROVISIONAL\]

## 15.1 Current Baseline

The active frontend module declares JUnit Jupiter and AssertJ test
dependencies and contains the Traffic Violation DMN as a representative
resource. The current validation has primarily been performed by reading
the model and inspecting protobuf text output.

This is sufficient for bootstrap development but not for regression
protection.

## 15.2 Immediate Test Scope

The next test suite should assert the current implementation exactly.

### Definitions

-   root id and name
-   namespace
-   expression language
-   type language

### Item definitions

-   three item definitions
-   five `tDriver` components
-   five `tViolation` components
-   two `tFine` components
-   built-in type normalization
-   allowed-values constraint preservation

### DRG

-   two input-data elements
-   two decisions
-   typed variables
-   information-requirement hrefs

### Decision table

-   UNIQUE hit policy
-   Rule-as-Row orientation
-   two typed inputs
-   two typed outputs
-   four ordered rules
-   unary-test and output source preservation

### Context

-   two context entries
-   local `TotalPoints` variable
-   both FEEL source expressions

## 15.3 Test Layers

``` text
1. XmlCursor unit tests
2. individual reader tests
3. complete DmnXmlReader integration tests
4. protobuf serialization determinism tests
5. negative XML and exception tests
6. later FEEL parser tests
7. later semantic analysis and Runtime IR equivalence tests
```

## 15.4 Golden Output

A normalized protobuf text or binary fixture may be used as a golden
result, but focused semantic assertions remain preferable because
additive protobuf fields should not unnecessarily break every test.

## 15.5 Determinism

Reading identical bytes must produce byte-identical deterministic
protobuf serialization when the same serialization mode is used. Rule
indexes, repeated-field order, and DRG-element order must remain stable.

## 15.6 Unsupported Content Tests

Tests should document intentionally unsupported content. Silent ignoring
is acceptable only while explicitly covered by a test and roadmap item.
Once diagnostics are implemented, unsupported semantic content should
produce structured diagnostics.

## 15.7 Security Tests

Before production use, add tests for:

-   external entities
-   entity expansion
-   malformed namespace declarations
-   excessive nesting
-   oversized attributes and text
-   invalid numeric and boolean attributes
-   malformed UTF-8

## 15.8 Future Cross-Engine Tests

After execution exists, compare results with an established DMN
implementation for compliance models. Performance measurements must be
separated from semantic correctness tests.

------------------------------------------------------------------------
