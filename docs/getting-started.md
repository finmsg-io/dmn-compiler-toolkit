# Getting started

## Requirements

- JDK 25
- Maven 3.9 or later
- Git

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

## Build individual stages

```bash
mvn -pl dmn-frontend-xml -am test
mvn -pl dmn-feel-parser -am test -Pgenerate-code
mvn -pl dmn-semantic-analysis -am test -Pgenerate-code
```

## Use the implemented pipeline

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

## Run documentation locally

```bash
pip install mkdocs mkdocs-material
mkdocs serve
```

Open `http://127.0.0.1:8000`.

