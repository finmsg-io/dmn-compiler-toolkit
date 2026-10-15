# Native literal `get entries`

The generator now uses the existing bounded context-literal proof for the
registered `get entries` builtin. It applies the shared FEEL runtime contract at
generation time and emits the verified result as native SQL literals, with no
runtime interpreter or UDF. The proof has the existing 2,048-node and 32-level
limits, verifies native numeric precision and array element layouts, and keeps
unrepresentable results on fallback.

Named invocation accepts `m` and preserves the shared runtime's last binding for
repeated argument names. Unknown names and mixed positional/named invocations
produce null. Result validation permits compatible declared lists in addition to
`ANY`, without removing declared-type coercion guards.

The parity regression checks primitive and nested contexts, empty contexts, null,
non-context arguments, invalid arity, named arguments and duplicate bindings.
Heterogeneous entry values remain guarded when Spark cannot share their element
layout.

## Verification

- First Maven attempt: cache access denied before execution; zero SQL actions,
  native/fallback percentages unavailable.
- First focused regression: 11 native actions (100%), zero fallback, zero failed
  or unknown SQL routes; one parity assertion failed for duplicate named `m`.
  Corrected the binding to preserve the last value, matching the shared runtime.
- Corrected focused regression: passed; 22 native actions (100%), zero fallback,
  zero failed/unknown, denominator 22. Log:
  `dmn-generator-sparksql/target/native-get-entries-focused-corrected.log`.

- Final full generator regression, including mixed-argument validation: 89/89
  JUnit tests passed; 867 native actions (96.66%), 30 fallback (3.34%), denominator
  897, zero failed/unknown routes. Formatting and install passed. Log:
  `dmn-generator-sparksql/target/native-get-entries-full-generator.log`.
- Official optimized generated Spark SQL focused suite: 9/9 passed, 9 native
  (100%), zero fallback, denominator nine, zero failures, unknown routes or
  model rejections. Compared case by case with the saved full checkpoint,
  `decision_003` through `decision_009` converted from fallback to native;
  `decision_001` and `decision_002` remained native. No reverse conversions.
  Evidence: runner target `native-get-entries-focused-tck.log` and
  `native-get-entries-focused-accounting-2026-10-15.json`.
- Target and installed generator JARs have identical SHA-256:
  `B35E8CE866008B8F45D73F3BD99AF1CD426491C5CE0028ED90CEA4B7D0010226`.

The focused result was subsequently verified in the combined full checkpoint:
see [native literal list builtins](native-literal-list-builtins-2026-10-15.md).
Use `native-list-builtin-full-accounting-2026-10-15.json` as the latest saved full
baseline. Do not report this focused suite as a full-suite rate.

The earlier full TCK checkpoint was 3,390/3,391 passed: 2,562 native (94.36%), 152 fallback
(5.60%) and one timeout (0.04%) of 2,715 attempted executable cases. Separately,
676 provisional expected model rejections require their ongoing semantic audit.
`0035 #001` passed in the later combined full run without changing the timeout;
no warmup fix has been proven. The 100% native goal remains outstanding.
