# String result typing after static function binding

The function lowerer now infers STRING for ADD when both recursively lowered
operands are STRING and the original expression declared ANY or STRING. This
preserves result typing after binding untyped function parameters to typed string
inputs. A containing declared-STRING function or decision can then pass its
existing result-coercion check and emit native concatenation.

The change does not infer numeric arithmetic or narrow arbitrary ANY inputs.
Shared input coercion and null propagation remain in place. Named arguments
continue to bind by parameter name before result inference. No fallback UDF or
adapter evaluation is added.

## Verification

- Focused regression passed: 48 native actions (100%), zero fallback (0%),
  denominator 48; failed/unknown zero. One JUnit test passed. Shared-runtime
  comparisons cover boxed typed and literal untyped functions in one context,
  positional and reversed named argument binding, declared STRING results,
  null/wrong-type/singleton-list inputs, quoted strings/backslashes, empty strings
  and both individual and AllDecisions queries. Generator target log:
  `native-string-function-focused-2026-10-20.log`.
- Full generator regression/install passed: 125/125 JUnit tests; 2,154 native
  actions (96.07%), 88 fallback (3.93%) of 2,242; failed/unknown zero.
  Hybrid integration: 2,141 native (96.05%), 88 fallback (3.95%) of 2,229.
  DMN integration: 13 native (100%), zero fallback (0%) of 13; three existing
  integration actions changed from fallback to native. Formatting/install/Maven
  exit zero. Generator target log: `native-string-function-generator-2026-10-20.log`.
  Target/installed JAR SHA256:
  `E5FE5E2CDB7CF7D0B8048D7163F9D8ACCFE2F61C6FCED71D2F1634C072AC45E1`.
- Official user-defined-function TCK passed on 2026-10-21: one native case
  (100%), zero fallback (0%) of one; failed/unknown zero, expected model
  rejections zero. Both positional and named function decisions are native.
  Exactly one conversion versus the last full baseline:
  `0030-user-defined-functions#001`. No reversals/rejection changes in the suite.
  Maven exit zero. Runner target evidence:
  `native-string-function-accounting-2026-10-21.json` and
  `native-string-function-tck-2026-10-21.log`.
- Targeted estimate: 2,690 native (99.08%), 25 fallback (0.92%) of 2,715
  executable baseline cases. This is not a new full result. Last full remains
  2,689 native (99.04%), 26 fallback (0.96%), plus 676 provisional expected
  model rejections separately. One conversion since the latest full run.

Changes remain uncommitted, including preceding service/sort/missing-class work.
The 100% native goal stays open.
