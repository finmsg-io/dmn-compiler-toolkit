# Getting started

## Requirements

- JDK 25
- Maven 3.9 or later
- Git

Verify the environment:

```bash
java -version
mvn -version
```

## Clone the repository

```bash
git clone https://github.com/finmsg-io/dmn-compiler-toolkit.git
cd dmn-compiler-toolkit
```

## Build all modules

```bash
mvn clean verify
```

## Build one module

```bash
mvn -pl dmn-frontend-xml -am test
```

The `-am` option builds required dependencies from the same reactor.

## Generate FEEL parser sources

```bash
mvn -Pgenerate-code clean verify
```

Generated parser sources are written below:

```text
dmn-feel-parser/src/gen/java
```

## Run documentation locally

Install the documentation tools:

```bash
pip install mkdocs mkdocs-material
```

Start the local server:

```bash
mkdocs serve
```

Open:

```text
http://127.0.0.1:8000
```
