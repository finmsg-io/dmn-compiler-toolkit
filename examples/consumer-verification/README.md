# External Consumer Verification Example

This standalone project verifies that an external Maven consumer without reactor knowledge can:
1. Resolve `dmn-compiler` and `dmn-generator-java` artifacts from Maven repositories.
2. Ingest and compile a standard DMN 1.5 XML model.
3. Evaluate decisions dynamically via `DmnCompiledModel`.
4. Generate standalone, reflection-free Java source code via `DmnJavaGenerator`.
