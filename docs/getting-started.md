# Getting started

<!-- generated-toc:start -->
## Table of contents

- [Requirements](#contents-section-1)
- [Clone and build](#contents-section-2)
- [Build individual stages](#contents-section-3)
- [Use the implemented pipeline](#contents-section-4)
- [Run documentation locally](#contents-section-5)
<!-- generated-toc:end -->


<a id="contents-section-1"></a>
## Requirements

- JDK 25
- Maven 3.9 or later
- Git

<a id="contents-section-2"></a>
## Clone and build

```bash
git clone https://github.com/finmsg-io/dmn-compiler-toolkit.git
cd dmn-compiler-toolkit
mvn clean test -Pgenerate-code
```

The `generate-code` profile regenerates ANTLR sources below:

```text
dmn-feel-parser/src/gen/java
```

<a id="contents-section-3"></a>
## Build individual stages

```bash
mvn -pl dmn-frontend-xml -am test
mvn -pl dmn-feel-parser -am test -Pgenerate-code
mvn -pl dmn-semantic-analysis -am test -Pgenerate-code
mvn -pl dmn-compiler -am test -Pgenerate-code
mvn -pl dmn-generator-java -am test
mvn -pl dmn-tck-runner -am test
```

<a id="contents-section-4"></a>
## Use the implemented pipeline

### Primary One-Call Compiler Facade API

The `dmn-compiler` module provides a high-level facade for loading and compiling single models or transitive model sets:

```java
import io.finmsg.dmn.compiler.*;
import io.finmsg.dmn.runtime.DmnInterpreter;
import io.finmsg.dmn.generator.java.DmnJavaGenerator;
import java.path.Path;
import java.util.Map;

// Configure compiler and model resolver
DmnCompiler compiler = DmnCompiler.create();

// Compile root model and transitive imports
DmnCompilationResult result = compiler.compile(Path.of("model.dmn"));

if (!result.isSuccess()) {
    result.diagnostics().forEach(d -> System.err.println(d.formattedMessage()));
    return;
}

DmnCompiledModel compiledModel = result.compiledModel();

// Evaluate with interpreter
Map<String, Object> inputs = Map.of("Applicant Age", 25, "Credit Score", 720);
Object decisionOutput = DmnInterpreter.evaluate(compiledModel, "Eligibility", inputs);

// Or generate high-performance Java code
String javaSource = new DmnJavaGenerator().generate(compiledModel.runtimeModel());
```

### Low-Level Stage Pipeline API

You can also invoke individual compiler passes directly:

```java
Definitions semanticModel = new DmnXmlReader().read(Path.of("model.dmn"));

DmnFeelParseResult feelResult =
    new DmnFeelParser().parseWithDiagnostics(semanticModel);

if (!feelResult.isSuccess()) {
  feelResult.diagnostics().forEach(System.out::println);
  return;
}

DmnSemanticAnalysisResult semanticResult =
    new DmnSemanticAnalyzer().analyze(feelResult.model());

semanticResult.diagnostics().forEach(System.out::println);
```

The strict FEEL API is also available:

```java
Definitions parsedModel = new DmnFeelParser().parse(semanticModel);
```

It throws `DmnFeelParseException` after collecting all syntax errors.

<a id="contents-section-5"></a>
## Run documentation locally

```bash
pip install mkdocs mkdocs-material
mkdocs serve
```

Open `http://127.0.0.1:8000`.

