please consider
- ./generall-prompt.md
- ./details/guardrails.md

Status: release 1.1.0 preparation in progress, 2026-10-29.

- User authorized merging the feature branch into main and releasing 1.1.0 before starting portable .dmnir work.
- Feature PR #2 merged into main (dad5431); source checkpoint 63821be passed branch CI. Release branch: codex/release-1.1.0, based on merged main.
- Typed gRPC and shutdown tasks COMPLETE: 3,391/3,391 typed cases, 3,494 completed RPCs; final clean run Maven 0, no shutdown warning. Interpreter and Spark full clean-exit runs also passed all 3,391; Spark native 3,391 (100%), fallback 0 (0%), no failures/errors/blocks/unknown. Separate retained ledgers and dated provenance: docs/tck-conformance.md.
- Prepare reactor version 1.1.0, release notes with explicit typed wire migration, source/Javadoc packaging, documentation and tooling verification, then release PR to main. Tag v1.1.0 only on merged main after verification.
- Existing publish-release.yml validates the tag/main ancestry, clean build, full optimized TCK and source/Javadoc artifacts before signed Central Portal publication. Release environment currently has no reviewers configured; tag push triggers automatic publication after checks. Do not claim published until registry availability is verified.
- Preserve pinned inventory 20274cd2ba9cad805db6114f331c743f4b2603a1 and separate backend accounting. Local gRPC TCK reruns are already complete; release metadata does not change engine semantics.
- Portable Protobuf .dmnir is future work, not implemented; existing Runtime IR is process-local (ADR-0025). Coverage enforcement and legacy-parser timeout work remain deferred.
