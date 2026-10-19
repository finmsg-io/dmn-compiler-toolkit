# Nested component types, fresh runtime IR and conditional score lists

Continuation from the string/boxed checkpoint. Saved full baseline:
3,391/3,391 passed; 2,658 native (97.90%), 57 fallback (2.10%) of 2,715
executable cases, with 676 provisional expected model rejections separately.
The 100% native goal and semantic rejection audit remain open.

## Changes and artifact diagnosis

Semantic named-type expansion and member inference now retain inline nested item
components and their collection layers. A shared helper replaces flat expansion
that previously treated a component without typeRef as ANY despite declared child
components. Unknown structural members retain the existing ANY behavior.

The installed runtime-IR artifact and target JAR were stale: javap showed no
lowerComponent method despite recursive lowering being present in repository
source. Their SHA-256 was BAF70AC48BFB9C1AEEB1754A8BD1F680AB13900FB986C9AF689F940B41AF4342.
A clean rebuild of dmn-runtime-ir passed its tests, installed the current source,
and javap confirmed lowerComponent in the installed JAR. Fresh target/installed
SHA-256: FB5E0BEC189171EB5DF6EF088BFA38500A00F48B8E95DF3AA5E2664E7DAAC7C4.
Matching target/installed generator hashes alone do not prove dependency freshness.
Rebuild runtime dependencies when source and installed APIs disagree.

A bounded conditional-list proof recognizes nested arrays with literal primitive
leaves, boolean branch selection and empty list alternatives. Limits: 512 syntax
nodes, depth sixteen, bounded literal lengths and at most 38 combined integral/
fractional digits across numeric output leaves. Mixed primitive kinds retain
fallback. The proof permits one-level flattening only when the resulting leaves
are primitive; deeper recursive flatten shapes retain their route. SQL preserves
null-list/element removal. Dynamic or unverified conditions retain existing
capability checks; this proof does not override other unsupported operations.

## Verification history

- Initial semantic test: 28/29 passed. The failing assertion expected rejection
  of an unknown structural context member; existing behavior returns ANY and
  was preserved. No Spark SQL executed: native zero, fallback zero, denominator
  zero; rates undefined. Root target `native-nested-types-semantic-2026-10-19.log`.
- Corrected semantic suite: 70/70 passed and dependency build passed. No Spark
  SQL executed: native zero, fallback zero, denominator zero; rates undefined.
  Root target `native-nested-types-semantic-final-2026-10-19.log`.
- First generator regression: 107/108 passed; nested end-to-end input assertion
  found Monthly still ANY, exposing the stale installed runtime-IR artifact.
  Executed actions: 1,216 native (95.82%), 53 fallback (4.18%) of 1,269,
  zero failed/unknown SQL routes. Generator target
  `native-nested-types-generator-2026-10-19.log`.
- Clean runtime-IR rebuild: 27/27 passed. Native zero, fallback zero, denominator
  zero; rates undefined because this module executes no Spark SQL. Root target
  `native-nested-types-ir-rebuild-2026-10-19.log`.
- Fresh-IR generator regression: 108/108 passed; 1,220 native actions (95.84%),
  53 fallback (4.16%) of 1,273, zero failed/unknown. Formatting/install passed.
  Generator target `native-nested-types-fresh-ir-generator-2026-10-19.log`.
  Nested numeric comparisons cover true/false, missing fields and null contexts.

- Combined generator regression: 109/109 passed; 1,229 native actions (95.87%),
  53 fallback (4.13%) of 1,282; zero failed/unknown. Formatting/install passed.
  Generator target `native-nested-score-generator-2026-10-19.log`. Conditional
  list parity covers nine true/false/null combinations and retains mixed-kind
  and incompatible decimal-width fallbacks.

Verified target/installed artifact SHA-256 values:

- Generator: EDE4A2392E60A8C9D8F2521401BE5E4AE50692BBC0319A103A9D39ADD3F1F23D.
- Semantic analysis: 68D10EA209CF6D27B4034BAA78FCC54F4A617D7C60EC1185AED662F5B27FC095.
- Runtime IR: FB5E0BEC189171EB5DF6EF088BFA38500A00F48B8E95DF3AA5E2664E7DAAC7C4.

## Full official checkpoint

2026-10-19 LATEST FULL nested-type/score-list checkpoint: 3,391/3,391 passed.
Of 2,715 executable cases: 2,661 native (98.01%), 54 fallback (1.99%);
zero failed/unknown routes, compilation/execution errors, unsupported, excluded
or invalid cases. Separately: 676 provisional expected model rejections, unchanged.
Three fallback-to-native conversions, zero reverse changes: chapter 11 cases
003a (Application risk score), 003b (Pre-bureau risk category), 003c (Bureau call type).
Fallback decreased from 57 to 54 (5.26% fewer fallback cases). No executable
cases moved into model rejection. Chapter 11 now has five fallback cases.
Generator: 109/109 passed; 1,229 native actions (95.87%), 53 fallback (4.13%)
of 1,282; zero failed/unknown. Semantic suite 70/70 and clean runtime IR 27/27
passed. Semantic/IR suites execute no Spark SQL (denominator zero, rates undefined).
Formatting/install passed. Generator target/installed SHA-256:
EDE4A2392E60A8C9D8F2521401BE5E4AE50692BBC0319A103A9D39ADD3F1F23D.
Semantic analysis SHA-256: 68D10EA209CF6D27B4034BAA78FCC54F4A617D7C60EC1185AED662F5B27FC095.
Runtime IR SHA-256: FB5E0BEC189171EB5DF6EF088BFA38500A00F48B8E95DF3AA5E2664E7DAAC7C4.
Important build finding: the old target/installed IR JAR lacked lowerComponent
although source already contained it. A clean dmn-runtime-ir rebuild fixed that;
javap confirmed the method. Generator hash equality alone is insufficient to
establish dependency freshness. The nested-input integration test guards this gap.
Saved runner target evidence: native-nested-score-full-accounting-2026-10-19.json
and native-nested-score-full-tck-2026-10-19.log. Baseline comparison is against
native-string-boxed-full-accounting-2026-10-19.json.
The 100% native goal and semantic rejection audit remain open. Remaining leading
groups: chapter 11 five; function libraries, conditionals and table variable inputs
three each. Preserve exact arithmetic, temporal precision/offset/duration identity,
shared list typing and lexical binding. Dynamic FEEL library division stays fallback.
Restart full-run cadence here: 45 minutes work, usage remaining below 20%, or
twenty further verified conversions, whichever comes first.

### Verified route changes

- `compliance-level-3/0087-chapter-11-example/0087-chapter-11-example-test-01.xml#003a`: fallback to native.
- `compliance-level-3/0087-chapter-11-example/0087-chapter-11-example-test-01.xml#003b`: fallback to native.
- `compliance-level-3/0087-chapter-11-example/0087-chapter-11-example-test-01.xml#003c`: fallback to native.

Maven completed the full TCK with exit code zero. Documentation facts verification,
strict MkDocs build and Git whitespace checks passed. Verified evidence is retained in this handover.
