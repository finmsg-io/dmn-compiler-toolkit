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

## Next: semantic analysis

- [ ] Persist or expose resolved symbol bindings
- [ ] Resolve named type references comprehensively
- [ ] Infer expression types
- [ ] Validate operators and function calls
- [ ] Validate decision tables
- [ ] Build the DRG dependency graph
- [ ] Detect dependency cycles
- [ ] Validate imports and cross-model references

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

