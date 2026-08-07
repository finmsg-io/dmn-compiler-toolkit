# `dmn-feel-parser`

ANTLR4 grammar, AST builder, and depth-first whole-model FEEL parsing pass module.

## Key Assets

- **`FeelLexer.g4` & `FeelParser.g4`**: Complete DMN 1.5 FEEL expression grammar.
- **`FeelAstBuilder`**: Transforms ANTLR parse trees into immutable Protobuf `Feel` AST nodes with zero ANTLR type leakage.
- **`DmnFeelParser`**: Model-aware depth-first pass parsing expressions across all DRG elements, decision tables, BKMs, and boxed logic with multi-error syntax diagnostics.

## Usage

```java
Definitions parsedModel = new DmnFeelParser().parse(rawModel);
```
