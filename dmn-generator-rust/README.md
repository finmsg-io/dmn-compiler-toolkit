# DMNIR Rust generator

This prototype consumes DMNIR 0.0 scalar artifacts, scalar expressions from
DMNIR 0.1, named records and field access from DMNIR 0.2, local contexts
from DMNIR 0.3, civil dates from DMNIR 0.4, and decision tables from DMNIR 0.5. It verifies the wire
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
`tests/fixtures/ExportArithmeticFixture.java`, `tests/fixtures/ExportMultiplicationFixture.java`,
`tests/fixtures/ExportDivisionFixture.java`, and `tests/fixtures/ExportRecordFixture.java`
regenerate the artifacts and check
the expected values against the Java adapter.
Run each in Java source-file mode with the `dmn-compiler` Maven test classpath and
the fixture output path as the first argument. The arithmetic exporter accepts
a second output path for the historical multiplication fixture. The multiplication
exporter requires a second output path for the historical division fixture. The
division exporter takes the artifact path, a TSV path for the expected Java
vectors, and a path for the unsupported power artifact.

The `Rust artifact parity` GitHub Actions workflow runs this test in a pinned
Rust container. It checks out the generator, fixture and schema, checks that
Java is absent, and runs Cargo offline after fetching the locked dependencies.

The verifier accepts scalar literals and resolved scalar references in bundled models.
Schema 0.1 additionally admits unary plus, negation, `not`, equality and inequality,
numeric and string ordering, `and`, `or`, conditionals, and numeric addition,
subtraction, multiplication, and division. Addition and subtraction use the greater
operand scale. Multiplication adds the operand scales. Division uses DECIMAL128
rounding and returns null for a zero divisor. All four propagate nulls.
The verifier limits additive chains to 128 operations across expressions and
decision dependencies. It rejects graphs that could exceed 32,768 coefficient
digits, absolute scale 8,192 for multiplication, or 8,388,608 base-10⁹ limb products.
Division has a separate 8,388,608-limb-visit budget and permits division-derived
scales through 32,768. Powers and profiles above 0.5 remain outside the subset.

Schema 0.2 adds `Value::Record(BTreeMap<String, Value>)` for nested record inputs
and results. All declared fields must be present with the expected types.
Explicit nulls are accepted, and additional fields are preserved. Invalid
typed inputs return an evaluation error. Record types retain their qualified
identities during verification. Recursive types and expanded layouts above
50,000 nodes or 32 levels are rejected before generation.

Schema 0.3 adds sequential typed local bindings and a final context result.
Bindings use decision-local identities and lexical visibility. Nested contexts
may shadow names while retaining references to visible outer bindings.
Forward references, escaped locals, duplicate binding IDs and incompatible types
are rejected. Local numeric values retain arithmetic bounds. Generated contexts
evaluate each binding once in order using Rust block expressions.
`tests/fixtures/ExportContextFixture.java` verifies and wraps the record artifact
in contexts, then checks Java parity. Pass the record artifact and context output
paths as arguments using the Java test classpath described above.

Schema 0.4 adds `Value::Date { year: i32, month: u32, day: u32 }` for civil
dates, including date fields, locals, conditionals, equality and ordering.
The proleptic Gregorian calendar permits years -999,999,999 through 999,999,999,
excluding zero. Invalid calendar dates and string-valued date inputs are rejected.
No timezone or clock conversion occurs. Time-of-day, offsets and durations are
outside this profile. The generated crate remains dependency-free.
`tests/fixtures/ExportDateFixture.java` produces the date artifact and Java
expected TSV vectors. Its arguments are the artifact and TSV output paths.

Schema 0.5 supports UNIQUE, FIRST and ANY decision tables with scalar or named
record results. Inputs are evaluated once. Rules use scalar comparisons, ranges,
alternatives, negation and wildcards. All matching rows are evaluated in order,
as in the Java adapter. UNIQUE rejects multiple matches, and ANY rejects unequal
results using numeric value equality across decimal scales. FIRST returns the
first match. No matches use each output's default expression or null, including
null-valued fields in record results. Policy conflicts return an evaluation error.
The verifier checks column counts, result shape, IDs, types, dependencies and
all cells, including inactive rules and defaults. Context expressions are rejected
inside table cells. Scalar output bounds propagate to later decisions.
`tests/fixtures/ExportTableFixture.java` writes table artifacts and Java vectors
to the directory supplied as its argument.
