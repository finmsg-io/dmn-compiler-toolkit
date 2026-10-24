# Exact numeric results from scalar BKM contexts

## Scope and native boundary

The scalar BKM/context plan now admits declared NUMBER terminal results as well
as BOOLEAN results. Numeric results use the existing versioned exact decimal
payload rather than Spark DOUBLE. Arithmetic, bindings, eager context entries,
lazy conditionals and expansion limits reuse the scalar plan. A null result is
selected using the declared output type, so it remains a null numeric payload.

This is reusable groundwork for structured loan metrics. It does not yet lower
list-of-context results or the three sorts in official 0014-loan-comparison.
No reduction in official fallback cases is claimed without a full TCK run.

A proven numeric alias can be expanded back to its source expression and remain
native. An ordinary downstream SQL consumer must not read the binary numeric
column as a primitive number. Capability analysis explicitly retains fallback
unless the complete consumer has its own proof; BOOLEAN scalar dependencies
remain safe native primitives.

## Variations and verification

The new integration cases vary field names, positional versus reversed named
bindings, large and high-precision signed numbers, null inputs and null/false
conditions. Invalid arithmetic in inactive branches remains inactive; invalid
unused context entries still raise errors. A downstream abs-based boolean
predicate exercises the unsupported consumer route through fallback.

Initial focused run: two tests, one failed boundary assertion. SQL accounting:
22 native (100%), 0 fallback (0%) of 22; unknown 0, two expected native SQL
error assertions. The incorrect expectation assumed a simple numeric alias could
not be proven; the scalar parser safely expands it instead.
Log: native-numeric-scalar-focused-2026-10-23.log.

Second focused run: two tests, one failed value assertion. SQL accounting:
22 native (95.65%), 1 fallback (4.35%) of 23; unknown 0, two expected native SQL
error assertions. Routing was correct, but a declared NUMBER fallback result
crosses the existing DOUBLE boundary. The test expected an exact value and
observed 1.125899906842624E+16 instead of
11258999068426241.2500000000000000000000000000000000000001.
Log: native-numeric-scalar-corrected-focused-2026-10-23.log.

This exposes a pre-existing fallback transport limitation, not a native scalar
arithmetic error. SparkSqlFeelValueCodec.toSpark converts declared NUMBER to
double; scalar NUMBER inputs also use that ordinary boundary. A separate change
must provide lossless numeric input/output transport consistently with consumer
routing and generated schemas. Do not describe all fallback numeric results as
lossless until that is implemented and validated. The corrected boundary case
uses a boolean result and an exactly representable input, while the native numeric
cases retain exact high-precision result assertions.

Full generator regression/install passed 151/151 tests, no failures/errors/skips.
Combined SQL accounting: 2,938 native (96.80%), 97 fallback (3.20%) of 3,035;
unknown 0, 58 expected native SQL error assertions. Hybrid integration alone:
2,925 native (96.79%), 97 fallback (3.21%) of 3,022; unknown 0, 58 expected
errors. DMN integration: 13 native (100%), 0 fallback (0%) of 13; failed/unknown 0.
The additional fallback action validates the unsupported consumer boundary.
Log: native-numeric-scalar-full-generator-2026-10-23.log.
Installed generator SHA256:
F361EF61D06275B0057A191D1F4F4E2643A0D760994D6346DFDD938D5A4BDC6D.

2026-10-23 exact numeric scalar-context extension verified: generator 151/151
passed, with 2,938 native SQL actions (96.80%), 97 fallback (3.20%) of 3,035;
unknown 0, 58 expected native SQL error assertions. The added fallback action
checks the unsupported consumer boundary. Full official TCK: 3,391/3,391 passed;
2,713 native (99.93%), 2 fallback (0.07%) of 2,715 executable cases, plus 676
expected model rejections. Failed/errors/unknown 0. All outcomes and routes are
identical to the previous full checkpoint: no conversions or reversals this batch.
This adds reusable exact NUMBER terminal results to scalar BKM/context plans,
with renamed-field, binding, precision, null, lazy-branch and eager-error tests.
Unproven numeric consumers retain fallback; proven aliases can expand natively.
It does not yet lower the structured loan loop results and sorting operations.
Important separate limitation: the ordinary declared NUMBER hybrid input/output
bridge uses DOUBLE, including numeric fallback output; arbitrary BigDecimal
precision is not preserved there. A reproducer and explicit follow-up are recorded
in docs/dev/slices/native-numeric-scalar-contexts-2026-10-23.md. Do not conflate
exact native scalar results with precision guarantees for every fallback route.
Remaining official fallbacks: 0007-date-time#001 and 0014-loan-comparison#001.
Next: lossless numeric hybrid transport and structured loan result/sort support.
Accounting: dmn-tck-runner/target/native-numeric-scalar-full-accounting-2026-10-23.json.
TCK log: dmn-tck-runner/target/native-numeric-scalar-full-tck-2026-10-23.log.
Generator log: dmn-generator-sparksql/target/native-numeric-scalar-full-generator-2026-10-23.log.
Installed generator SHA256:
F361EF61D06275B0057A191D1F4F4E2643A0D760994D6346DFDD938D5A4BDC6D.
Changes are uncommitted after bb0e090.

Maven exited 0. Surefire logged its fork shutdown timeout after all cases
completed successfully. Documentation facts, diff whitespace checks and the
strict MkDocs build passed.
