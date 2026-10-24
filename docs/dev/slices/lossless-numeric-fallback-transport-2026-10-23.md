# Lossless numeric input and result transport for fallback

## Problem and implementation

The numeric scalar-context regression exposed precision loss in the ordinary
fallback boundary: typed NUMBER inputs and outputs crossed Spark DOUBLE. An
exact runtime value could therefore differ from the decoded fallback result.
This work addresses transport correctness; it does not make interpreter calls
native SQL or claim an official fallback reduction.

When hybrid generation includes fallback, numeric-bearing model inputs receive
additional versioned binary FEEL payload columns. This covers NUMBER, typed lists
of numbers and typed contexts containing numeric fields recursively. Existing
native columns and exact native projections remain available. Fallback input
structs select the encoded columns; combined-query CTEs retain them so later
fallback decisions can evaluate their original subgraphs without rounded inputs.

Fallback NUMBER and numeric-bearing LIST results use binary FEEL payloads.
CONTEXT results already used binary transport. The payload preserves BigDecimal
coefficient and scale; a null result remains SQL NULL. No Java computation is
moved into input projection: it performs declared input coercion and serialization.

## Caller contract and native boundaries

Rows for generated hybrid schemas must be built with
`SparkSqlFeelValueCodec.inputRow(model, values, generated.inputSchema())`.
The two-argument overload only creates the ordinary input columns and cannot
populate additional generated projections. Existing externally supplied DOUBLE
columns cannot recover precision already lost before encoding: retain exact
source values when constructing payloads.

Regenerate SQL, schemas and runner artifacts together after this change.
Interpreter return types for numeric results change from DOUBLE/arrays of DOUBLE
to BINARY. Decode them using SparkSqlFeelValueCodec.fromSpark. Native-only input
schemas remain unchanged. This change does not make arbitrary existing native
NUMBER expressions exact; those still require a separately verified exact plan.

Routing remains static. Interpreter actions count as fallback, even when their
input/result transport is lossless. SQL exceptions do not trigger a retry.

## Verification

Focused scalar and nested-list regression passed both tests: 2 native SQL actions
(14.29%), 12 fallback (85.71%) of 14; unknown 0. Two native SQL errors are
expected passing assertions for unused-entry evaluation. Cases include positive
and negative precision-sensitive scalar results, lists with large/small values,
empty lists, nested context input fields, custom name collisions and repartitioned
individual/combined queries.
Log: lossless-fallback-numeric-focused-2026-10-23.log.

Verification continued with native structured rankings; see
[the complete run history](native-ranked-contexts-2026-10-24.md). The corrected
full generator suite passed 154/154 tests: 2,954 native actions (96.16%),
118 fallback (3.84%) of 3,072; unknown 0, 58 expected native SQL errors.
A subsequent focused five-test run after the final codec addition passed:
18 native (38.30%), 29 fallback (61.70%) of 47; unknown 0, two expected native
SQL errors and one expected fallback SQL error. Null numeric results remain
SQL NULL; unused non-finite numeric arguments are retained without premature
serialization errors, while evaluating their arithmetic still raises an error.
Non-finite Double/Float values use payload tags 20/21 and retain their types.
These tagged payloads require matching toolkit code on driver and executors.
