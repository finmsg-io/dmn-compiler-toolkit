# Native bounded loop lowering

The existing static lexical substitution pass now expands small closed loops
into typed list expressions. It handles signed/computed integer range bounds,
empty lists, nested results, and later iteration sources bound by earlier ones.
Closed literal decision lists can supply an iteration source after preserving
their declared-result coercion. Bodies remain Runtime IR and execute as SQL;
this pass does not invoke the runtime interpreter.

Expansion is capped at 256 emitted loop results, four iteration bindings and the
existing 4,096-node / 48-depth substitution budget. Literal-source checks also
have a 512-node / 16-depth bound. Integer arithmetic intermediates must fit
signed 32-bit bounds, and literal text is capped at 2,048 characters. Dynamic
sources, partial accumulators, null elements, fractional arithmetic and
incompatible result layouts retain the original graph and fallback handling.

The emitter also recognizes literal range values used directly as list
iteration sources. The shared runtime rejects those sources and returns null;
native SQL now preserves that validation without materializing temporal range
operands or coercing their precision/offsets.

## Verification

Focused parity regression: 13 native SQL actions (100%), zero fallback, zero
failed or unknown routes, denominator 13; Maven exit 0. Cases include dependent
bindings, nested results, signed and computed bounds, empty iteration, and invalid
numeric/string/temporal range sources. Evidence:
`dmn-generator-sparksql/target/native-bounded-loop-focused.log`.

Subsequent review tightened guards for null body results, unsupported comparison
kinds and oversized text. The full generator regression includes those guards;
91/91 tests passed, with 958 native SQL actions (96.96%), 30 fallback (3.04%),
denominator 988, zero failed or unknown routes. Formatting and install passed;
Maven exit 0. Evidence:
`dmn-generator-sparksql/target/native-bounded-loop-full-generator.log`.
Target and installed generator JAR SHA-256 match:
`1AF252F8416C63E2B188D81048B4C1D85F06B7002DB5E8184319173D197B59AB`.
Focused official suites (loops, coercion, lambda, boxed loops and external Java):
102/102 passed. Of 92 executable cases, 58 native (63.04%), 34 fallback (36.96%),
zero failed/unknown; ten provisional expected model rejections separately.
Twelve fallback-to-native conversions, zero reverse conversions, were verified
against the saved full baseline: `0084 decision_003/009/010/012/014/015/019_a/
020_a/021_a/022_a/025`, and `1152 #002`. Evidence in runner target:
`native-bounded-loop-focused-tck.log` and
`native-bounded-loop-focused-accounting-2026-10-15.json`.

Usage remaining reached 18%, so the full Spark SQL TCK was started under the
user's below-20% checkpoint rule. It passed 3,391/3,391 (100%): 2,596 native
(95.62%) and 119 fallback (4.38%) of 2,715 executable cases; zero failed/unknown
or compilation/execution errors. Separately: 676 provisional expected model
rejections. The saved full comparison verifies all twelve conversions, with
zero reverse conversions. `0035 #001` passed with the unchanged timeout.
The full Maven run exited successfully (exit 0).

New saved full baseline in `dmn-tck-runner/target`:
`native-bounded-loop-full-accounting-2026-10-15.json` and
`native-bounded-loop-full-tck-2026-10-15.log`. Use the saved full JSON, since
subsequent focused runs overwrite the generic accounting file. Earlier baseline:
`native-list-builtin-full-accounting-2026-10-15.json`:
3,391/3,391 passed; native 2,584 (95.17%), fallback 131 (4.83%) of 2,715 executable
cases, with 676 provisional expected model rejections separately. Do not derive
new global rates from the focused regression's SQL-action counts.

The 100% native goal remains outstanding: 119 measured fallbacks and the
separate rejection semantic audit remain open. No additional feature changes
were made after this below-20%-usage checkpoint.
