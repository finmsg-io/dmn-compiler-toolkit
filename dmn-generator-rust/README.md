# DMNIR Rust generator

This prototype consumes DMNIR 0.0 scalar artifacts. It verifies the wire and model
graph, then writes a standalone Rust library crate. The artifact is the only model
input to the generator.

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

The parity test runs the generator twice on the checked-in Java-produced artifact,
compares the generated files, and builds and executes one generated crate offline.
`tests/fixtures/ExportFixture.java` regenerates the artifact and checks the expected
values against the Java adapter. Run it in Java source-file mode with the
`dmn-compiler` Maven test classpath and the fixture output path as its only argument.

The verifier accepts scalar literals and resolved scalar references in bundled models.
Later DMNIR profiles and their additional operations are outside this first slice.
