# Native nested contexts and statistical validation

The bounded literal-context proof now accepts nested context builtins, including
`context put`, and evaluates the original invocation through shared FEEL binding
and validation. Verified results emit native SQL literals, with no interpreter
or UDF during query execution. This covers nested updates, invalid intermediate
paths, and `get entries` over an updated context.

The static lexical substitution pass also lowers transparent boxed returns,
substitutes bound context entries, inlines closed literal context decisions,
and infers native field layouts after substitution. Existing external Java
descriptor, declared-result coercion and expansion guards remain in effect.

The context proof retains its 2,048-node / 32-depth budget and exact decimal and
homogeneous array-layout requirements; literal text is bounded at 2,048
characters. Dynamic references and unsupported nested calls receive no proof.
Original invocation binding preserves duplicate names and rejects mixed
positional/named argument styles according to the shared runtime.

The literal-builtin proof also covers `median`, `mode`, and `stddev`, including
null/heterogeneous literal list validation and exact numeric mode lists. Numeric
arrays require a common decimal representation: maximum integer width plus
maximum fractional width must fit 38 digits, including numeric fields inside
arrays of contexts and nested arrays. Unrepresentable closed context results
and numeric arrays explicitly retain fallback.

## Verification

- Focused nested-context parity: 11 native actions (100%), zero fallback, zero
  failed/unknown routes, denominator 11; Maven exit 0. Evidence: generator target
  `native-nested-context-focused.log`.
- Full context regression including duplicate/mixed argument binding: 93/93
  tests passed; 971 native actions (97.00%), 30 fallback (3.00%), denominator
  1,001; zero failed/unknown routes, formatting/install passed, Maven exit 0.
  Evidence: generator target `native-nested-context-full-generator.log`.

Combined generator: 94/94 tests passed; 986 native actions (97.05%), 30 fallback
(2.95%), denominator 1,016, zero failed/unknown routes. Formatting/install passed;
Maven exit 0. Evidence: generator target
`native-context-statistics-full-generator.log`. Target and installed generator
JAR SHA-256 match:
`893EE219D6B6F98781A806BEF9476AA80EE3548855545CA60630BDC431368447`.
The first official check passed 164/164: 120 native (77.92%), 34 fallback (22.08%)
of 154 executable cases; ten provisional expected model rejections separately.
Zero failures/unknown routes. Six conversions were verified at that stage:
five statistical cases and context-put case 002. The other contexts referenced
closed decisions or returned named boxed fields; anonymous-return lowering alone
did not cover those forms. Evidence: runner target
`native-context-statistics-focused-tck.log` and
`native-context-statistics-focused-accounting-2026-10-16.json`.

The closed-dependency regression then passed four native actions (100%), zero
fallback/failed/unknown; denominator four, Maven exit 0. Evidence: generator
target `native-closed-context-focused.log`.

Expanded generator regression before the final decimal-array guard: 96/96 tests
passed, 994 native actions (97.07%), 30 fallback (2.93%) of 1,024; zero failed/
unknown, formatting/install passed, Maven exit 0. Evidence: generator target
`native-context-statistics-final-generator.log`.

The next guarded full generator run passed 97/97 tests: 994 native actions
(96.79%), 33 fallback (3.21%) of 1,027; zero failed/unknown. The three additional
fallback actions deliberately verify exact values when a large integer and a
tiny fraction cannot share one Spark decimal array type. Evidence: generator
target `native-context-statistics-guarded-generator.log`.

A final field-layout refinement recognizes actual primitive constant kinds when
their IR annotation is `ANY`; the dependency regression explicitly covers that
metadata. Final generator and repeated official verification passed as recorded below.
Do not infer official conversions or global rates from regression action counts.
The previous full baseline was runner target
`native-bounded-loop-full-accounting-2026-10-15.json`: 3,391/3,391 passed, 2,596
native (95.62%), 119 fallback (4.38%) of 2,715 executable cases, with 676
provisional expected model rejections separately. The 100% native goal and
semantic rejection audit remain outstanding.

Final generator: 97/97 tests passed; 994 native actions (96.79%), 33 fallback
(3.21%) of 1,027; zero failed/unknown routes. Three fallback actions are explicit
combined-decimal precision boundaries. Formatting/install passed, Maven exit 0.
Log: generator target/native-context-statistics-verified-generator.log.
Target and installed JAR SHA-256 match:
8EECD83976F1297D6867F9B20A87B5404948EADEC6F1A6FED73E14A24782C353.

Repeated official suites: 164/164 passed; 125 native (81.17%), 29 fallback
(18.83%) of 154 executable cases, ten provisional expected model rejections
separately; zero failed/unknown. Saved evidence: runner target/
native-context-statistics-verified-accounting-2026-10-16.json and
native-context-statistics-verified-tck.log. Usage remaining reached 5%, triggering
the completed full TCK checkpoint below under the user's below-20% rule.


## Completed full checkpoint

All 3,391/3,391 cases passed. Of 2,715 executable cases, 2,608 were native
(96.06%) and 107 fallback (3.94%). There were zero failures, unknown routes,
compilation errors or execution errors. The 676 provisional expected model
rejections remain separate; their semantic audit is still open. Maven exited 0.

Evidence in `dmn-tck-runner/target`:
`native-context-statistics-full-tck-2026-10-16.log` and
`native-context-statistics-full-accounting-2026-10-16.json`.
This saved full accounting file is the new comparison baseline.

Actual per-case comparison against the previous bounded-loop full baseline:

- 0061 median: 005, 007 moved from fallback to native.
- 0062 mode: 005, 007 moved from fallback to native.
- 0063 stddev: 005, 007 moved from fallback to native.
- 0085 decision services: 015 moved from fallback to native.
- 1146 context put: 002, 014, 015, nested010, nested011, nested012 moved
  from fallback to native.
- 1146 context put: 004 moved from native to fallback and still passed.
  Investigate this reverse route next; its cause has not yet been isolated.

There are thirteen forward conversions and one reverse conversion: net twelve
fewer fallbacks, from 119 (4.38%) to 107 (3.94%). Regression SQL-action rates
above are separate measurements and do not substitute for these official rates.
The 100% native goal remains open. No further source changes followed this full
checkpoint. Resume with the reverse route and remaining fallback families;
repeat full TCK after 45 minutes of work, usage remaining below 20%, or twenty
further conversions, whichever comes first.
