# Integral quotient loops in native Spark SQL

A single direct numeric-list loop can now inline a statically bound FEEL function
and lower the predicate `value / factor = decimal(value / factor, 0)` to native
SQL. The proof checks the full predicate, identical quotient operands, scale zero,
direct numeric inputs, lexical depth and slot identity, and the absence of ranges
or partial-result binding. Other loop shapes retain their existing route.

SQL computes the DECIMAL128 quotient with the shared exact division implementation
and checks whether that rounded quotient is integral. It does not substitute a
remainder test, which would differ for high-precision inputs. Null quotients match
the runtime equality of two nulls. SQL transform preserves list order, null lists,
empty lists and deferred validation. Input metadata records source slots so rows
can be built from the original model before BKM inlining. Projection converts
numeric representation only; no quotient or predicate is calculated in Java.

## Verification

Initial focused regression passed: 14 native actions (100%), 0 fallback (0%) of
14; failed/unknown 0. Cases cover both query forms, signs, fractional/zero/null
factors, precision-dependent integrality, null values, empty lists and deferred
invalid-numerator handling. Log: `native-factor-loop-focused-2026-10-22.log`.

Expanded validation regression passed two tests: 22 native actions (91.67%),
2 fallback (8.33%) of 24; unknown 0. Eight native SQL errors are intentional
passed assertions for invalid numbers and scale failures. The two fallback actions
verify that a nonzero decimal rounding scale retains its original route and values.
Log: `native-factor-loop-validation-2026-10-22.log`.

Full generator regression/install passed 142/142 tests, no JUnit failures,
errors or skips: 2,848 native actions (96.74%), 96 fallback (3.26%) of 2,944;
unknown 0. Thirty-four native SQL errors are intentional passed assertions.
Hybrid integration: 2,835 native (96.72%), 96 fallback (3.28%) of 2,931;
34 expected errors, unknown 0. DMN integration: 13 native (100%), 0 fallback
(0%) of 13; failed/unknown 0. The two new fallback actions validate the
nonzero-rounding-scale guard. Log: `native-factor-loop-generator-2026-10-22.log`.
Target and installed generator SHA256:
`01622A382429DE7CAE75B7E24874CEBBD0615A1777653EE568D8B85098407513`.

Initial official loop verification passed 4/4 but did not convert the factor
case: 3 native (75%), 1 fallback (25%) of 4; failed/unknown 0, expected model
rejections 0. The compiler emitted LIST<ANY> for the loop expression beneath its
declared LIST<BOOLEAN> decision result, while the first fixture already had a
typed loop. The lowering now infers the proven boolean body result and the fixture
uses the compiler expression type. Focused and full verification are being rerun.
Initial accounting: `native-factor-loop-initial-accounting-2026-10-22.json`.
Initial official log: `native-factor-loop-tck-2026-10-22.log`.

Corrected focused regression with compiler-shaped LIST<ANY> expressions passed:
22 native (91.67%), 2 fallback (8.33%) of 24; unknown 0, eight intentional
native SQL errors. A non-executing capability diagnostic now classifies every
0033 decision as native. This diagnostic alone is not a TCK conversion result.
Log: `native-factor-loop-typed-focused-2026-10-22.log`.
Corrected full generator regression/install passed 142/142 tests with the same
2,848 native (96.74%), 96 fallback (3.26%) of 2,944; unknown 0, 34 intentional
native SQL errors, no JUnit failures/errors/skips. Hybrid integration: 2,835 native
(96.72%), 96 fallback (3.28%) of 2,931; DMN integration: 13 native (100%),
0 fallback (0%) of 13. Built and installed generator SHA256:
`7F9423C1437840B3A4248B1C63879D421573EBCB6D1616F7A56B183402CFC174`.
Log: `native-factor-loop-typed-generator-2026-10-22.log`.
Full official TCK passed 3,391/3,391: 2,706 native (99.67%), 9 fallback
(0.33%) of 2,715 executable cases, plus 676 expected model rejections.
Failed/errors/unknown/unsupported/excluded/invalid: zero. Exact prior-full
comparison has identical case keys and three FALLBACK -> NATIVE conversions:
`0031-user-defined-functions#001/#002` and `0033-for-loops#003`. No route
reversals or rejection changes. This supersedes the targeted estimates.
Accounting: `native-factor-loop-full-accounting-2026-10-22.json`.
Log: `native-factor-loop-full-tck-2026-10-22.log`.

## Remaining fallback cases

- `0007-date-time#001`: temporal constructors, transport, arithmetic and properties.
- `0003-iteration#001`: payment iteration with power and decimal arithmetic.
- `0004-lending#001`, `0014-loan-comparison#001`,
  `0087-chapter-11-example#001/#002/#003d/#003e/#004`: seven composite lending cases.

This verified checkpoint accompanies the requested implementation/documentation commit.

