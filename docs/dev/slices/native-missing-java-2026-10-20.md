# Native null results for absent bootstrap Java classes

The literal Java-call proof now accepts a bounded descriptor for a simple class
name directly in java.lang only when the bootstrap loader proves it absent.
The lookup disables class initialization. Existing classes outside the explicit
pure-method whitelist remain unsupported, as do arbitrary application packages,
subpackages and nested class names. Existing literal argument, signature and
parameter bounds still apply.

After the absence proof, the original call is evaluated once through shared
runtime rules. This preserves binding, arity, coercion and null behavior for
external and boxed Java descriptors. Query execution emits a native null literal;
it does not attempt reflection or call a Java fallback UDF. This targets the
official missing-class error/null case, not an additional successful Java method.

## Verification

- Focused regression passed: 24 native actions (100%), zero fallback (0%),
  denominator 24; failed/unknown zero. One JUnit test passed. Cases cover two
  absent java.lang names, external/boxed definitions, numeric/wrong-type/missing
  arguments and both SQL query forms, compared with shared runtime behavior.
  Existing System/Runtime classes and application/other-package names remain
  outside the proof. Generator target log: `native-missing-java-focused-2026-10-20.log`.
- Full generator regression/install passed: 124/124 JUnit tests; 2,103 native
  actions (95.85%), 91 fallback (4.15%) of 2,194; failed/unknown zero.
  Hybrid integration: 2,093 native (95.97%), 88 fallback (4.03%) of 2,181.
  DMN integration: 10 native (76.92%), 3 fallback (23.08%) of 13.
  Formatting/install/Maven exit zero. Generator target log:
  `native-missing-java-generator-2026-10-20.log`. Target/installed JAR SHA256:
  `DDC93867BAE03C4361C215C5E25D4F6D2DEE506B4A19BCB92473B5112A162C8C`.
- Official external-Java TCK suite passed: 18/18 cases; 18 native (100%), zero
  fallback (0%) of 18; failed/unknown zero, expected model rejections zero.
  Exactly one conversion against the last full baseline: `0076-feel-external-java#incorrect_002`.
  No reversals or rejection changes within the suite. Maven exit zero.
  Runner target evidence: `native-missing-java-accounting-2026-10-20.json` and
  `native-missing-java-tck-2026-10-20.log`.
- Full official Spark SQL TCK passed: 3,391/3,391; 2,689 native (99.04%),
  26 fallback (0.96%) of 2,715 executable cases; failed/unknown routes zero.
  Compilation/execution errors, unsupported, excluded and invalid counts zero.
  Separately: 676 provisional expected model rejections unchanged. Exact comparison
  with the preceding full checkpoint confirms four conversions: missing-class
  `0076#incorrect_002`, service `0088#001`, and lambdas `0092#017/#018`.
  Zero reversals or rejection changes. Fallback decreased 30 to 26 (13.33%).
  Runner target evidence: `native-missing-java-full-accounting-2026-10-20.json`
  and `native-missing-java-full-tck-2026-10-20.log`. This full checkpoint verifies
  the accumulated targeted service/sort improvements. Full Maven exit zero.
  Documentation facts, strict MkDocs build and whitespace checks passed.

This batch and preceding service/sort changes remain uncommitted. The 100%
native goal stays open. Restart the full-run cadence at this checkpoint.
