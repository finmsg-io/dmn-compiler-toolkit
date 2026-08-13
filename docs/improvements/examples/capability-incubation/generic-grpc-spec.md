# INC-GRPC-001 — Stabilize the generic gRPC adapter

Status: example — proposed  
Candidate maturity: `incubating`  
Module: `dmn-grpc`  
Capability: dynamic `Value` schema and generic evaluation service  
Policy: [Capability maturity and incubation](../../capability-maturity-and-incubation.md)

## Outcome

Service teams can expose compiled DMN evaluation through the generic gRPC contract with documented
value semantics, lifecycle, error behavior, concurrency guarantees, and cross-version compatibility.

## Current production recommendation

Controlled service integration is acceptable when client/server versions are managed together and
the documented dynamic-value scope is sufficient.

## Acceptance examples

1. Given every supported FEEL value type, when encoded into the generic `Value` schema, transported,
   decoded, and evaluated, then the result matches direct runtime evaluation.
2. Given invalid model identity, inputs, types, or evaluation failure, the service returns stable,
   documented status/error behavior without leaking internal details.
3. Given concurrent requests against a reusable generated service, results remain isolated and
   deterministic.
4. Given a client generated from the previous supported schema version, compatible server evolution
   preserves field numbers and expected behavior.
5. Given oversized or deeply nested request values, configured size/depth limits reject the request
   predictably.
6. An external client/server example builds from released artifacts and completes an evaluation over
   a real gRPC channel.

## Required scope declaration

- Mapping for null, boolean, number, string, temporal, list, and context values
- Numeric precision and temporal/time-zone encoding
- gRPC status versus application `EvaluationError` policy
- Model selection and unknown decision behavior
- Request/response size and nesting limits
- Thread safety and generated service lifecycle
- Authentication/authorization boundary (provided versus delegated to host application)
- Schema compatibility and supported client/server version policy

## Constraints

- Dynamic transport conversion must preserve the supported FEEL value semantics.
- Security/authentication features not provided by the adapter are stated explicitly.
- Internal exception messages and model data are not exposed unintentionally.
- Field numbers are never reused after publication.
- Incubating status does not change artifact coordinates.

## Graduation evidence

- Value conversion round-trip/property tests
- Runtime versus gRPC end-to-end parity
- Concurrent evaluation and lifecycle tests
- Adversarial size/depth/error tests
- External generated-client integration test
- Accepted generic-schema evolution policy
- Cross-version compatibility fixture
- Operational documentation for deadlines, limits, errors, and host security responsibilities

## Graduation decision

Graduate to `stable` when generic value semantics, errors, concurrency, limits, external consumption,
and schema evolution are documented and executable. Typed API stability is evaluated separately and
does not block generic gRPC graduation.
