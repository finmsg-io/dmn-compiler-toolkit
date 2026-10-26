please consider
- ./generall-prompt.md
- ./details/guardrails.md


please work on

- Ordered master backlog: [active work](../docs/todos/index.md#active-work-in-priority-order).
- Active task: [security and correctness hardening](../docs/dev/slices/security-correctness-hardening-task-2026-10-24.md). Native Spark TCK goal verified: all 3,391 official cases executed natively and matched expected results; 3,391 native (100%), zero fallback (0%), zero failures/errors/compilation blocks/unknown, Maven exit 0 without shutdown warning. Final fallback 0082 invoke_007 converted; exactly one route changed against the preceding full run. Runner rejects compilation blocks and missing outputs. Evidence: runner target native-invalid-binding-full-tck.log, native-invalid-binding-full-accounting.json and native-invalid-binding-baseline-comparison.json. The implementation and evidence are committed together in this publication checkpoint. User approved this fallback fix below the 20% usage reserve; resume the normal reserve rule after this checkpoint. Preserve fixtures; legacy-parser timeouts remain deferred.
- Planned task: [documentation drift remediation](../docs/dev/slices/documentation-drift-remediation-task-2026-10-24.md).
