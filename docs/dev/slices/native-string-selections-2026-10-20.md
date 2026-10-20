# Typed string selections and singleton result coercion

Continues from the verified full 34-fallback checkpoint: 3,391/3,391 passed;
2,681 native (98.75%), 34 fallback (1.25%) of 2,715 executable cases, plus
676 provisional expected model rejections. Prior batches remain uncommitted.

A terminal proof admits equality filters on a direct declared LIST<STRING>
input and constant-position sublists of the same input. Declared list/ANY
results retain lists; STRING results unwrap exactly one selected element and
otherwise return null, following shared declared-result coercion. Null input
and null elements retain selection semantics. Numeric/context filters stay
outside this proof to preserve exact representation and missing-field rules.

Upper/lower case over a proven equality filter uses the shared formatter and
ROOT case conversion on the constant match, then a SQL cardinality guard.
This preserves leading-plus formatting and Unicode behavior without relying on
Spark's case conversion. Null sources return null. Empty or multiple matches
retain the shared runtime's existing error behavior via native raise_error.
Routing does not depend on observed input values.

Bounds: direct typed input reference, one filter local binding at lexical depth
zero, equality to a string literal at most 2,048 characters with valid UTF-16;
sublist positions use existing bounded exact-integer validation. Named calls,
other predicates, captured references, arbitrary string functions, list-valued
case-call declarations and other input types remain outside the proof.

## Verification history

- Initial focused run stopped during test model construction because the manual
  fixture omitted its local slot count. Zero SQL actions: native/fallback rates
  undefined. Corrected the fixture's declared frame; production proof unchanged.
- Corrected focused regression passed: 78 native actions (100%), zero fallback
  (0%), denominator 78; failed/unknown zero. Covers declared ANY/STRING/list
  results, empty/single/multiple matches, null input/elements, sublist bounds,
  shared +1 formatting and Unicode case conversion. Maven exit zero. Generator
  target logs: `native-string-selection-focused-2026-10-20.log` (initial fixture
  failure) and `native-string-selection-focused-fixed-2026-10-20.log` (passed).
- Complete generator regression/install passed: 119/119 tests. Combined SQL:
  1,665 native actions (94.82%), 91 fallback (5.18%), denominator 1,756;
  failed/unknown zero. Hybrid integration: 1,655 native (94.95%), 88 fallback
  (5.05%) of 1,743. DMN integration: 10 native (76.92%), 3 fallback (23.08%)
  of 13. Formatting/install/Maven exit zero. Generator target log:
  `native-string-selection-generator-2026-10-20.log`.
- Target/installed generator JAR SHA256 match:
  `153F12374C6353E72BE7EDBF7D3BF4E06AB16D57C17D88FB242025573CAB725E`.
- Official singleton-list TCK passed: one native case (100%), zero fallback
  (0%), denominator one, failed/unknown zero; no expected model rejections,
  Maven exit zero. All five requested decisions run natively. Exact comparison
  to saved full baseline confirms `0021-singleton-list#001` changed fallback
  to native. No reversals or rejection changes within the tested suite.
  Runner target evidence: `native-string-selection-accounting-2026-10-20.json`
  and `native-string-selection-tck-2026-10-20.log`.
- Targeted estimate: 2,682 native (98.78%), 33 fallback (1.22%) of the unchanged
  2,715 executable baseline. This is not a new full result. Last full remains
  2,681 native (98.75%), 34 fallback (1.25%), with 676 provisional expected
  model rejections separately. Changes remain uncommitted.
