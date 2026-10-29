# Bounded native function lowering — 2026-10-13

Hybrid generation now performs bounded static substitution of FEEL function
applications before capability analysis. It preserves lexical capture, parameter
shadowing, named argument binding, boxed context locals and closures returned by
ANY-typed intermediate decisions. Required dependencies are recomputed from the
remaining value references, so native consumers do not materialize Java closures.
Fallback UDFs and generated Java runners retain the original runtime model.

Primitive scalar/list coercion is applied at parameter, function-return and
decision-result boundaries. Null stays null during singleton-list wrapping.
Numeric built-in function arguments support abs, sqrt and verified constant sum
calls. External functions, unsupported coercions and unbound targets retain the
established fallback. Depth and expansion budgets prevent recursive or enormous SQL.

## Regression evidence

- Lexical capture, shadowing and named binding: five native actions (100%), zero
  fallback, denominator five; passed.
- Closure and guard boundary set: six native (54.55%), five deliberate fallback
  (45.45%), denominator 11; passed.
- Nullable coercion and closure boundary set: 12 native (70.59%), five deliberate
  fallback (29.41%), denominator 17; four tests passed.
- Initial full source passed 81 tests, but installation failed formatting after
  a test was added during the run. Final guarded source subsequently passed 82
  tests and installation: 769 native (97.10%), 23 fallback (2.90%), denominator
  792. These measurements precede the later boxed/coercion/builtin additions.

The coercion fixture initially included an input ID in executable evaluation
order; this was corrected. Its first corrected version used sum(null), which
exposed an existing shared-runtime exception. List identity now isolates nullable
coercion. The sum(null) runtime issue remains outstanding; no runtime fix is claimed.

The accumulated batch passed 85 generator tests: 781 native actions (97.14%) and
23 fallback (2.86%), denominator 804, zero failed or unknown routes. A final
singleton-null guard subsequently passed three focused tests: 11 native (64.71%)
and six deliberate fallback actions (35.29%), denominator 17.

The first full TCK of this batch had 2,538 native passes (93.48%), 176 fallback
passes (6.48%) and one failed case (0.04%) of 2,715 executable cases. Separately,
676 provisional expected model rejections passed. The failure was 0076 boxed_001:
an implicit external Java method descriptor was inlined as a context rather than
invoked. The 2026-10-14 correction conservatively retains fallback for Java
descriptor contexts. A regression verifies that the implicit Math.max descriptor
returns 456, preserving the original invocation semantics. Verification of this
corrected source passed all 86 generator tests, formatting and installation:
781 native actions (96.90%), 25 fallback (3.10%), denominator 806, zero failed
or unknown routes. The explicit Java descriptor regression returns 456 through
fallback. The corrected full TCK passed 3,391/3,391 cases: 2,538 native (93.48%),
177 fallback (6.52%), denominator 2,715 executable cases; 676 provisional expected
model rejections separately. Zero failed/unknown routes, compilation errors or
execution errors. Compared with the calendar checkpoint, 18 cases converted from
fallback to native: 15 in 0092 lambda, two in 0082 coercion and one in 0099
arithmetic negation. No native-to-fallback conversions. Evidence: runner
target/native-static-functions-corrected-full-accounting.json,
native-static-functions-corrected-full-tck.log and native-static-functions-conversions.json.
Generator evidence: target/native-static-functions-corrected-generator.log.
Regression SQL-action counts remain separate from official case conversions.

## Full TCK cadence

The user requests a full TCK after 45 minutes of work, usage remaining below 20%,
or 20 fallback rules converted, whichever occurs first. Usage reached 12% during
this batch, triggering final verification and the full TCK checkpoint. Stop new
feature work while completing verification and handover.
