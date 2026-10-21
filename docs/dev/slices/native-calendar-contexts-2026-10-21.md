# Native calendar contexts with bounded arithmetic

Calendar contexts can now use direct declared inputs and bounded literal day or
month additions. The proof admits up to 16 independent entries over one input,
using day-of-week or month-of-year calls. Whole-day durations are bounded to
366 days; year/month periods to 12 months. Other shapes retain their prior route.

The existing calendar projection now includes epoch day, local second, zoned
identity, current offset and a bounded window of timezone transitions. These are
value representation components. SQL performs Gregorian month clamping, day
arithmetic, timezone offset selection and gap resolution. Named-zone datetime
values follow the shared runtime's local arithmetic; ZonedDateTime follows
instant arithmetic for durations and local arithmetic for periods. Original
payload columns remain unchanged. SQL raises an error for date/year overflow.

## Verification

- Initial focused fixture lacked context local slots. Two tests ran with one
  runtime fixture error. Completed SQL: 2 native (66.67%), 1 fallback (33.33%)
  of 3; failed/unknown SQL routes 0. Corrected fixture allocation.
- Corrected focused run passed both tests: 70 native actions (98.59%), 1 fallback
  (1.41%) of 71; failed/unknown 0. New calendar context: 68 native actions,
  0 fallback (100%/0%) of 68. The existing projection/payload test intentionally
  executes one fallback. Cases cover leap years, year zero, near-boundary dates,
  fractional seconds, offsets, fixed zones, DST, named-zone identity, and the
  Kiritimati skipped-day transition. Both generated query forms match shared
  runtime results; original input payload bytes remain identical.
- Full generator regression/install passed 130/130 tests: 2,618 native actions
  (96.75%), 88 fallback (3.25%) of 2,706; failed/unknown 0.
  Hybrid integration: 2,605 native (96.73%), 88 fallback (3.27%) of 2,693.
  DMN integration: 13 native (100%), 0 fallback (0%) of 13.
  Target/installed JAR SHA256:
  `9717E98E4B0C2F126B9C7A5A3703F7E771FF2C352A519A2F58FE560E99662246`.
  Log: `native-calendar-context-generator-2026-10-21.log`.
- Official calendar suites passed 24/24: 24 native cases (100%), 0 fallback
  (0%) of 24; failed/unknown 0, expected model rejections 0. Four conversions:
  `0096` and `0097`, each `date_001` and `datetime_001`. No route reversals or
  rejection changes against the latest full baseline within these suites.
  Accounting: `native-calendar-context-accounting-2026-10-21.json`.
  Log: `native-calendar-context-tck-2026-10-21.log`.

Last verified full official baseline: 2,695 native (99.26%), 20 fallback (0.74%)
of 2,715 executable cases; 676 expected model rejections. Targeted update: 2,699 native (99.41%), 16 fallback (0.59%) of 2,715.
This is not a new full result. Changes remain uncommitted.
