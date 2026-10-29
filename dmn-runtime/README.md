# `dmn-runtime`

Deterministic process-local interpreter for executable `RuntimeModel` IR.

## Key Assets

- **`DmnRuntime`**: Evaluates slot maps, lexical frames, FEEL built-in functions, and decision table hit policies (`UNIQUE`, `FIRST`, `COLLECT`, `RULE ORDER`, `OUTPUT ORDER`).
- **Zero Framework Dependencies**: Evaluates decisions without dependencies on XML parsers, ANTLR, or Protobuf reflection.
- **Spec Conformance**: 100% OMG DMN 1.5 TCK certified across 3,611 compliant test cases.

## Usage

```java
DmnEvaluationResult result = new DmnRuntime().evaluate(runtimeModel, inputs);
Object value = result.value(decision.resultSlot());
```

## External Java execution policy

Model-defined Java calls are denied by default, including calls through the
two-argument `DmnRuntime.invokeExternalJava` helper. Denial throws
`ExternalJavaDeniedException` with an `EXTERNAL_JAVA_DENIED:` diagnostic before
resolving target or parameter classes. This changes the previous unrestricted
default. Hosts must explicitly approve each class and signature they require:

```java
var policy = ExternalJavaPolicy.allowOnly(Set.of(
    new ExternalJavaPolicy.Method("java.lang.Math", "max(double,double)")));
var runtime = new DmnRuntime(policy);
```

Class and signature strings are trimmed; internal signature whitespace remains
significant. `unrestrictedTrustedUse()` is an explicit compatibility option for
trusted models. An approved method has its normal Java effects; this policy does
not sandbox the method's implementation. Policies are immutable and owned by the
runtime instance, with no process-wide mutable authorization state.

See the [cross-backend policy trace](../docs/dev/slices/security-correctness-hardening-task-2026-10-24.md#1-external-java-function-policy-highest-priority)
for generated code, Spark folding, fallback, and gRPC boundaries.
