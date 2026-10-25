# `dmn-grpc` TODO List

Last reviewed: 2026-10-24
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
