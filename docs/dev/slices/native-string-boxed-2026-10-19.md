# Native string projections and boxed Java descriptors

Continuation from the partial-loop checkpoint. Latest full global
baseline: 3,391/3,391 passed; 2,652 native (97.68%), 63 fallback (2.32%) of
2,715 executable cases. Separately: 676 provisional expected model rejections.
The 100% native goal and semantic rejection audit remain open.

## Changes

Trusted pure literal Java folding now recognizes boxed Java descriptors on
non-external function definitions. It evaluates the original definition, retaining
its distinct arity, named-binding and argument-coercion rules. The existing fixed
class/method whitelist, descriptor/argument limits, exact numeric result proof and
UTF-16 output guard remain in force. Dynamic calls retain their established route.

Proven string concatenations over ANY inputs use added string-only boundary
projections. Original opaque payload columns remain intact for fallback consumers.
A projected value is the original String or null for every other type; no string
coercion of numbers, lists, dates or booleans occurs. The proof only accepts bounded
ADD trees over constants and direct STRING/ANY inputs, with a proven string side
at every addition; ambiguous ANY + ANY is rejected. Limits: 128 syntax nodes and
depth 32. Projection columns avoid case-insensitive user column collisions and
survive both individual and AllDecisions CTE pipelines. Direct decision-service
arguments remain supplied values; default input-decision expressions are not inlined.

## Verification history

- Boxed Java focused: one test passed; five native actions (100%), zero fallback
  (0%) of five, zero failed/unknown. Generator target
  `native-boxed-java-focused-2026-10-19.log`.
- First filtered official attempt executed zero cases: runner inherited
  tck.requireFull=true and rejected filtering. Native zero, fallback zero,
  denominator zero: rates undefined because no cases executed. Maven exited one
  with runner setup/accounting errors; no SQL route failure or unknown action.
  Runner target `native-boxed-java-services-official-2026-10-19.log`.
- Combined string/boxed focused: two tests passed; 13 native actions (61.90%),
  eight deliberate fallback identity actions (38.10%) of 21; zero failed/unknown.
  Checked correct strings, empty/null values, wrong types, quote/backslash data,
  column-name collision, ambiguous addition rejection, unchanged payload bytes
  and mixed native/fallback AllDecisions parity. Generator target
  `native-string-boxed-focused-2026-10-19.log`.

- First full generator regression: 106/107 passed; the sole failure was an
  obsolete assertion expecting a trusted implicit Java descriptor to stay on
  fallback. Executed actions: 1,215 native (95.82%), 53 fallback (4.18%) of
  1,268; zero failed/unknown SQL routes. Generator target
  `native-string-boxed-full-generator-2026-10-19.log`.
- Corrected full generator regression: 107/107 passed; 1,216 native actions
  (95.82%), 53 fallback (4.18%) of 1,269; zero failed/unknown. Formatting/install
  passed. Updated the trusted descriptor assertion and added a guard retaining
  implicit Math.random() fallback. Projection values are restricted to String.
  Generator target `native-string-boxed-final-generator-2026-10-19.log`.

Target and installed JAR SHA-256:
`14EDC6A938E474994E1B23CF8B42CCE3A7FDAA63540502771A38B1A2C675925C`.
Full official verification completed; latest checkpoint follows below.

## Remaining-group diagnosis

Chapter 11 has an input context with untyped Monthly field, plus heterogeneous or
untyped list expressions. Direct decision services 002/003 have untyped parameters
used only for string concatenation; their capabilities previously rejected those
opaque inputs. Diagnostic program/log are generated target artifacts only.

## Full official checkpoint

2026-10-19 LATEST FULL string/boxed-Java checkpoint: 3,391/3,391 passed.
Of 2,715 executable cases: 2,658 native (97.90%), 57 fallback (2.10%);
zero failed/unknown routes, compilation/execution errors, unsupported, excluded
or invalid cases. Separately: 676 provisional expected model rejections.
Six verified fallback-to-native conversions, zero reverse changes against the
partial-loop full checkpoint: 0076 boxed_001 and 0085 cases 002, 002_a, 002_b,
002_c and 003. Fallback decreased from 63 to 57 (9.52% fewer fallback cases).
Direct decision-service suite now has zero fallback cases. Original ANY payloads
remain intact; only proven string concatenations consume string-only projections.
Generator: 107/107 passed; 1,216 native actions (95.82%), 53 fallback (4.18%)
of 1,269; zero failed/unknown. Formatting/install passed. Target/installed JAR:
14EDC6A938E474994E1B23CF8B42CCE3A7FDAA63540502771A38B1A2C675925C.
Saved runner target evidence: native-string-boxed-full-accounting-2026-10-19.json
and native-string-boxed-full-tck-2026-10-19.log. Case 0035 passed unchanged timeout.
The 100% native goal and semantic rejection audit remain open. Chapter 11 still
has eight fallbacks, including an opaque input context with untyped Monthly field
and heterogeneous/untyped list expressions. Next work should preserve temporal
precision/offset/duration identity, shared list typing and lexical binding.
Restart full-run cadence here: 45 minutes work, usage remaining below 20%, or
twenty further verified conversions, whichever comes first.

### Verified route changes

- `compliance-level-3/0076-feel-external-java/0076-feel-external-java-test-01.xml#boxed_001`: fallback to native.
- `compliance-level-3/0085-decision-services/0085-decision-services-test-01.xml#002`: fallback to native.
- `compliance-level-3/0085-decision-services/0085-decision-services-test-01.xml#002_a`: fallback to native.
- `compliance-level-3/0085-decision-services/0085-decision-services-test-01.xml#002_b`: fallback to native.
- `compliance-level-3/0085-decision-services/0085-decision-services-test-01.xml#002_c`: fallback to native.
- `compliance-level-3/0085-decision-services/0085-decision-services-test-01.xml#003`: fallback to native.

## Runner shutdown and documentation checks

Maven exited zero. The post-suite Surefire fork shutdown warning recurred thirty
seconds after System.exit(0), after every TCK case had passed. This is separate
from case execution timeouts; Spark/fork cleanup remains a follow-up.
Documentation facts verification, strict MkDocs build and Git whitespace checks passed.
