# Exact native temporal expression chains

## Reusable proof and execution

The temporal chain planner expands supported decision references and flat contexts
into a bounded topological SQL aggregate. It does not inspect TCK case identifiers,
model names, or expected results. Inputs are declared strings, exact numbers,
and Java duration/period representations. Constructor validation, property access,
duration arithmetic, local/offset date-time subtraction, and year/month differences
operate on explicit components rather than Spark timestamps. Nanoseconds remain
integers, dates remain epoch-day values, and offsets remain seconds. Period and
Duration values retain distinct payload tags and component semantics.

Spark built-in `try_reflect` invokes standard JDK `java.time` factories for parsing,
validation, and canonical formatting. These are JVM calls through a Spark built-in;
this is not an assertion that execution consists only of primitive SQL arithmetic.
No FEEL interpreter, DmnRuntime evaluator, registered decision UDF, or custom
calculation method is called from SQL. SQL performs component arithmetic, zone
rule selection, coercion checks, and final binary payload construction. Input
projection performs declared coercion and representation extraction, and loads
reference zone rules; it does not evaluate a decision or resolve an input date's
zone offset in Java.

Named zones supplied in string inputs carry JDK reference metadata: initial offset,
historical transitions, and recurring transition rules. SQL selects offsets at the
actual local date/time, including the runtime's preferred offset in gaps/overlaps.
The input date/time is not reduced to a microsecond timestamp. The generated-schema
inputRow overload is required to populate these projections. Regenerate SQL,
input schema, and runner artifacts together.

## Explicit boundaries

The proof has a 512-expression parse budget and maximum expansion depth 32.
Contexts are flat and limited to 16 entries. Typed context results currently require
constructor-proven DATE fields; untyped flat contexts can contain other supported
scalar outputs. Arbitrary string input is not admitted as a terminal string result
without a lossless proof. Named-zone string constants currently stay outside this
proof; named zones in declared string inputs are supported. Constant-only expressions
retain their existing generator paths and do not require input projections.

Loops, dynamic functions, decision tables, nested contexts, raw typed date/time
inputs, and unproven downstream consumers stay on other existing native paths or
fallback. This is reusable bounded support, not unrestricted FEEL support. A change
outside a proof can still require fallback even when all executable official TCK
cases are native. Temporal terminal payloads must not be read by an unproven
consumer as ordinary Spark numbers or timestamps.

## Validation history

All counts below are observed Spark SQL actions, not TCK cases. Failed SQL actions
include expected error assertions; failed JUnit tests are stated separately.

| Run/log prefix | Tests | Native | Fallback | Denominator | Unknown | Failed SQL | Result |
| --- | --- | --- | --- | --- | --- | --- | --- |
| native-temporal-jdk-factories-2026-10-24 | 1 | 1 (100%) | 0 (0%) | 1 | 0 | 0 | passed |
| native-temporal-components-2026-10-24 | 2 | 6 (100%) | 0 (0%) | 6 | 0 | 0 | passed |
| native-temporal-chain-focused-2026-10-24 | none | 0 (undefined) | 0 (undefined) | 0 | 0 | 0 | compilation stopped at dependency collection type |
| native-temporal-chain-focused-retry-2026-10-24 | 3 | 9 (100%) | 0 (0%) | 9 | 0 | 0 | passed |
| native-temporal-chain-full-generator-2026-10-24 | 160 | 2956 (96.16%) | 118 (3.84%) | 3074 | 0 | 64 | 155 passed; 5 projection-boundary errors; 58 SQL errors expected |
| native-temporal-chain-corrected-full-generator-2026-10-24 | 161 | 2973 (96.15%) | 119 (3.85%) | 3092 | 0 | 59 | passed; all SQL errors expected |

Logs are under dmn-generator-sparksql/target with the listed prefix and .log suffix.
The initial full regression revealed constant-only roots were incorrectly routed
to a projected-input plan. The planner now leaves these on their existing native
paths. Additional guards preserve the distinct accepted operand kinds of the one-
and two-argument date-and-time constructors.

Variations compare generated SQL with the shared runtime for nine-digit fractions,
differing offsets, named-zone DST transitions and future overlap rules, valid and
invalid leap days, year zero, negative and large years, normalized periods, retained
period days, negative nanosecond durations, invalid duration text, fractional numeric
time constructors, and incompatible date-and-time constructor operands.

Corrected full generator regression/install passed 161/161 tests with no failures,
errors, or skips. Observed actions: 2,973 native (96.15%), 119 fallback (3.85%),
denominator 3,092, unknown 0. All 59 failed SQL actions were expected error
assertions (58 native and one fallback). The temporal class contributes 19 native
actions (100%), zero fallback, denominator 19, failed/unknown 0.

Log: dmn-generator-sparksql/target/native-temporal-chain-corrected-full-generator-2026-10-24.log.
Both target and installed generator artifacts have SHA256:
941A917B54C82D03B59C865D37193E21FBDB98132167E6C2CAD7EA0748C45E8C.

The full official optimized hybrid Spark SQL TCK passed all 3,391 outcomes:

| Route | Cases | Of executable cases | Of all cases |
| --- | --- | --- | --- |
| Native Spark SQL | 2,715 | 100% of 2,715 | 80.06% of 3,391 |
| Fallback | 0 | 0% of 2,715 | 0% of 3,391 |
| Compiler-rejected: NO Spark SQL executed | 676 | excluded from this denominator | 19.94% of 3,391 |

Failed/errors/unknown, unsupported/excluded/invalid/unexpected compilation errors:
zero. All case keys and statuses match the prior ranked-context full checkpoint;
exactly one route changes: 0007-date-time#001 FALLBACK -> NATIVE. No reversals
or rejection changes. The 676 expected rejections are passing negative-test
outcomes, not SQL execution, and their exact reasons remain unaudited.

Accounting: dmn-tck-runner/target/native-temporal-chain-full-accounting-2026-10-24.json.
Rejections: dmn-tck-runner/target/native-temporal-chain-full-rejections-2026-10-24.tsv.
Log: dmn-tck-runner/target/native-temporal-chain-full-tck-2026-10-24.log.

## Meaning of the 676 expected model rejections

These are error-expecting TCK cases rejected by the shared compiler before SQL
emission. The runner records an expected rejection only when compilation returns
unsuccessfully and every requested expected result is marked as an error. A thrown
preparation exception is not accepted by that branch. Neither native SQL nor a
fallback evaluator executes for these cases.

The saved 676 diagnostics contain 645 operand-type errors, 19 decision/BKM declared
result-type mismatches, and 12 other semantic errors. These counts are diagnostic
categories, not an audit proving each rejection reason matches the TCK's intent.
The runner's current check matches error expectation at case level; it does not
compare a specific expected diagnostic code. Such an audit remains outstanding.

These cases do not prove a Spark platform limitation. Some error semantics could
be represented by native SQL error expressions if the compiler admitted a runtime
error plan, but that is a distinct compiler/runner design change. A 100% native
rate over 2,715 executable cases must never be described as 3,391 cases executing
native SQL. All-case pass accounting includes the 676 negative-test rejections.

The full TCK Maven process exited 0. After that run, only the test runner's console
summary wording changed: expected compiler rejections explicitly say NO Spark SQL
executed, and native/fallback shares use explicit non-rejected and all-case
denominators. The summary also names the rejection diagnostic report and states
that exact diagnostic causes are not matched. This reporting-only change does not
alter routing, assertions, or generated artifacts; it is separately compiled.
