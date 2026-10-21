# Native service concatenation through typed string decisions

Continues from commit 6974cb5 and the full 30-fallback checkpoint.
The string-only projection proof now recognizes references to decisions declared
STRING, provided the reference type matches the producer declaration. The existing
capability analysis still inspects the complete producer dependency graph. This
allows a lowered service invocation to concatenate a native string decision with
an opaque ANY parameter using the existing string-only input projection.

The projection preserves nulls and rejects non-string ANY values as required by
shared addition semantics. Original opaque input bytes remain unchanged. No
function is evaluated in the adapter, and no new runtime fallback is introduced.
Numeric/context decision references and mismatched reference types retain their
existing route.

## Verification

- Focused integration passed: 54 native actions (100%), zero fallback (0%),
  denominator 54; failed/unknown zero. One JUnit test passed. Coverage includes
  dynamic string decisions, direct service invocation, null/wrong-type producer
  inputs, strings with quotes/backslashes, empty strings, null/numeric/Boolean/
  singleton-list/context/date parameters, payload preservation, and both query
  forms. Expected results come from the shared runtime. Generator target log:
  `native-service-string-focused-2026-10-20.log`.
- Full generator regression/install passed: 122/122 JUnit tests; 2,015 native
  actions (95.68%), 91 fallback (4.32%), denominator 2,106; failed/unknown zero.
  Hybrid integration: 2,005 native (95.80%), 88 fallback (4.20%) of 2,093.
  DMN integration: 10 native (76.92%), 3 fallback (23.08%) of 13.
  Formatting/install/Maven exit zero. Generator target log:
  `native-service-string-generator-2026-10-20.log`. Target/installed JAR SHA256:
  `ED19877F67E34ED246F7255C779A70FAF10C0EC2A388AEF98B73036519F34AC7`.
- Official no-decision-logic service TCK passed: one native case (100%), zero
  fallback (0%), denominator one; failed/unknown zero, expected model rejections
  zero. Exactly one conversion against the full baseline:
  `0088-no-decision-logic#001`. No reversals or rejection changes within the suite.
  Maven exit zero. Runner target evidence:
  `native-service-string-accounting-2026-10-20.json` and
  `native-service-string-tck-2026-10-20.log`.
- Targeted estimate: 2,686 native (98.93%), 29 fallback (1.07%) of 2,715
  executable baseline cases. This is not a new full result. Last full remains
  2,685 native (98.90%), 30 fallback (1.10%), plus 676 provisional expected
  model rejections. One verified conversion since the last full checkpoint.

Changes in this batch remain uncommitted. The 100% native goal stays open.
