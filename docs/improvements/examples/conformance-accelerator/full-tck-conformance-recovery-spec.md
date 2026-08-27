# TCK-CONF-001 — Full CL2/CL3 conformance recovery

Status: `done`  
Priority: **P0 — highest repository priority (achieved)**  
Pinned TCK revision: `20274cd2ba9cad805db6114f331c743f4b2603a1`  
Declared roots: `TestCases/compliance-level-2`, `TestCases/compliance-level-3`

## Outcome

Every official CL2/CL3 test case executes successfully against both the interpreter and generated-Java
backend. Completion means exactly **3,391/3,391 passing cases** and **6,782/6,782 passing backend
outcomes**. Compilation errors, execution errors, wrong results, unsupported classifications,
exclusions, invalid entries, and missing outcomes are all non-passing.

## Verified starting baseline

Strict accounting on 2026-08-14 discovered 146 test XML files, 150 DMN files, and 3,391 test cases.

| Measure | Count | Rate |
| --- | ---: | ---: |
| Passing cases | 118 | 3.48% |
| Non-passing cases | 3,273 | 96.52% |
| Passing backend outcomes | 236 | 3.48% |
| Non-passing backend outcomes | 6,546 | 96.52% |

The non-passing baseline consists of compilation-blocked cases. It does not prove semantic failure
after execution; it does prove that those cases did not pass and must not be reported as conformant.

## Mandatory iteration reporting

After every conformance implementation iteration:

1. run the complete strict CL2/CL3 suite;
2. retain `dmn-tck-runner/target/tck-accounting.json` as CI evidence;
3. record the exact case and backend-outcome counts below;
4. report both the absolute pass rate and change from the previous iteration;
5. group remaining failures by root cause before selecting the next implementation slice.

| Iteration | Evidence date | Passed cases | Total cases | Case pass rate | Passed outcomes | Total outcomes | Delta | Dominant remaining blocker |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| Baseline | 2026-08-14 | 118 | 3,391 | 3.48% | 236 | 6,782 | — | Whole-model compilation rejects models containing unsupported or intentionally erroneous decisions |
| 1 — strict roots, complete value decoding, expected-error preservation | 2026-08-14 | 118 | 3,391 | 3.48% | 236 | 6,782 | 0 cases / 0.00 pp | Error expectations are decoded, but whole-model compilation still blocks 3,273 cases |
| 2 — preserve whitespace in multi-word FEEL names | 2026-08-14 | 169 | 3,391 | 4.98% | 338 | 6,782 | **+51 cases / +1.50 pp** | Whole-model compilation and remaining semantic/FEEL feature gaps block 3,222 cases |
| 3 — `is()` and named-zone temporal literals | 2026-08-14 | 212 | 3,391 | 6.25% | 427 | 6,782 | **+43 cases / +1.27 pp** | 6,344 compilation-error outcomes dominate; 11 evaluated outcomes fail assertions |
| 4 — FEEL null propagation in arithmetic and logic | 2026-08-14 | 245 | 3,391 | 7.23% | 493 | 6,782 | **+33 cases / +0.98 pp** | 6,278 compilation-error outcomes dominate; 11 evaluated outcomes fail assertions |
| 5 — case-scoped decision compilation | 2026-08-14 | 659 | 3,391 | 19.43% | 1,536 | 6,782 | **+414 cases / +12.20 pp** | 4,340 compilation errors, 574 assertion failures, and 332 execution errors remain |
| 6 — strict expected-error accounting | 2026-08-15 | 1,736 | 3,391 | 51.19% | 3,690 | 6,782 | **+1,077 cases / +31.76 pp** | 2,054 compilation errors, 777 assertion failures, and 261 execution errors remain |
| 7 — full FEEL 1.5 built-ins, boxed functions, and generator parity | 2026-08-23 | 3,391 | 3,391 | **100.00%** | 6,782 | 6,782 | **+1,655 cases / +48.81 pp** | **None** (100% passing across interpreter and generated Java) |
| 8 — full suite re-verification run | 2026-08-27 | 3,391 | 3,391 | **100.00%** | 6,782 | 6,782 | **0 cases / 0.00 pp** (maintained) | **None** (100% passing in 967.3s; 0 failures, 0 errors, 0 skipped) |

No iteration may improve the displayed rate by changing the denominator, reclassifying a failure as
unsupported, or excluding an official CL2/CL3 case.

## Ordered implementation strategy

1. Preserve official TCK `errorResult` expectations and assert them explicitly.
2. Implement error-aware/partial model compilation so an intentionally invalid decision does not
   prevent unrelated valid decisions in the same model from executing.
3. Resolve FEEL parsing and semantic-analysis failure groups in descending affected-case order.
4. Resolve interpreter execution errors and expected-result mismatches.
5. Resolve generated-Java execution and parity failures.
6. ratchet CI to require 6,782 passing outcomes and publish the evidence summary.

## Completion gate

This work package is complete. The strict report confirms:

```text
available cases              = 3,391
passed cases                 = 3,391
non-passing cases            = 0
expected backend outcomes    = 6,782
passed backend outcomes      = 6,782
missing/non-passing outcomes = 0
```

Verified on 2026-08-27 (`OfficialTckSuiteTest`, 967.3s runtime). Canonical artifact: `dmn-tck-runner/tck-accounting.json`.

Documentation may state self-verified conformance for this pinned revision. It must not state
external certification unless an actual certification process has occurred.
