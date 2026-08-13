# INC-GRPC-002 — Stabilize typed protobuf and gRPC generation

Status: example — proposed  
Candidate maturity: `incubating`  
Module: `dmn-grpc`  
Capability: generated typed protobuf schemas and services  
Policy: [Capability maturity and incubation](../../capability-maturity-and-incubation.md)

## Outcome

Users can regenerate typed protobuf/gRPC APIs after compatible DMN model changes without unexpected
field renumbering, message/service renaming, or client breakage, and can predict when a model change
requires a breaking release.

## Current production recommendation

Controlled use is acceptable when generated clients and servers are versioned/deployed together.
Independent long-lived client compatibility is not promised until the schema evolution policy and
cross-version tests pass.

## Acceptance examples

1. Given the same DMN model and options, repeated generation produces byte-identical schema and Java
   source with stable package, message, service, method, and field names/numbers.
2. Given an additive optional field in a DMN item definition, regeneration follows the accepted
   compatible field-allocation policy and an older client can communicate with the newer server.
3. Given rename, removal, type change, requiredness change, or structural movement, generation
   identifies whether the change is compatible, requires reserved identifiers/numbers, or is
   breaking.
4. Given colliding, hostile, reserved-word, Unicode, or invalid protobuf identifiers, deterministic
   sanitization either produces collision-free stable names or fails with a diagnostic.
5. Given a previous released generated schema, compatibility verification detects an accidental
   field-number/name/service break before publication.
6. An external consumer generates/compiles clients and servers from released artifacts and completes
   a typed evaluation.

## Decisions required

- Stable identity used to allocate protobuf field numbers
- Storage of allocation history across regeneration
- Rename detection versus remove/add semantics
- Reserved field numbers and names after removal
- Optional/required/presence policy
- Mapping of DMN types, lists, contexts, temporal values, and recursion
- Package/message/service naming and collision policy
- Compatibility scope across toolkit versions and generated-model versions

These decisions resolve development-plan D-005 and should be accepted in an ADR before graduation.

## Constraints

- Field numbers are deterministic and never silently reused.
- Schema compatibility is evaluated against a retained prior schema, not inferred only from current
  source.
- Generated output remains deterministic across Windows/Linux and locale settings.
- Incompatible model changes produce explicit diagnostics or a declared major-version workflow.
- Incubating status remains visible in API/Javadocs and does not rename Maven coordinates.

## Graduation evidence

- Accepted typed-schema compatibility ADR
- Golden schemas and cross-version fixtures
- Automated protobuf compatibility checks
- Deterministic naming/numbering/collision tests
- Add/change/remove/rename evolution matrix
- External older-client/newer-server integration test
- Generated-source compilation and runtime evaluation parity
- Migration and versioning documentation

## Graduation decision

Typed gRPC remains `incubating` while D-005 is unresolved. It graduates only when schema identity,
field allocation, evolution rules, cross-version verification, and external-consumer behavior form
an enforceable compatibility contract.
