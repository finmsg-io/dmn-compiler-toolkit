# Exact native sorting over typed input lists

The generator recognizes inline two-parameter ascending or descending sort
comparators over a declared numeric/string list, or a declared context list with
one numeric/string sort member. The source must be a direct model input and the
result must retain the declared list type. Other comparator shapes retain their
prior route.

Projection rows carry canonical exact comparison keys and encoded original item
payloads. SQL array_sort performs the ordering and returns sorted payloads; the
existing result codec decodes the item values. Numeric values retain precision,
scale and exponent without double or Spark decimal conversion. Context rows
retain their contents. Comparison keys use Java string ordering. STRING input
transport retains the existing CharSequence-to-String boundary conversion.

Comparator outcomes follow the shared runtime, including its nonzero result for
equal non-null keys. Null context keys compare as unknown; null list items and
invalid numeric comparisons preserve error paths. Original input columns remain
available for existing consumers and fallback decisions.

## Verification

- Focused regression passed: 36 native actions (100%), 0 fallback (0%) of 36;
  failed/unknown 0. Both directions and both query forms cover exact decimals,
  large/tiny exponents, equal numeric keys with differing scales, complete
  context rows, supplementary Unicode strings, empty lists and null sources.
  Log: `native-exact-sort-focused-2026-10-22.log`.
- First full generator regression/install passed 133/133 tests: 2,694 native
  actions (96.84%), 88 fallback (3.16%) of 2,782; unknown 0. Six SQL errors are
  existing intentional error-preservation assertions, all passed. This run adds
  compatibility coverage for STRING CharSequence normalization.
  Log: `native-exact-sort-generator-2026-10-22.log`.
- Review then tightened eligibility: comparator parameter slots must be distinct
  and the declared comparator return type must preserve Boolean results. New
  regressions verify fallback behavior for aliased bindings and string/list
  return coercion. The first guarded run stopped at test compilation due to a
  wrong registration helper; native 0, fallback 0, denominator 0, rates undefined
  because no SQL executed. Corrected to the existing registerUdfs helper.
  Log: `native-exact-sort-guarded-generator-2026-10-22.log`.
- Corrected guarded full run had one assertion failure: native codec canonical
  numbers [3,1,2] were compared with Java Double expectations [3.0,1.0,2.0].
  Ordering matched. The other 133 tests passed. Route accounting: 2,694 native
  (96.80%), 89 fallback (3.20%) of 2,783; unknown 0, six expected SQL errors.
  Normalized the shared expected value through the existing native result codec.
  Log: `native-exact-sort-corrected-generator-2026-10-22.log`.
- Final full generator regression/install passed 134/134 tests: 2,694 native
  actions (96.63%), 94 fallback (3.37%) of 2,788; unknown 0. Six native SQL
  errors are intentional assertions; no JUnit failures, errors or skips.
  Hybrid integration: 2,681 native (96.61%), 94 fallback (3.39%) of 2,775;
  six expected errors, unknown 0. DMN integration: 13 native (100%), zero
  fallback (0%) of 13; failed/unknown 0. Six added fallback actions exercise
  comparator rejection guards, rather than representing route regressions.
  Target and installed generator SHA256:
  `591FB4471C7F408BCEB298F2C69D3DE84F37BEA9EC51D71699379D726EC61D7B`.
  Log: `native-exact-sort-final-generator-2026-10-22.log`.
- Full official Spark SQL TCK passed 3,391/3,391 cases: 2,701 native (99.48%),
  14 fallback (0.52%) of 2,715 executable cases, plus 676 unchanged provisional
  expected model rejections. Failed/unknown, errors, unsupported, excluded and
  invalid 0. Exact previous-full comparison: six forward conversions, no
  reversals, identical case keys and no rejection changes. New `0013-sort#001`
  is fully native across its three requested decisions. The other five forward
  changes confirm earlier calendar and join conversions.
  Accounting: `native-exact-sort-full-accounting-2026-10-22.json`.
  Log: `native-exact-sort-full-tck-2026-10-22.log`.

The new verified full baseline has 14 remaining fallback cases: temporal
construction (1), numeric functions (3), iteration/loops (3), lending (7).
This checkpoint accompanies the implementation commit requested after verification.
