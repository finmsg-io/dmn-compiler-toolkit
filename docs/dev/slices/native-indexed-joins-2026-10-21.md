# Native indexed joins over typed context lists

A terminal string result can now lower nested equality filters followed by
first-item selection. The shape selects a row from one context list by a string
input, selects its numeric join key, filters a second context list using that
key, and returns the first matching string field. Proof checks declared input
and member types, lexical bindings, constant first positions and result type.
Other shapes retain their previous route.

Input projections contain canonical exact numeric keys, deferred invalid-number
flags, string equality keys and string result fields. Java string equality keys
preserve UTF-16 units and distinguish null from unsupported CharSequence values.
SQL performs selection and comparison. Original input columns remain available.
No per-row FEEL evaluation is hidden in projection encoding.

SQL preserves source order, duplicate matches and the shared distinction between
null and empty inner sources. Filters scan all matching candidates before first
selection, so an invalid later numeric key still raises. Matched null rows also
raise, matching the shared runtime's immutable-list rejection.

## Verification

- Initial focused run stopped at runtime IR fixture validation because the local
  frame count was too small. Native 0, fallback 0, denominator 0; rates undefined
  because no SQL executed. Corrected the fixture local-slot count. Removed
  nested-list field fixtures unsupported by the existing native input encoder.
- Corrected focused regression passed: 34 native actions (100%), 0 fallback
  (0%) of 34; failed/unknown 0. Covers duplicate matches, exact keys beyond double
  precision, nearby 1e-38 differences, extreme BigDecimal scales, null keys,
  null/empty lists, unrelated NaN values and CharSequence equality distinctions.
  Both query forms match the shared runtime. Original input columns unchanged.
  Log: `native-indexed-join-corrected-focused-2026-10-21.log`.
- Full generator regression/install passed 132/132 JUnit tests, with no failures,
  errors or skips. Route accounting: 2,658 native actions (96.80%), 88 fallback
  (3.20%) of 2,746; unknown 0. Six native SQL errors are intentional assertions
  for later NaN and matched-null rows, all passed. Successful actions alone:
  2,652 native (96.79%), 88 fallback (3.21%) of 2,740.
  Hybrid integration: 2,645 native (96.78%), 88 fallback (3.22%) of 2,733;
  six expected SQL errors, unknown 0. DMN integration: 13 native (100%),
  0 fallback (0%) of 13, failed/unknown 0.
  Target and installed generator SHA256:
  `C2F441AA68EC8B720F432B82778B534348AF14FC8DB6FDBA39493AEE4C0E0EE0`.
  Log: `native-indexed-join-generator-2026-10-21.log`.
- Official `0006-join#001` passed: 1 native case (100%), 0 fallback (0%) of 1;
  failed/unknown 0, expected model rejections 0. Accounting comparison with the
  last full run confirms one FALLBACK -> NATIVE conversion.
  Accounting: `native-indexed-join-accounting-2026-10-21.json`.
  Log: `native-indexed-join-tck-2026-10-21.log`.

Last full official baseline: 2,695 native (99.26%), 20 fallback (0.74%) of
2,715 executable cases; 676 provisional expected model rejections. Calendar
conversions subsequently gave a targeted estimate of 16 remaining fallbacks.
This join conversion gives a targeted estimate of 2,700 native (99.45%),
15 fallback (0.55%) of 2,715. This is not a new full result. Changes remain
uncommitted.
