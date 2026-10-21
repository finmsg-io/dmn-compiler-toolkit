# Bounded string sort specialization

A fixed list of at most 16 non-null string literals can be sorted natively when
a two-string-parameter comparator is a Boolean conditional over equality between
one parameter and a bounded string literal or a declared STRING input. The
function lowerer resolves BKM bindings and captured lexical frames before the
proof runs. Both positional invocation and function-call forms are supported.

Generation evaluates the bounded sort through the shared runtime, preserving its
comparator behavior and duplicate ordering. For a dynamic input, each distinct
source string is one possible equality outcome; all other values share the null
input outcome. The result is a bounded native SQL CASE selecting precomputed
literal lists. Query execution does not call a sort UDF or evaluate the comparator
in an adapter. String input coercion remains the existing shared coercion.

Arbitrary predicates, additional free references, dynamic source lists, non-string
parameters, oversized lists/text, external functions and other return kinds do
not satisfy the proof. A shared-runtime failure during specialization preserves
the existing fallback route.

## Verification

- Focused regression passed: 64 native SQL actions (100%), zero fallback (0%),
  denominator 64; failed/unknown zero. One JUnit test passed. Shared runtime
  comparisons cover duplicate strings, captured closures passed through another
  BKM, comparator BKM references, function-call and positional invocation forms,
  matching/nonmatching/null/numeric/singleton-list/empty-list inputs, and both
  individual and AllDecisions SQL. Generator target log:
  `native-bounded-sort-focused-2026-10-20.log`.
- The first full generator regression/install passed: 123/123 tests; 2,079
  native actions (95.81%), 91 fallback (4.19%), denominator 2,170;
  failed/unknown zero. Log: `native-bounded-sort-generator-2026-10-20.log`.
  Route inspection then found that a captured input with a nonzero value slot
  failed temporary-model validation. The synthetic evaluator now normalizes
  its single input to slot zero. This was an incomplete conversion, not a
  failed SQL test.
- Corrected focused regression passed: 64 native actions (100%), zero fallback
  (0%) of 64; failed/unknown zero. The captured input now occupies a nonzero
  model slot and the decision declares ANY. Log:
  `native-bounded-sort-normalized-focused-2026-10-20.log`.
- Corrected full generator regression/install passed: 123/123 tests; 2,079
  native actions (95.81%), 91 fallback (4.19%) of 2,170; failed/unknown zero.
  Hybrid integration: 2,069 native (95.92%), 88 fallback (4.08%) of 2,157.
  DMN integration: 10 native (76.92%), 3 fallback (23.08%) of 13.
  Formatting/install/Maven exit zero. Generator target log:
  `native-bounded-sort-normalized-generator-2026-10-20.log`.
  Target/installed JAR SHA256:
  `0A681BD48274D956D4CC0076B3028976C27D89857BF23FFD0355DCE68C11FBD7`.
- Official lambda and service TCK suites passed: 19/19; 19 native (100%),
  zero fallback (0%) of 19; failed/unknown zero, no expected model rejections.
  Two new conversions: `0092-feel-lambda#017` and `#018`. Prior conversion
  `0088-no-decision-logic#001` remains native. Exact comparison with the last
  full baseline confirms three forward conversions, zero reversals and no
  rejection changes within these suites. Maven exit zero. Runner target evidence:
  `native-bounded-sort-accounting-2026-10-20.json` and
  `native-bounded-sort-tck-2026-10-20.log`.
- Combined targeted estimate: 2,688 native (99.01%), 27 fallback (0.99%) of
  2,715 executable baseline cases. This is not a new full result. Last full
  remains 2,685 native (98.90%), 30 fallback (1.10%), plus 676 provisional
  expected model rejections separately. Three verified conversions since the
  last full checkpoint. The temporary evaluator normalizes the captured input
  to slot zero; generated SQL retains the original model input reference.

This batch and the preceding service-string batch remain uncommitted. The
100% native goal stays open.
