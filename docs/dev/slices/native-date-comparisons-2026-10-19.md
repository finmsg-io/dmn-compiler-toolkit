# Exact date-input comparisons and string conditionals

Continuation from the nested-score checkpoint. Saved full baseline:
3,391/3,391 passed; 2,661 native (98.01%), 54 fallback (1.99%) of 2,715
executable cases; 676 provisional expected model rejections separately.
The 100% native goal and semantic rejection audit remain open.

## Change

A narrow date-input proof admits comparison of one declared DATE input with a
validated constant DATE endpoint, either directly as a boolean result or as the
condition selecting verified string branches. It supports all six comparison
operators and both operand orders. Endpoints are date literals or one-argument
literal-string date constructors, parsed with LocalDate within 64 characters.
String branches are constants/direct STRING inputs or substring before/after
with a literal delimiter. Opaque inputs, datetime comparisons, dynamic endpoints,
nested branch calls and other expression shapes retain existing checks.

The existing calendar boundary projection carries exact year/month/day fields.
Comparison uses a BIGINT year*10000+month*100+day key, within safe bounds even at
LocalDate.MIN/MAX, without Spark DATE/TIMESTAMP conversion. Projection now applies
shared declared-input coercion first, including singleton unwrapping. ANY calendar
inputs retain their established parsing semantics. Null or invalid typed DATE
values follow FEEL equality/inequality and ordering-null semantics. Original
opaque payload bytes remain unchanged for other consumers.

## Verification history

- Focused date parity: one test passed; 240 native SQL actions (100%), zero
  fallback (0%) of 240; zero failed/unknown. All six operators, both orders,
  extreme/zero years, boundary equality, null, wrong types and singleton coercion.
  Generator target `native-date-comparison-focused-2026-10-19.log`.

- Full generator: 110/110 passed; 1,493 native actions (96.57%), 53 fallback
  (3.43%) of 1,546, zero failed/unknown. Formatting/install passed. The expanded
  date test executes 264 native actions, covering offset-datetime wrong-type
  rejection and literal date constructor endpoints as well as the original cases.
  Generator target `native-date-comparison-generator-2026-10-19.log`.
- Official conditionals: 6/6 passed, six native (100%), zero fallback (0%) of six,
  zero failed/unknown or expected rejections. Maven exited zero.

Targeted verification confirmed cases 004, 005 and 006 moved from fallback to
native, with zero reversals. Usage then reached 20% remaining and triggered the
full run below, superseding the earlier targeted-only global baseline.

## Full official checkpoint

2026-10-19 LATEST FULL date-comparison checkpoint: 3,391/3,391 passed.
Of 2,715 executable cases: 2,664 native (98.12%), 51 fallback (1.88%);
zero failed/unknown routes, compilation/execution errors, unsupported, excluded
or invalid cases. Separately: 676 provisional expected model rejections, unchanged.
Three verified fallback-to-native conversions: 0032 conditionals cases 004, 005,
006; zero reverse conversions or rejection reclassifications against the nested-score
full checkpoint. Fallback decreased from 54 to 51 (5.56% fewer fallback cases).
Generator: 110/110 passed; 1,493 native SQL actions (96.57%), 53 fallback (3.43%)
of 1,546; zero failed/unknown. Formatting/install passed. Target/installed JAR:
B4C2DB4CF5931D4F01FD96840198B653BED54E83E3E83B7B8C0C150BB7765566.
Retained dependency hashes from the previous checkpoint: semantic analysis
68D10EA209CF6D27B4034BAA78FCC54F4A617D7C60EC1185AED662F5B27FC095;
runtime IR FB5E0BEC189171EB5DF6EF088BFA38500A00F48B8E95DF3AA5E2664E7DAAC7C4.
Saved runner target: native-date-comparison-full-accounting-2026-10-19.json and
native-date-comparison-full-tck-2026-10-19.log. Full run triggered when primary
usage reached 20% remaining, after targeted verification; no feature edits followed.
The 100% native goal and semantic rejection audit remain open. Preserve temporal
precision/offset/duration identity and exact arithmetic. Remaining leading groups:
chapter 11 five; function libraries and table-variable inputs three each.
Restart cadence here: 45 minutes work, remaining usage below 20%, or twenty
further verified conversions, whichever comes first. Verified evidence is retained in this handover.

Verified full route changes:

- `compliance-level-3/0032-conditionals/0032-conditionals-test-01.xml#004`: fallback to native.
- `compliance-level-3/0032-conditionals/0032-conditionals-test-01.xml#005`: fallback to native.
- `compliance-level-3/0032-conditionals/0032-conditionals-test-01.xml#006`: fallback to native.

Maven completed the full TCK with exit code zero. Documentation facts verification,
strict MkDocs build and Git whitespace checks passed.
