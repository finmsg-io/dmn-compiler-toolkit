# ADR-0016 — Generated Java Package and Class Naming Policy

<!-- generated-toc:start -->
## Table of contents

- [Context](#contents-section-1)
- [Decision](#contents-section-2)
- [Consequences](#contents-section-3)
<!-- generated-toc:end -->

| Field | Value |
| --- | --- |
| Status | Accepted |
| Decision | ADR-0016 |

<a id="contents-section-1"></a>
## Context

Generated Java source code needs predictable package and class naming conventions to prevent collisions, support multi-model compilation, integrate cleanly into consumer build tools, and provide intuitive programmatic access.

---

<a id="contents-section-2"></a>
## Decision

1. **Default Package Structure**: By default, generated code uses `io.finmsg.dmn.generated.<model_identifier>`, where `<model_identifier>` is a sanitized, lowercased representation of the DMN model name or namespace.
2. **Class Naming**: Decision model classes default to PascalCase-sanitized model names (or `GeneratedDecisionModel` when unspecified). Each decision method corresponds directly to the sanitized decision name.
3. **Customization**: Consumers can explicitly override `packageName` and `className` via `DmnJavaGeneratorOptions`.
4. **Collision Prevention**: When decision names or identifiers contain characters outside standard Java identifier syntax (spaces, hyphens, special symbols), they are normalized deterministically using camelCase/PascalCase transforms and alphanumeric filtering.

---

<a id="contents-section-3"></a>
## Consequences

Advantages

* Deterministic, reproducible class and package generation.
* Clean separation of generated models across different namespaces.
+ Full configurability for host applications and build plugins.

Trade-offs

* Consumers embedding multiple versions of the same model must provide distinct package/class options or namespace mappings.
