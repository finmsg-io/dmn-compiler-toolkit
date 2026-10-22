# DECIMAL128 division in native Spark SQL

The terminal exact numeric-context proof now admits division. SQL long division
operates on unsigned coefficient strings, emits 35 significant quotient digits,
and uses the remainder to round to 34 digits with half-even rounding. Exact
quotients retain the preferred scale where precision permits. Rounding carry,
signs and decimal payload scale are preserved without a whole-value double or
Spark fixed-decimal conversion.

Null operands short-circuit before numeric conversion. A zero denominator returns
null before converting an invalid numerator, matching the shared runtime.
Nonzero invalid numbers and quotient scale overflow preserve runtime error paths.
BigDecimal also checks its provisional division scale before stripping exact zeros;
the SQL path checks that provisional scale and the nonzero preferred scale.
Zero numerators use the saturated preferred scale.

Input projection only converts representation components. Numeric calculation,
rounding and validation execute in Spark SQL. Existing static context-function
binding exposes the new division route without adding a function-specific rewrite.

## Verification

- Initial normal-case regression passed: 48 native actions (100%), 0 fallback
  (0%) of 48; failed/unknown 0. Both query forms match runtime decimal values
  and scale for repeating quotients, half-even ties, rounding carry, signs,
  large exponents, zero divisors and nulls.
- Expanded boundary regression stopped on a reference-runtime overflow that the
  fixture incorrectly expected to succeed. It had completed 58 native actions
  (100%), 0 fallback (0%) of 58; failed SQL actions/unknown 0. JUnit: two tests,
  one error. The local JDK implementation confirmed the provisional scale check;
  both the fixture classification and SQL overflow handling were corrected.
- Corrected regression passed both tests: 70 native actions (100%), 0 fallback
  (0%) of 70; unknown 0. Ten native SQL errors are intentional passed assertions
  for invalid numbers and scale overflow. It also checks zero saturation and
  invalid-numerator/zero-denominator evaluation order.

Logs under `dmn-generator-sparksql/target/`:
`native-exact-division-focused-2026-10-22.log`,
`native-exact-division-boundaries-2026-10-22.log`,
`native-exact-division-corrected-boundaries-2026-10-22.log`.

Full generator regression/install passed 140/140 tests, no JUnit failures,
errors or skips: 2,826 native SQL actions (96.78%), 94 fallback (3.22%) of
2,920; unknown 0. Twenty-six native SQL errors are intentional passed assertions
(ten new division checks plus sixteen existing). Hybrid integration: 2,813
native (96.77%), 94 fallback (3.23%) of 2,907; failed 26 expected, unknown 0.
DMN integration: 13 native (100%), 0 fallback (0%) of 13; failed/unknown 0.
Log: `native-exact-division-generator-2026-10-22.log`.
Built and installed generator SHA256:
`0EA05DDB465AA2EAE7F206E32819E27183571F803D749B05D77643A214C2D3DD`.

Official user-defined-function suite passed 3/3: 3 native (100%), 0 fallback
(0%) of 3; failed/unknown 0, expected model rejections 0. Exact comparison with
the last full accounting confirms #001 and #002 changed FALLBACK -> NATIVE,
#003 remains native, no route reversals or rejection changes within the suite.
Accounting: `dmn-tck-runner/target/native-exact-division-accounting-2026-10-22.json`.
Log: `dmn-tck-runner/target/native-exact-division-tck-2026-10-22.log`.

Targeted aggregate estimate: 2,705 native (99.63%), 10 fallback (0.37%) of
2,715 executable cases. This is not a new full result; latest full remains
2,703 native (99.56%), 12 fallback (0.44%), plus 676 expected model rejections.
Remaining targeted groups: temporal construction (1), iteration/factor loop (2),
composite lending (7). Changes remain uncommitted after `a9b3449`.

## Subsequent full verification

The combined division/factor-loop full run passed 3,391/3,391: 2,706 native
(99.67%), 9 fallback (0.33%) of 2,715 executable cases, plus 676 expected model
rejections; failed/unknown 0. It confirms the two division conversions and the
factor-loop conversion without reversals or rejection changes. See the
[factor-loop checkpoint](native-factor-loops-2026-10-22.md) for the final build
hash, generator regression counts and remaining work. This full result supersedes
the earlier targeted estimate and accompanies the requested commit/push.
