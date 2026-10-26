please consider
- ./generall-prompt.md
- ./details/guardrails.md


please work on

- Ordered master backlog: [active work](../docs/todos/index.md#active-work-in-priority-order).
- Active task: [security and correctness hardening](../docs/dev/slices/security-correctness-hardening-task-2026-10-24.md). The duration([]) fallback is fixed and verified: 168 generator tests passed; full inventory recorded 2,729 executed native cases, zero fallback, and 662 unexecuted compiler rejections. Do not call the latter passes. User order: commit/push this checkpoint first; afterwards require every TCK case to execute and produce its expected result. Remove rejection-as-pass, reject missing result nodes, and fix the 662 blocked cases. Keep fixtures unchanged and report execution against all 3,391 cases. Prior HARD-03/audit/14-runtime fixes are included in the pending checkpoint; legacy-parser timeouts remain deferred. Latest checked usage window had reset (86% primary remaining).
- Planned task: [documentation drift remediation](../docs/dev/slices/documentation-drift-remediation-task-2026-10-24.md).
