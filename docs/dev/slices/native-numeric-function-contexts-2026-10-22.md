# Exact numeric function contexts in native Spark SQL

Static function binding now resolves function members in literal contexts and
producer decisions, including named arguments and nested calls. Lexical bindings
are substituted before SQL generation. A numeric context is admitted only when
its complete result has a proven exact arithmetic route; division retains fallback.

Exact numeric input projections carry coefficient digits, scale, sign and deferred
invalid-value information. SQL performs addition, subtraction, multiplication and
numeric comparisons over those components, then emits the versioned decimal
payload. Input projection performs representation conversion only. Schema metadata
records source slots so callers can build rows from the original model after
function inlining has changed the generated expression.

The terminal context proof checks declared numeric fields, bounded expressions,
direct numeric inputs and supported conditionals/ranges. It preserves entry order,
null handling and exact decimal scale. Function-valued producer results continue
to use their existing route when requested directly.

## Verification

Focused regression passed both tests: 32 native SQL actions (100%), 0 fallback
(0%) of 32, failed/unknown 0. It checks both query forms for direct contexts,
inline function contexts, individual producer-context consumers, named argument
order, nested calls, values beyond double precision, signs, scale and nulls.
Log: `native-numeric-functions-focused-2026-10-22.log`.

The first full generator run failed four existing context capability assertions:
138 tests, four failures, no errors/skips. It recorded 2,726 native actions
(96.67%), 94 fallback (3.33%) of 2,820; unknown 0, sixteen intentional native SQL
errors. A new context type retagging rule had also changed ordinary context
coercion. The rule is now restricted to decisions that bind context functions.
Log: `native-numeric-functions-full-generator-2026-10-22.log`.

Corrected full generator regression/install passed 138/138 tests, no JUnit
failures/errors/skips: 2,756 native SQL actions (96.70%), 94 fallback (3.30%)
of 2,850; unknown 0. Sixteen native SQL errors are intentional passed assertions.
Hybrid integration: 2,743 native (96.69%), 94 fallback (3.31%) of 2,837;
sixteen expected errors, unknown 0. DMN integration: 13 native (100%),
0 fallback (0%) of 13; failed/unknown 0.
Log: `native-numeric-functions-corrected-generator-2026-10-22.log`.
Target and installed generator SHA256:
`7F54771BB1B1439751478D6808D2E5F5744338F697956222046613C92323EF6C`.

Full official TCK passed 3,391/3,391: 2,703 native (99.56%), 12 fallback
(0.44%) of 2,715 executable cases, plus 676 expected model rejections.
Failed/errors/unknown/unsupported/excluded/invalid: zero. Exact prior-full
comparison has identical case keys, two FALLBACK -> NATIVE conversions
(`0031-user-defined-functions#003`, `0033-for-loops#002`), no reversals and
no rejection changes. This supersedes the prior targeted estimates.
Accounting: `native-numeric-functions-full-accounting-2026-10-22.json`.
Log: `native-numeric-functions-full-tck-2026-10-22.log`.

## Remaining fallback cases

- `0007-date-time#001`: temporal construction/validation.
- `0031-user-defined-functions#001/#002`: exact numeric division in function contexts.
- `0003-iteration#001`, `0033-for-loops#003`: iteration and factor loops.
- `0004-lending#001`, `0014-loan-comparison#001`,
  `0087-chapter-11-example#001/#002/#003d/#003e/#004`: composite lending.

