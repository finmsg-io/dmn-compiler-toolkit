# Literal type and extreme-scale rounding proofs — 2026-10-14

This batch starts from the verified 2,538 native / 177 fallback checkpoint out
of 2,715 executable official cases (93.48% / 6.52%). Official coverage gains
remain unmeasured until the next full TCK.

## Structural type checks

Bounded literal constructors support native boolean proofs without transporting
closures or heterogeneous lists through Spark. Function bodies are never
executed for these checks. Typed callable-signature validation and external
functions retain their existing routes. Literal lists and contexts preserve
the shared runtime's null-member and required-field behavior. Invalid dates,
dynamic references and excessive constructor size/depth do not receive a proof.
Large numeric literals can be tested as numbers without representing their
values in Spark decimals.

The corrected focused type regression passed 19 native SQL actions (100%), zero
fallback, denominator 19, zero failed/unknown routes. The initial fixture used
unsupported standalone context<> syntax and stopped after ten successful native
actions. A supported context field shape replaced that fixture.

## Rounding

Literal rounding proofs cover an unchanged exact value when scale already
retains all digits, and null for integer scales outside the shared runtime's
[-6111,6176] bounds. Bounded addition, subtraction and multiplication can prove
scale expressions. The generated value uses an exact decimal cast with verified
precision/scale; it does not call floating-point power for extreme scales.

Dynamic operands, overflowing/fractional scale proofs and coarse scales without
an exact native proof retain existing validation. Very negative scales that
would exceed native arithmetic bounds explicitly retain lossless fallback,
including unary and arithmetic scale expressions.

The first combined run exposed floor(5.5,-6111) reaching division by zero:
24 native successful actions (96%), zero fallback, one failed/unknown action
(4%), denominator 25. A generalized coarse-scale guard addresses that gap.
The corrected test deliberately executes these coarse cases through fallback
and compares their exact results, including very large decimals, to the shared
runtime. The corrected combined batch passed both focused tests: 59 native
actions (88.06%), eight deliberate fallback actions (11.94%), denominator 67,
zero failed or unknown routes. Full generator verification passed 88/88 tests,
formatting and installation: 843 native actions (96.56%), 30 fallback (3.44%),
denominator 873, zero failed or unknown routes. One earlier full run stopped at
an old routing assertion that explicitly required fallback for the three newly
proven scales; it was updated to require native routing and retain its result
checks. Final proof scope is limited to the eight shared-runtime rounding
built-ins; emitter-only aliases do not receive these new proofs.
The final alias guard passed 40 native actions (83.33%), eight deliberate
fallback (16.67%), denominator 48, zero failed/unknown routes. Installed and
target generator JARs share SHA-256
0FA5CA811A2B40281FC6C58D3E87087FF2B4F19F5DF8C0477DAC45D06AEAFC9A.
The first full optimized Spark SQL TCK passed 3,391/3,391: 2,563 native (94.40%),
152 fallback (5.60%), denominator 2,715 executable cases; 676 provisional expected
model rejections separately, zero failures or compilation/execution errors.
Compared with the prior function checkpoint, 25 cases converted from fallback
to native: 13 in 0070 instance-of and three each in 1141, 1142, 1143 and 1144
rounding suites. Zero reverse conversions. Evidence: runner
target/native-type-rounding-full-tck.log and native-type-rounding-first-full-accounting.json.
The requested 2026-10-15 repeat passed 3,390/3,391: native passes 2,562 (94.36%),
fallback passes 152 (5.60%), one timeout (0.04%), denominator 2,715 attempted
executable cases; 676 provisional model rejections separately. Case 0035 #001
exceeded the unchanged 30-second limit. No result mismatches or compilation/
execution errors. Maven exit 1. The identical JAR passed the earlier full run.
There remain 25 actual fallback-to-native conversions and zero reverse
conversions; the native-pass increase in this repeat is 24 because of the timeout.
The focused same-limit 0035 suite reproduced the timeout: two native passes
(66.67%), zero fallback, one timeout (33.33%), denominator three; Maven exit 1.
This does not supersede the full result. No timeout or generator source changed
for the rerun, and warmup remains unverified. Retry evidence: runner
target/native-type-rounding-timeout-retry-accounting-2026-10-15.json.
Full evidence: runner target/native-type-rounding-full-accounting-2026-10-15.json
and native-type-rounding-full-tck-2026-10-15.log.

## Remaining work

Empty-source loops, bounded arithmetic range endpoints, dynamic list/context
operations and the separate model-rejection semantic audit remain outstanding.
The 100% native goal remains active. Apply the user's full-TCK cadence: 45 minutes
of work, usage remaining below 20%, or 20 converted fallback rules.
