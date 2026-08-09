# DQ-001 - Reusable data-quality BKM library

Status: example - proposed

Profile: DMN pattern library

Source proposal: [Data-quality BKM patterns, MT564 showcase, and authoring acceleration](../../data-quality-patterns-and-authoring.md)

## Outcome

A DMN model author can import a small, versioned data-quality model and invoke documented BKMs to
produce consistent structured violations for required values, formats, code lists, conditional
presence, cross-field relationships, mutual exclusion, and duplicate detection.

## Observable acceptance examples

1. Given a required null or blank value, when `requiredValue` is invoked with rule metadata, then it
   returns one violation containing the supplied rule ID, severity, path, message, and expected state.
2. Given a valid value, when any validation BKM succeeds, then it returns an empty violation list
   rather than null or a success-shaped violation.
3. Given an invalid code, when `allowedValue` is invoked, then the violation identifies the actual
   value and allowed code set without embedding domain-specific codes in the generic library.
4. Given a conditional field requirement, when the condition is false, then absence is accepted; when
   the condition is true, normal required-value semantics apply.
5. Given repeated structures with duplicate stable keys, when `uniqueBy` runs, then deterministic
   violations identify the duplicate key and affected path.
6. Given the same scenarios on interpreter and generated Java, when results are evaluated, then
   violation values and ordering are equivalent.
7. Given a second domain model, when it imports the library, then it can reuse the BKMs without SWIFT,
   benchmark, or MT564 dependencies.

## Constraints

- Use standard DMN/FEEL constructs supported by the declared backends.
- Keep domain code lists, paths, wording, and severity choices in the consuming model.
- Define canonical `QualityViolation` and optional `QualityReport` ItemDefinitions.
- Prefer violation lists over booleans so failure information is not reconstructed downstream.
- Preserve deterministic ordering when multiple violations are returned.
- Avoid personally identifiable or production message data in fixtures.
- Version breaking BKM signature or output-schema changes deliberately.

## Non-goals

- Complete SWIFT or ISO 15022 validation.
- Parsing raw messages.
- A universal validation language.
- Automatic correction of invalid values.
- Scoring policies tied to one organization.
- Immediate extraction into a separate repository.

## Unknowns and opportunities

- Whether generic BKMs should return violation lists or nullable single violations
- How actual values should be represented when sensitive data must be redacted
- Whether `uniqueBy` can remain portable to the initial backend set
- Opportunity: reuse the library for ISO 20022 or internal reference-data checks
- Opportunity: generate a pattern catalogue from BKM metadata after signatures stabilize

## Verification

- Unit-like DMN scenarios cover success, null, empty, malformed, boundary, and multi-violation cases.
- A second small non-MT564 consumer proves the library is not accidentally domain-coupled.
- Multi-file compilation and import resolution run through the public compiler facade.
- Interpreter and generated-Java results are compared structurally.
- Repeated evaluation and generation are deterministic.
- Formatting and full Maven verification pass on Windows and Linux.

## Evidence required for completion

- BKM signature and output-schema reference;
- executable scenario bundle for every BKM;
- one MT564 and one non-MT564 importing consumer;
- interpreter/generated-Java parity results;
- compatibility and limitation notes.
