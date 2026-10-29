# Numeric context loops in generated Spark SQL

The new bounded loop proof admits arithmetic over numeric fields of one direct
context-list input. It validates the declared numeric-list result, context field
layout, lexical depth and local slot, direct input identity, supported operators,
expression depth and node budget. Statically bound FEEL functions are inlined
before this proof. SQL input metadata records the source slot and field names,
so row construction can use the original model before BKM inlining.

Projection converts input representation components only. SQL evaluates arithmetic
nodes in topological order with a bounded aggregate of decimal representations.
This limits analyzer nesting for formulas with repeated division and power.

Power uses Spark's built-in `java_method` expression to invoke
`java.lang.Math.pow`. The runtime also uses Math.pow and BigDecimal.valueOf(double).
The SQL path reconstructs the exact decimal from the returned Double.toString
representation, including its preferred scale, and rejects non-finite results.
This route contains Java reflection in a Spark built-in expression; it contains
no FEEL interpreter or ScalaUDF fallback. The other arithmetic uses native SQL
digit addition, subtraction, multiplication and DECIMAL128 division.

## Verification history

- Initial focused run failed both capability assertions before executing SQL:
  0 native, 0 fallback, denominator 0; rates undefined. Dynamic context-item paths
  were rejected by static function lowering. Path preservation is now scoped to
  the bounded numeric-context loop proof.
- After path correction, the power test passed with 2 native actions (100%),
  0 fallback (0%) of 2; failed/unknown routes 0. The payment test then failed in
  analysis with Spark's default 100-iteration resolution limit, before an
  accounted SQL action. Arithmetic emission was changed to a flat topological
  aggregate instead of deeply nested arithmetic lambdas. Analyzer settings
  are unchanged.

Flattened-plan regression passed both tests with default analyzer settings:
10 native SQL actions (100%), 0 fallback (0%) of 10; failed/unknown 0.
Payment outputs match the shared runtime exactly, including list order and scale;
null/empty source lists retain their behavior. Power checks cover signs,
fractional exponents, exact decimal conversion and large/small exponents.
Log: `native-payment-loop-staged-focused-2026-10-23.log`.

Expanded validation completed 22 native actions (100%), 0 fallback (0%) of 22;
unknown 0, twelve expected native SQL errors. One assertion then failed because
the reference runtime returned null for a declared list containing a context with
missing required fields, rather than throwing. The fixture expectation was
corrected; invalid context items can null the whole input list during declared
input coercion. SQL projection applies that same coercion before transforming.
The next fixture run completed 24 native actions (100%), 0 fallback (0%) of
24; unknown 0, twelve expected native SQL errors. It verified the missing-field
input, then stopped on a reference-runtime null-result exception for an explicit
null context item. Explicit null items are retained, unlike contexts missing
required fields; the fixture now tests these separately. No production change
was required for either fixture correction. Final focused validation passed all three tests: 26 native SQL actions (100%),
0 fallback (0%) of 26; unknown 0. Fourteen native SQL errors are intentional
passed assertions for non-finite power, invalid numbers and null loop results.
The two normal tests also pass exact payment values, sign/underflow/zero power
cases, missing-field input coercion, null sources, empty lists and list order.
Log: `native-payment-loop-final-validation-2026-10-23.log`.
A non-executing capability diagnostic now classifies the official 0003-iteration
decision as native; this alone is not an official conversion result.

Full generator regression/install passed 145/145 tests, no JUnit failures,
errors or skips: 2,874 native SQL actions (96.77%), 96 fallback (3.23%) of
2,970; unknown 0. Forty-eight native SQL errors are intentional passed assertions
(fourteen new loop/power checks plus thirty-four existing). Hybrid integration:
2,861 native (96.75%), 96 fallback (3.25%) of 2,957; failed 48 expected,
unknown 0. DMN integration: 13 native (100%), 0 fallback (0%) of 13;
failed/unknown 0. Log: `native-payment-loop-generator-2026-10-23.log`.
Built and installed generator SHA256:
`AEDE0F6878CDF7287CD1D08C578480C538C20C3B04CF887FB9D7BC5F4866AB00`.

Full official TCK passed 3,391/3,391: 2,707 native (99.71%), 8 fallback
(0.29%) of 2,715 executable cases, plus 676 expected model rejections.
Failed/errors/unknown/unsupported/excluded/invalid: zero. Exact prior-full
comparison has identical case keys, one FALLBACK -> NATIVE conversion
(`0003-iteration#001`), no route reversals and no rejection changes.
Accounting: `native-payment-loop-full-accounting-2026-10-23.json`.
Log: `native-payment-loop-full-tck-2026-10-23.log`.

## Remaining fallback cases

- `0007-date-time#001`: temporal constructor/transport/validation chain.
- `0004-lending#001`, `0014-loan-comparison#001`,
  `0087-chapter-11-example#001/#002/#003d/#003e/#004`: composite lending.

Next inspect shared lending BKM/scalar numeric boundaries and reuse the flat
exact arithmetic/power plan only for fully proven result shapes. Temporal
constructors require a separate lossless transport and validation chain.
Changes remain uncommitted after `611797e`.

