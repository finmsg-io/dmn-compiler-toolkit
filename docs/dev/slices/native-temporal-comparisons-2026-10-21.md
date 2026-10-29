# Native typed comparison tables

The generator now recognizes bounded UNIQUE comparison tables over declared
inputs and context members. Equality/negation and three-way ordering tables use
SQL comparisons over canonical value components. Original input columns remain
available for fallback decisions. Projection column names avoid collisions.

Temporal components preserve nanoseconds, local versus offset identity, named
zones and duration kinds. Equality follows shared runtime millisecond semantics
where applicable; ordering retains nanoseconds. SQL rejects overlapping UNIQUE
matches instead of choosing an arbitrary rule. Numeric components avoid double
rounding. Duration subtype coercion now accepts only the declared Duration or
Period kind, including context fields.

## Verification

- Runtime validation passed 9 tests. No SQL executed: native 0, fallback 0,
  denominator 0; rates undefined.
- Restored focused temporal regression passed: 40 native actions (100%),
  fallback 0 (0%), denominator 40; failed/unknown 0.
- Expanded regression completed 66 native actions (100%), fallback 0 (0%),
  denominator 66; failed/unknown SQL routes 0, then encountered a test assertion
  assuming an extra projection for already-native string tables. Corrected.
- The first full run had one erroneous test fixture: negation was assigned to
  the wildcard field. Completed SQL actions: 2,536 native (96.65%), 88 fallback
  (3.35%) of 2,624; failed/unknown SQL routes 0. Corrected the fixture.
- Corrected full generator regression/install passed 129/129 tests: 2,550
  native actions (96.66%), 88 fallback (3.34%) of 2,638; failed/unknown 0.
  Hybrid integration: 2,537 native (96.65%), 88 fallback (3.35%) of 2,625.
  DMN integration: 13 native (100%), 0 fallback (0%) of 13.
  Target and installed generator SHA256:
  `9897A6DB374BA2AEE2F6291628A8F0B98FE1D700FA09C0EBE4EFE63246865C87`.
  Log: `native-temporal-corrected-generator-2026-10-21.log`.
- Full official TCK passed 3,391/3,391: 2,695 native cases (99.26%),
  20 fallback (0.74%) of 2,715 executable cases. Expected model rejections
  remain 676. Failed/unknown, errors, unsupported, excluded and invalid 0.
  Exact comparison with the prior full baseline: six forward conversions,
  no reversals, identical case keys and no rejection changes. All three
  `0036-dt-variable-input` cases are newly native in this batch.
  Accounting: `native-temporal-full-accounting-2026-10-21.json`.
  Log: `native-temporal-full-tck-2026-10-21.log`.

The remaining 20 fallback cases are calendar arithmetic (4), temporal
construction (1), numeric functions (3), collections/joins/sort/loops (5)
and composite lending (7). Changes remain uncommitted.
