# Closed context collections and exact literal results

Continuation from the date-comparison checkpoint. Saved full baseline:
3,391/3,391 passed; 2,664 native (98.12%), 51 fallback (1.88%) of 2,715
executable cases; 676 provisional expected model rejections separately.
The 100% native goal and semantic rejection audit remain open.

## Change

Closed literal path projections, filters and expression-based membership tests
can be evaluated once at generation time using shared FEEL runtime semantics.
Only results accepted by the exact native literal converter are emitted. This
preserves missing-key versus explicit-null context identity, list order and null
projection entries without synthesizing missing context fields. Query execution
uses SQL literals, with no runtime interpreter/UDF for admitted expressions.

Bounds: 512 expression nodes, depth sixteen, 64 entries per literal list/context,
64 membership endpoints, local slots below 2,048, 2,048-character literal values,
256-character keys/path members and bounded numeric precision/scale. Invalid
UTF-16 surrogates are rejected. Filter predicates admit only the filter's own
local binding, property access, comparisons, boolean operators and literal
selectors. Function calls, loops, external value references and nested filters
are outside this proof. Unrepresentable final decimal arrays and mixed context
layouts retain fallback. Existing native-filter representation proofs remain
valid, including same-key context arrays with nullable fields.

## Verification history

- Initial focused run: one failed assertion after 12 native actions (92.31%),
  one fallback (7.69%) of 13; zero failed/unknown SQL routes. The new folding
  proof rejected an unrepresentable decimal projection, but the original route
  still classified it as native. Added a capability guard for proven closed
  operations requiring lossless result representation. Generator target
  `native-closed-collections-focused-2026-10-19.log`.
- Corrected focused run: one test passed; 12 native actions (85.71%), two
  deliberate fallback actions (14.29%) of 14; zero failed/unknown. Generator
  target `native-closed-collections-verified-2026-10-19.log`. Coverage includes
  missing/null keys, membership identity, absent projection members, empty
  filters, positive/negative/out-of-range indexing and decimal width guards.
- Initial full generator regression: 110/111 passed; the new guard incorrectly
  rejected an existing nullable same-key context filter. Executed actions:
  1,500 native (96.46%), 55 fallback (3.54%) of 1,555; zero failed/unknown SQL
  routes. Reused the existing native-filter proof to retain that route.
  Generator target `native-closed-collections-generator-2026-10-19.log`.

- Corrected full generator regression: 111/111 passed; 1,505 native actions
  (96.47%), 55 fallback (3.53%) of 1,560; zero failed/unknown. Formatting/install
  passed. Generator target `native-closed-collections-final-generator-2026-10-19.log`.

Target and installed generator SHA-256 match:
`B2E6F69AF3F3297B72869510E7BC12A9F9E3DCA22875AFC48E8E8D4B5FFBAC32`.
Full official verification completed; checkpoint follows.

## Full official checkpoint

2026-10-19 LATEST FULL closed-collection checkpoint: 3,391/3,391 passed.
Of 2,715 executable cases: 2,668 native (98.27%), 47 fallback (1.73%);
zero failed/unknown routes, compilation/execution errors, unsupported, excluded
or invalid cases. Separately: 676 provisional expected model rejections, unchanged.
Four verified fallback-to-native conversions: 0069 list cases 032/034 and 0072
context membership context_001/context_001_a. Zero reverse conversions or rejection
reclassifications against the date-comparison full checkpoint. Fallback decreased
from 51 to 47 (7.84% fewer fallback cases).
Generator: 111/111 passed; 1,505 native SQL actions (96.47%), 55 fallback (3.53%)
of 1,560; zero failed/unknown. Formatting/install passed. Target/installed JAR:
B2E6F69AF3F3297B72869510E7BC12A9F9E3DCA22875AFC48E8E8D4B5FFBAC32.
Retained dependency hashes: semantic analysis
68D10EA209CF6D27B4034BAA78FCC54F4A617D7C60EC1185AED662F5B27FC095;
runtime IR FB5E0BEC189171EB5DF6EF088BFA38500A00F48B8E95DF3AA5E2664E7DAAC7C4.
Saved runner target: native-closed-collections-full-accounting-2026-10-19.json and
native-closed-collections-full-tck-2026-10-19.log. The new capability guard retains
fallback for unrepresentable closed results while reusing existing nullable-context
native filter proofs. Missing context keys remain distinct from explicit nulls.
The 100% native goal and semantic rejection audit remain open. Remaining leading
groups: chapter 11 five; function libraries and table-variable inputs three each.
Preserve exact arithmetic, temporal precision/offset/duration identity, list typing
and lexical binding. Never promote dynamic FEEL division through Spark doubles.
Restart cadence here: 45 minutes work, remaining usage below 20%, or twenty
further verified conversions, whichever comes first. Verified evidence is retained in this handover.

### Verified full route changes

- `compliance-level-3/0069-feel-list/0069-feel-list-test-01.xml#032`: fallback to native.
- `compliance-level-3/0069-feel-list/0069-feel-list-test-01.xml#034`: fallback to native.
- `compliance-level-3/0072-feel-in/0072-feel-in-test-01.xml#context_001`: fallback to native.
- `compliance-level-3/0072-feel-in/0072-feel-in-test-01.xml#context_001_a`: fallback to native.

Maven completed the full TCK with exit code zero. Documentation facts verification,
strict MkDocs build and Git whitespace checks passed.
