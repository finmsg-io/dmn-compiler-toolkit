# Exact Cartesian-loop multiplication in native Spark SQL

Two direct numeric-list inputs can now lower a Cartesian loop whose result is
the product of the two bound values. The proof checks declared list/result types,
distinct lexical slots, direct sources, no ranges and no partial-result binding.
SQL transforms retain source order and outer-loop evaluation order. Other loop
shapes retain their previous route.

Input projections carry unsigned unscaled digits, sign, scale and a deferred
invalid-number flag. SQL computes coefficient convolution and digit carry, applies
the sign and checks Java BigDecimal scale overflow. No whole-value double or
Spark decimal cast limits the product. Empty outer sources skip the inner source;
empty inner sources skip multiplication. Null results and non-finite numeric
operations preserve error paths.

The versioned payload codec now writes decimal tag 19 with an explicit signed
32-bit scale and unscaled integer string, and continues to read legacy tag 4.
This preserves decimals at both scale limits that scientific-text parsing could
not round-trip. Generated SQL emits this same representation; the result decoder
constructs values without performing arithmetic. Original input columns remain
unchanged. Readers/writers use the matching toolkit artifact.

## Verification

- Initial focused run completed 1 native action (100%), 0 fallback (0%) of 1,
  failed/unknown SQL routes 0, then failed a value assertion: coefficient products
  matched but negative-scale bytes were truncated. The first replacement missed
  the formatted source line; its rerun reproduced the same assertion failure
  with the same 1 native/0 fallback counts. Logs:
  `native-exact-product-focused-2026-10-22.log` and
  `native-exact-product-corrected-focused-2026-10-22.log`.
- Verified correction selects the low 32 scale bits before padding. Focused
  regression passed: 20 native actions (100%), 0 fallback (0%) of 20;
  failed/unknown 0. Both query forms match shared runtime results for coefficients
  beyond 38 digits, signs, scale retention, large/tiny exponents, both integer
  scale limits, zero scale saturation and null/empty source evaluation order.
  It also checks legacy decoding and new scale-limit payload round-trips.
  Log: `native-exact-product-patched-focused-2026-10-22.log`.
- Full generator regression/install passed 136/136 tests: 2,724 native actions
  (96.66%), 94 fallback (3.34%) of 2,818; unknown 0. Sixteen native SQL errors
  are intentional assertions (ten new checks plus six existing), all passed;
  no JUnit failures/errors/skips. Hybrid integration: 2,711 native (96.65%),
  94 fallback (3.35%) of 2,805; sixteen expected errors, unknown 0. DMN integration:
  13 native (100%), 0 fallback (0%) of 13; failed/unknown 0.
  Target/installed generator SHA256:
  `C78BA2CEE6A8E6B67FCBBA3B55E74D03BB43C95640BB1C9413C3EA4031708088`.
  Log: `native-exact-product-generator-2026-10-22.log`.
- Official loop suite passed 4/4: 3 native cases (75%), 1 fallback (25%) of 4;
  failed/unknown 0, expected model rejections 0. `0033-for-loops#002` changed
  FALLBACK -> NATIVE. No reversals or rejection changes against latest full
  accounting within the suite. Factor-loop case #003 remains fallback.
  Accounting: `native-exact-product-accounting-2026-10-22.json`.
  Log: `native-exact-product-tck-2026-10-22.log`.

Latest full official baseline: 2,701 native (99.48%), 14 fallback (0.52%) of
2,715 executable cases, plus 676 provisional expected model rejections. The targeted update is 2,702 native (99.52%), 13 fallback (0.48%) of 2,715.
This is not a new full result. Changes remain uncommitted after `264056f`.

## Subsequent full verification

The combined exact-numeric full run verified this loop conversion and
`0031-user-defined-functions#003`: 3,391/3,391 passed, 2,703 native (99.56%),
12 fallback (0.44%) of 2,715 executable cases, plus 676 expected model rejections;
failed/unknown 0, no route reversals or rejection changes. See the
[numeric function checkpoint](native-numeric-function-contexts-2026-10-22.md)
for the final generator hash, regression counts and remaining work.
