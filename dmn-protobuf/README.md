# `dmn-protobuf`

The canonical, dependency-neutral Protobuf semantic model contract module of the DMN Compiler Toolkit.

## Key Assets

- **Proto Schemas**: `common.proto`, `core.proto`, `types.proto`, `feel_text.proto`, `feel_parsed.proto`, `feel.proto`, `decision_table.proto`, `drg.proto`, `model.proto`.
- **Replaceable Text/Parsed Nodes**: `feel.proto` defines `Feel`, `ExpressionNode`, and `BoxedExpression` using `oneof` fields to allow text-to-AST transformation without mutating object identity.
- **Zero Reverse Dependencies**: Pure semantic message contracts without dependencies on XML parsers, ANTLR grammars, or execution runtimes.

## Usage

```java
import io.finmsg.dmn.model.Definitions;
```
