please consider
- ./generall-prompt.md
- ./details/guardrails.md

Status: CLOSED for the agreed scope, 2026-10-26. Do not start further implementation without a new task instruction.

- Completed native Spark milestone: all 3,391 official cases executed natively and matched expected results; zero fallback, failed cases, compilation blocks, errors or unknown routes. Source/evidence checkpoint f2af395. See the [public conformance record](../docs/tck-conformance.md).
- Completed [DOC-01](../docs/dev/slices/documentation-drift-remediation-task-2026-10-24.md): 166 Markdown pages classified, maintained links and publication/orphan policy enforced, historical journals separated, public claims corrected, MkDocs severities reviewed. All 69 maintained-page checks and strict documentation checks passed.
- Completed [coverage baseline checkpoint](../docs/dev/slices/security-correctness-hardening-task-2026-10-24.md#4-coverage-baseline-and-missing-error-paths): clean default reactor passed 503 tests; five added runtime boundary tests passed in a separate 17-test run. All 56 tooling tests passed. Generated-code exclusions, module-own line/branch coverage and separate runtime impact are recorded in retained JSON evidence.
- Coverage thresholds/enforcement are explicitly deferred: the user chose baseline only. HARD-02 legacy-parser timeouts remain deferred. These do not reopen the completed scope.
- The user requested publication of the final verified documentation/coverage changes. This closing checkpoint includes them; preceding pushed checkpoint is 97b03b2. The agreed implementation scope remains closed.
- Recommended next independent item: P19.1 native TCK inventory/outcome regression guard, from the [ordered backlog](../docs/todos/index.md). Require every pinned official case to execute, match expected results and pass natively; reject missing cases, compiler blocks, failures/errors, unknown routes and fallback. Confirm this scope before starting.
