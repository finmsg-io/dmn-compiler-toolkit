# `dmn-grpc` TODO List

Last reviewed: 2026-10-28
Current status: **Listed generator/adapter foundation delivered; operational follow-up remains open.**

## Work Items

- [x] Transport-neutral `evaluation.proto` gRPC contract definitions
- [x] Polymorphic `Value` Proto $\leftrightarrow$ Java bidirectional value converter (`DmnGrpcValueConverter`)
- [x] Java gRPC service stub generator wrapping compiled decision engines (`DmnGrpcGenerator`)
- [x] In-process Netty-free gRPC server/client integration test suite (`DmnGrpcServiceTest`)
- [x] Module documentation and slice specifications


## Open follow-up work

The [hardening task](../dev/slices/security-correctness-hardening-task-2026-10-24.md)
records the completed external-function policy across service entry points
(HARD-01, verified by in-process denial/permission tests) and owns the continuing
investigation of deployment responsibilities (P2). Establish whether authentication,
TLS, request limits and deadlines belong to this adapter or its hosting service.
Absence of server bootstrap code is not, by itself, a defect in a service adapter.
The broader strongly typed contract milestone remains separate roadmap work.

## Typed service execution and conformance measurement

Typed conformance completed, 2026-10-29: **3,391/3,391 actual expected-result
passes**, with **3,494 typed RPC attempts and completed responses**, zero failures,
execution errors, unsupported cases or compilation errors. The user explicitly
authorized replacing the typed contract directly; regenerate typed clients and
servers. See the [passing checkpoint](../tck-conformance.md#typed-grpc-checkpoint-2026-10-29).

Generated bundles now include the Java engine and typed service handler. The
handler denies external Java by default and accepts a host-owned policy. Actual
HTTP/2/protobuf boundary tests now verify exact large decimals, explicit nulls,
nested null/empty lists, typed invocation and policy denial/permission. The
replacement regression build passed 17 tests, including four typed HTTP/2 tests.
Focused official verification passed 91/91 cases; the subsequent full run passed
all 3,391 cases. The generic API result is not substituted for typed execution.

Opt-in runner backend: `tck.backend=typed-grpc`. Coverage uses its own per-case
ledger and RPC/case counts; unsupported cases and schema/Java compilation errors
remain non-passing outcomes in the full 3,391-case denominator. A focused run
passed 2/3 cases and recorded one unsupported direct invocation, with two actual
RPC attempts/responses. Full measurement finished: **1,883/3,391 passed (55.53%)**,
1,369 failed assertions, 125 execution/decoding errors, 11 unsupported cases and
three schema compilation errors. RPC attempts/responses: 3,461/3,454. See the
[coverage record](../tck-conformance.md#typed-grpc-coverage-2026-10-28) and
[historical ledger](../assets/tck-accounting-typed-grpc-baseline.json). These figures
describe the original contract, not the replacement's current pass rate.

Lifecycle follow-up: the full passing typed run emitted a post-results Surefire
warning. Parent/fork captures showed XML replay of passed-test logs delaying the
exit acknowledgement. The runner uses Surefire 3.5.5 and omits duplicate passed-test
XML output; console logs, XML case results and failed-test stdout/stderr/stacks are
preserved. The TCP experiment was removed; the original shutdown timeout remains.
Full interpreter and Spark runs passed cleanly under the final reporting policy;
Spark retained 3,391 native passes and zero fallback. An isolated reporting probe
verified failure diagnostics remain present. The complete reactor passed 513 tests.
The final full typed rerun passed 3,391/3,391 cases with 3,494 completed RPCs,
Maven exit 0 and no shutdown warning (7,320.949 seconds). Its ledger exactly
matches the retained passing evidence. Both requested tasks are complete.

## Official gRPC TCK conformance

Status: completed locally, 2026-10-27. Scope confirmed by the user:
**generic Evaluate API first**, not the separate model-specific typed service
schema generator.

Acceptance: all 3,391 pinned official CL2/CL3 cases execute through generated
production handlers over real loopback HTTP/2/protobuf and match expected results.
No rejected, missing, skipped, unexecuted invocation or synthetic null is a pass.
Retain complete case-level accounting, RPC attempts/completed-response counts,
failures/errors/blocks and upstream revision. Grpc-specific data stays separate
from interpreter/Java/Spark evidence; SQL native/fallback metrics are N/A here.

Completion: **3,391/3,391 cases passed**, with 3,391 RPC attempts and completed
responses, zero failures, compilation blocks or execution errors. All eleven
direct-invocation cases executed. See the [public conformance record](../tck-conformance.md#generic-grpc-checkpoint-2026-10-27)
and [retained per-case evidence](../assets/tck-accounting-grpc.json).

Original full baseline: **1,881 passed, 1,498 failed, 12 execution errors** out of
3,391 recorded cases. There were 3,380 RPC attempts and completed responses;
11 direct-invocation cases were unexecuted and failed explicitly. Retained local
evidence: runner target `grpc-http2-full-baseline-accounting.json` and
`grpc-http2-full-baseline.log`. This baseline does not establish conformance.

The user approved additive request `invocable_name`/`arguments` and response
`invocation_result` fields. Fixes now retain explicit null outputs and nested null
members, support offset/zoned temporal values, and preserve compiler decision
slots across service declarations. All 21 focused regression tests passed:
compiler 8, gRPC 8, HTTP/2 harness 1 and strict runner checks 4. Official focused
invocation/interval run passed 68/69 cases with 69 RPC attempts and completed
responses. Its remaining failure exposed unevaluated service dependencies. After
the handler fix, all eight gRPC tests passed again and that official case passed
at a 120-second limit (one RPC and completed response). A prior 30-second retest
timed out and remains a failed check. The full corrected 3,391-case run passed
at 120 seconds per case, Maven exit 0, with a post-results Surefire shutdown
warning retained in the public record. The full reactor produced 513 passing
tests and no failures/errors; its exit code was unavailable after tool interruption.
The handler executes generated Java; no interpreter fallback is involved.
The implementation, regression tests, retained gRPC ledger and achievement
documentation form this completion checkpoint. The separate Spark revalidation
also passed 3,391/3,391 natively, zero fallback; its ledger exactly matches the
retained native evidence.
