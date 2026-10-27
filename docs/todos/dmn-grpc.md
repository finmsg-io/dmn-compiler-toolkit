# `dmn-grpc` TODO List

Last reviewed: 2026-10-27
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
