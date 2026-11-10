# DMNIR Rust generator

This prototype consumes DMNIR 0.0 scalar artifacts and the comparison,
conditional, and additive arithmetic subset of DMNIR 0.1. It verifies the wire
and model graph, then writes a standalone Rust library crate. The artifact is
the only model input to the generator.

From this directory, use the pinned Rust 1.99.1 toolchain.

```console
cargo test --locked --offline
cargo run --locked --offline -- tests/fixtures/scalar-0.0.dmnir target/generated-example
cargo test --offline --manifest-path target/generated-example/Cargo.toml
```

Run `cargo fetch --locked` once to populate a fresh dependency cache. The generator
compiles bindings from the toolkit's DMNIR schema using a pinned vendored Protobuf
compiler. The generated crate uses only the Rust standard library. Its output directory
must be new.

Call `generated_dmn::evaluate` with a `BTreeMap` keyed by model namespace and input
element ID. Provide every declared input, including explicit `Value::Null`. Decision
results use the same namespace and element ID convention. Numeric values preserve
the decimal coefficient string and signed scale, with no floating-point conversion.

The parity tests run the generator twice on each checked-in Java-produced artifact,
compare the generated files, and build and execute the generated crates offline.
`tests/fixtures/ExportFixture.java`, `tests/fixtures/ExportExpressionFixture.java`,
and `tests/fixtures/ExportArithmeticFixture.java` regenerate the artifacts and
check the expected values against the Java adapter.
Run each in Java source-file mode with the `dmn-compiler` Maven test classpath and
the fixture output path as the first argument. The arithmetic exporter accepts
a second output path for its unsupported multiplication fixture.

The `Rust artifact parity` GitHub Actions workflow runs this test in a pinned
Rust container. It checks out the generator, fixture and schema, checks that
Java is absent, and runs Cargo offline after fetching the locked dependencies.

The verifier accepts scalar literals and resolved scalar references in bundled models.
Schema 0.1 additionally admits unary plus, negation, `not`, equality and inequality,
numeric and string ordering, `and`, `or`, conditionals, and numeric addition and
subtraction. Decimal arithmetic preserves the greater operand scale and propagates
nulls. The verifier rejects chains longer than 128 additions or subtractions
across expression and decision dependencies. Multiplication, division, powers,
and later DMNIR profiles remain outside the supported subset.
