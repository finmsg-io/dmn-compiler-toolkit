# Authoring DSL (`.decision`)

The `.decision` authoring DSL (`dmn-frontend-dsl`) provides a concise, Git-friendly textual syntax for authoring DMN models. It is parsed by an ANTLR4 grammar (`DecisionDsl.g4`) and lowered into the exact same DMN 1.6 `Definitions` protobuf model as `dmn-frontend-xml`.

Because both frontends produce the same canonical protobuf model, `.decision` sources flow directly through:

- `DmnCompiler`, `DmnFeelParser`, `DmnModelSetSemanticAnalyzer`, and `DmnOptimizer`
- Process-local `DmnRuntime` and AOT Java, Rust, Spark SQL, and gRPC backends
- Portable `.dmnir` binary artifacts (`DmnIrScalarProducer`)
- Conforming DMN 1.6, DMN 1.5, and DMN 1.3 XML export (`DmnIrScalarDmnExporter`)

See the [DMNIR guide](dmnir.md) for portable artifact details and the [modules page](modules.md#dmn-frontend-dsl) for module boundaries.

## Quick example

A multi-file model consists of a reusable library (`rates.decision`) and a policy model (`loan-underwriting.decision`) with inline tests. Both files are checked in under `dmn-frontend-dsl/src/test/resources/examples/`.

### `rates.decision`

```text
model "Rates Library"
namespace "urn:example:dsl:rates"
id "rates-lib"

bkm MonthlyRate id "bkm_MonthlyRate"(annualRate: number): number
  = annualRate / 12
```

### `loan-underwriting.decision`

```text
model "Loan Underwriting"
namespace "urn:example:dsl:policy"
id "loan-underwriting"

import Rates from "rates.decision" namespace "urn:example:dsl:rates"

type tApplicant {
  Age: number constraint >= 0
  Score: number
}

type tScores list<number>

input Applicant: tApplicant
input Principal: number
input AnnualRate: number

decision RiskTier: string
  requires Applicant
  table first {
    when Applicant.Score: number | Applicant.Age: number => result: string default "HIGH"
    | >= 700 | >= 21 | => "LOW"
    | [600 .. 699] | >= 18 | => "MEDIUM"
    | - | - | => "HIGH"
  }

decision MonthlyInterest: number
  requires Principal, AnnualRate
  uses Rates.MonthlyRate#bkm_MonthlyRate
  = Principal * Rates.MonthlyRate(AnnualRate)

decision Approved: boolean
  requires RiskTier, MonthlyInterest
  = RiskTier != "HIGH" and MonthlyInterest <= 1000

test "Low risk approved" {
  given Applicant = { Age: 30, Score: 750 }
  given Principal = 120000
  given AnnualRate = 0.06
  expect RiskTier = "LOW"
  expect MonthlyInterest = 600
  expect Approved = true
}

test "High interest declined" {
  given Applicant = { Age: 25, Score: 650 }
  given Principal = 300000
  given AnnualRate = 0.06
  expect RiskTier = "MEDIUM"
  expect Approved = false
}
```

## Language reference

The outer syntax is defined in `dmn-frontend-dsl/src/main/antlr4/io/finmsg/dmn/frontend/dsl/grammar/DecisionDsl.g4`. Embedded FEEL expressions and unary tests are preserved verbatim and parsed by `dmn-feel-parser`.

| Construct | Syntax | Notes |
| --- | --- | --- |
| Model header | `model "Name"` / `namespace "urn:..."` / `id "model-id"` | `model` and `namespace` set DMN `Definitions` identity; `id` is optional. |
| Import | `import Alias from "file.decision" namespace "urn:..."` | Resolves relative `.decision` or `.dmn` models via `DmnModelResolver`. |
| Record type | `type tName { Field: type [constraint <unaryTest>] }` | Lowers to DMN `itemDefinition` with `itemComponent` fields. |
| List type | `type tName list<T>` | Lowers to `itemDefinition` with `isCollection="true"`. |
| Alias type | `type tName = Base [constraint <unaryTest>]` | Constrained or aliased scalar/named `itemDefinition`. |
| Input data | `input Name [id "..."]: Type` | Declares a DMN `inputData` element. |
| BKM | `bkm Name [id "..."](p1: T1, p2: T2): Ret [uses ...] = <feel>` | Declares a `businessKnowledgeModel` with formal parameters. |
| Expression decision | `decision Name [id "..."]: Type [requires ...] [uses ...] = <feel>` | Single- or multi-line FEEL expression body. |
| Decision table | `decision Name: Type [requires ...] table <hitPolicy> { when ... => ... \| ... => ... }` | Supports standard DMN hit policies (`unique`, `first`, `priority`, `any`, `collect`, `collect sum`, `collect min`, `collect max`, `collect count`, `rule order`, `output order`) and optional `default <expr>`. |
| Inline test | `test "Description" { given Input = <feel> ... expect Decision = <feel> ... }` | Executed by `DmnDslTestRunner` / CLI `test`; **excluded from lowered DMN and `.dmnir` production payloads**. |
| Comments | `// line comment` and `/* block comment */` | Ignored by the parser. |

### Inline tests and production artifacts

Inline `test` blocks are first-class in the `.decision` AST so authors can keep executable examples next to their decisions. During lowering (`DmnDslLowerer`), inline `test` blocks are stripped completely — compiling a `.decision` file with or without `test` blocks produces **byte-identical** `.dmnir` artifacts.

### Bounded parsing and diagnostics

`DmnDslReadOptions` enforces bounded input size (`maxBytes`) and declaration count (`maxDeclarations`). Syntax and structural errors throw `DmnDslParseException` with `line:column` source positions (`DmnDslDiagnostic`).

## Runnable CLI

The `dmn-compiler` module packages a standalone runnable CLI JAR (`dmn-compiler-<version>-cli.jar`, main class `io.finmsg.dmn.compiler.cli.DmnDslCli`) alongside the standard thin library JAR.

### Build the CLI JAR

```bash
mvn package -pl dmn-compiler -am -DskipTests
```

This produces `dmn-compiler/target/dmn-compiler-1.1.0-cli.jar` without modifying the thin library artifact `dmn-compiler-1.1.0.jar`.

### CLI commands

```bash
CLI="dmn-compiler/target/dmn-compiler-1.1.0-cli.jar"

# 1. Parse, resolve imports, and compile
java -jar "$CLI" check dmn-frontend-dsl/src/test/resources/examples/loan-underwriting.decision

# 2. Format canonically (stdout, or in-place with --write)
java -jar "$CLI" format dmn-frontend-dsl/src/test/resources/examples/loan-underwriting.decision
java -jar "$CLI" format path/to/model.decision --write

# 3. Execute inline test blocks
java -jar "$CLI" test dmn-frontend-dsl/src/test/resources/examples/loan-underwriting.decision

# 4. Compile to a portable .dmnir binary artifact
java -jar "$CLI" compile dmn-frontend-dsl/src/test/resources/examples/loan-underwriting.decision --out build/loan.dmnir

# 5. Export conforming DMN XML (1.6 default, or 1.5 / 1.3) from .decision or .dmnir
java -jar "$CLI" export build/loan.dmnir --out build/dmn-xml --dmn-version 1.6
java -jar "$CLI" export dmn-frontend-dsl/src/test/resources/examples/loan-underwriting.decision --out build/dmn-1.5 --dmn-version 1.5
```

### Exit codes

| Exit code | Meaning |
| --- | --- |
| `0` | Success (all checks, tests, compilation, or export steps passed). |
| `1` | Parse, semantic, compilation, inline test, or export failure. |
| `2` | CLI usage or argument error. |

## Java API usage

### Parse, format, and compile

```java
import io.finmsg.dmn.compiler.*;
import io.finmsg.dmn.frontend.dsl.DmnDslReader;
import java.nio.file.Path;

// Parse and canonically format
DmnDslReader reader = new DmnDslReader();
String canonical = reader.format(dslSource);

// Compile directly through DmnCompiler (auto-detects .decision and .dmn)
Path entry = Path.of("loan-underwriting.decision").toAbsolutePath();
DmnSource source = new DmnSource(DmnSourceId.of(entry.toUri().toString()), java.nio.file.Files.readAllBytes(entry));
DmnCompilationResult result = new DmnCompiler()
    .compile(source, new FilesystemDmnModelResolver(entry.getParent()), DmnCompilerOptions.optimized());
```

### Run inline tests programmatically

```java
import io.finmsg.dmn.compiler.cli.DmnDslTestRunner;

DmnDslTestRunner.TestReport report = new DmnDslTestRunner()
    .run(source, new FilesystemDmnModelResolver(entry.getParent()));
if (!report.allPassed()) {
    throw new IllegalStateException("Failed tests: " + report.failed());
}
```

## IDE and editor integration

`DecisionDsl.g4` is maintained as the single source of truth for both compiler parsing and editor tooling:

- **IntelliJ IDEA**: reuse `DecisionDsl.g4` directly with `antlr4-intellij-adaptor` for lexing, PSI tree construction, syntax highlighting, and live diagnostics.
- **VS Code**: pair a TextMate grammar or ANTLR4 TypeScript target with a Java Language Server (LSP) backed by `DmnDslReader`, `DmnDslFormatter`, and `DmnCompiler` for completions, hover types, formatting, and semantic diagnostics.
