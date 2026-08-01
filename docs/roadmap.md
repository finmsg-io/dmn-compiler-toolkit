# Roadmap

## Completed foundation

- [x] Protobuf semantic model
- [x] FEEL text and parsed schemas without reverse dependency
- [x] Replaceable FEEL, expression, boxed-expression, unary-test, and type-constraint nodes
- [x] VTD-XML DMN reader
- [x] Initial DMN XML writer infrastructure and writer tests
- [x] ANTLR4 FEEL grammar and generated parser
- [x] Protobuf FEEL AST builder
- [x] Depth-first `DmnFeelParser` pass
- [x] Model-aware, multi-error FEEL diagnostics
- [x] Traffic Violation XML-to-FEEL integration test
- [x] `dmn-semantic-analysis` module
- [x] First FEEL name/property-resolution pass
- [x] Traffic Violation semantic-analysis test
- [x] Unified semantic-analysis pipeline
- [x] Named-type resolution and declared-type validation
- [x] FEEL expression and boxed-expression type inference
- [x] Operator and built-in function validation
- [x] Decision-table, BKM, item-definition, and decision-service validation
- [x] DRG dependency analysis, cycle detection, and deterministic compilation order
- [x] Exposed resolved symbol and named-type bindings

## Next: semantic-analysis completion

- [ ] Validate imports and cross-model references
- [ ] Close remaining DMN 1.5 expression and validation edge cases
- [ ] Stabilize the semantic pipeline as an input to Runtime IR

## XML frontend completion

- [ ] Complete invocation coverage
- [ ] Complete writer coverage and round-trip tests
- [ ] Populate source locations
- [ ] Preserve documentation and extension elements
- [ ] Complete imports and decision services
- [ ] Add malformed and hostile XML tests

## Later stages

- [ ] Runtime IR
- [ ] Constant folding and expression simplification
- [ ] Java code generation
- [ ] Runtime activation
- [ ] Public compiler API and CLI
- [ ] JMH performance benchmarks
