# Native literal list builtins

`SparkSqlLiteralBuiltinProof` uses shared FEEL evaluation and argument binding
for bounded literal calls to `all`, `any`, `string join`, and `product`. SQL
contains the resulting scalar literal; execution requires no interpreter or UDF.
This handles nullable, heterogeneous and empty literal lists without delegating
their validation to Spark coercion.

The whitelist accepts only the four registered builtin names. Inputs contain
primitive constants and lists, with a total 512-node budget, depth at most 16,
text at most 2,048 characters per constant and bounded numeric precision/scale.
Decimal outputs must fit Spark's exact 38-digit representation, and joined text
is capped at 32,768 characters. Dynamic references, functions, temporal values,
oversized inputs and inexact decimal outputs do not receive this proof.

Original named/positional invocation binding is evaluated through the runtime,
including invalid names, duplicate names and mixed argument styles. Expression
and decision result kinds remain guarded; a scalar proof cannot bypass declared
list or other incompatible result coercion.

## Verification

The parity regression covers all four builtins with positional and named lists,
nulls, mixed primitive kinds, empty lists, invalid arity/names, duplicate bindings
and mixed argument styles. String joining includes separators and prefix/suffix.
Planning guards cover dynamic inputs, resource limits, decimal overflow and
incompatible declared lists.

The first attempt failed at test compilation due to a generic fixture type;
zero SQL actions, native/fallback rates unavailable. The corrected focused
regression passed with 78 native SQL actions (100%), zero fallback, zero failed
or unknown routes, denominator 78; Maven exit 0. Evidence:
`dmn-generator-sparksql/target/native-list-builtin-focused-corrected.log`.
Full generator: 90/90 tests passed, 945 native actions (96.92%), 30 fallback
(3.08%), denominator 975, zero failed or unknown routes. Formatting and install
passed; Maven exit 0. Evidence:
`dmn-generator-sparksql/target/native-list-builtin-full-generator.log`.
Target and installed generator JAR SHA-256 match:
`50ED93B99E330CBFED59AB21757AD07C527B3474E60D2DFB38DFC8F22FA9F5FB`.
Focused official verification: 71/71 passed; 71 native (100%), zero fallback,
failed or unknown routes, denominator 71, zero model rejections. Against the
saved full checkpoint, this verifies 14 fallback-to-native conversions: four
`0059 all`, three `0060 any`, three `0094 product`, and four `1140 string join`.
There were zero reverse conversions. Evidence: runner target
`native-list-builtin-focused-tck.log` and
`native-list-builtin-focused-accounting-2026-10-15.json`.

Together with the seven earlier `get entries` conversions, this reaches 21
verified conversions since the last full checkpoint. The full TCK checkpoint
under the user's 20-conversion rule passed 3,391/3,391 (100%). Of 2,715 executable
cases, 2,584 passed natively (95.17%) and 131 via fallback (4.83%), with zero
failed/unknown routes or compilation/execution errors. Separately, 676 provisional
expected model rejections remain subject to their semantic audit.
The full Maven run exited successfully (exit 0).

The full accounting comparison against
`native-type-rounding-full-accounting-2026-10-15.json` verifies all 21 conversions
and zero reverse conversions. The native pass count increased by 22 because
`0035-test-structure-output #001` also passed after previously timing out. Its
30-second limit was unchanged; this recovery does not prove a warmup fix.

Saved full evidence in `dmn-tck-runner/target`:
`native-list-builtin-full-tck-2026-10-15.log` and
`native-list-builtin-full-accounting-2026-10-15.json`. Use this saved full snapshot
as the next baseline; subsequent focused runs can overwrite the generic JSON.

The 100% native goal remains outstanding, with 131 measured fallback cases and
the separate provisional rejection audit still open. Next full-run cadence
starts at this completed checkpoint: 45 minutes of work, usage remaining below
20%, or 20 further conversions, whichever occurs first.
