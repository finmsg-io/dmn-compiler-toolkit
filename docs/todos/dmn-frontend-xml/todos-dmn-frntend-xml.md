Known gaps in `dmn-frontend-xml`:

1. **Invocation parsing**

    * `InvocationReader` is still incomplete.
    * Binding names and binding expressions need full support.

2. **ItemDefinition completeness**

    * Nested `itemComponent` structures are not fully implemented.
    * `allowedValues` / type constraints are incomplete.
    * More complete handling of `typeRef`, collection types, and nested components is needed.

3. **Boxed expressions**

    * Context, relation, list, and function-definition structures exist, but need broader DMN coverage and validation.
    * Some boxed-expression variants may still be represented only partially.

4. **Decision table completeness**

    * `defaultOutputEntry` needs complete support.
    * Input/output clause attributes may not all be mapped.
    * Annotation handling is minimal.
    * Hit-policy aggregation combinations need validation.

5. **FEEL handling**

    * The frontend only creates the `text` branch.
    * FEEL parsing and population of the `parsed` branch belong to the later parser/compiler stage.
    * Unary tests and expressions are not validated by the XML frontend.

6. **Imports**

    * DMN `<import>` parsing is basic.
    * Import type, location URI, namespace resolution, and imported-model linking are not complete.

7. **Decision requirements**

    * Information, knowledge, and authority requirements are read, but reference resolution is not performed.
    * `href` targets are not yet linked to actual model elements.

8. **Decision services**

    * Full parsing of output decisions, encapsulated decisions, and input data/decisions needs verification or completion.

9. **Business knowledge models**

    * Encapsulated logic and parameter handling need broader test coverage.
    * Function definitions are not yet semantically validated.

10. **Knowledge sources**

    * Authority and ownership references may not be fully mapped or resolved.

11. **Extension elements**

    * `extensionElements` are not fully preserved.
    * Vendor-specific XML content is likely ignored or only partially captured.

12. **Documentation and mixed content**

    * XML documentation content may lose formatting or nested markup.
    * Whitespace handling needs explicit tests.

13. **Namespace handling**

    * Multiple DMN versions need broader verification.
    * Namespace-prefix variations should be tested.
    * Foreign namespaces need predictable handling.

14. **Source locations**

    * Line and column information is not fully populated.
    * Precise diagnostics therefore remain limited.

15. **Validation**

    * Required attributes and child elements are not comprehensively validated.
    * Duplicate IDs, invalid references, and structural errors are not detected consistently.

16. **Unsupported elements**

    * Unsupported DMN elements currently need a consistent policy:

        * fail immediately;
        * preserve them;
        * or emit diagnostics and continue.

17. **Testing**

    * More unit tests are needed for every reader.
    * Missing negative tests for malformed XML.
    * Missing round-trip or reference-model comparison tests.
    * More DMN conformance examples should be added.

18. **Reader registry**

    * Shared readers must remain stateless.
    * This constraint is currently architectural rather than enforced.
    * Mutable fields added later could introduce thread-safety problems.

19. **Performance tests**

    * No benchmarks yet for large DMN files.
    * No memory-allocation or deep-recursion tests.

20. **XML security**

    * External entities, DTD handling, and hostile XML inputs need explicit verification and tests.

The highest-priority next gaps are:

```text
1. InvocationReader
2. ItemDefinition components and allowedValues
3. Decision-table defaultOutputEntry
4. reference resolution
5. reader tests
```
