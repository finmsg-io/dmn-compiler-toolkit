Architecturally, `dmn-frontend-xml` has a sound overall direction but needs stronger namespace correctness, diagnostics, encapsulation, and test coverage before it should be treated as a robust compiler frontend.

## Current architecture

```text
Path / InputStream / byte[]
          │
          ▼
     DmnXmlReader
          │
          ▼
      XmlCursor
          │
          ▼
    VtdXmlCursor ──────► VTD-XML
          │
          ▼
 element-specific readers
          │
          ▼
 protobuf Definitions
```

The writer follows the reverse path:

```text
protobuf Definitions
          │
          ▼
 element-specific writers
          │
          ▼
      XmlEmitter
          │
          ▼
       DMN XML
```

The module dependency direction is correct:

```text
dmn-frontend-xml
   ├── dmn-protobuf
   ├── vtd-xml
   └── slf4j-api
```

It does not depend on FEEL parsing, semantic analysis, or later compiler stages.

## Findings

### 1. High: namespace resolution is document-global instead of cursor-scoped

[`VtdXmlCursor.loadNamespaces()`](C:/00-finmsg.io/dmn-compiler-toolkit/dmn-frontend-xml/src/main/java/io/finmsg/dmn/frontend/xml/vtd/VtdXmlCursor.java:200) scans every namespace declaration in the document into one map.

This loses XML namespace scope. A nested element can redeclare the root prefix and overwrite the mapping used to detect the root DMN version. [`namespaceUri()`](C:/00-finmsg.io/dmn-compiler-toolkit/dmn-frontend-xml/src/main/java/io/finmsg/dmn/frontend/xml/vtd/VtdXmlCursor.java:232) then returns the global map entry rather than the namespace applicable to the current element.

Recommended direction: make namespace lookup cursor-relative using the XML engine’s namespace APIs. Do not maintain a flattened namespace map.

### 2. High: readers dispatch only by local name

Most reader switches use `cursor.localName()`, for example [`DefinitionsBodyReader`](C:/00-finmsg.io/dmn-compiler-toolkit/dmn-frontend-xml/src/main/java/io/finmsg/dmn/frontend/xml/dmn/reader/DefinitionsBodyReader.java:31).

Consequently, an extension element such as:

```xml
<vendor:decision/>
```

could be interpreted as a DMN decision if it appears in the expected structural position.

The architecture should distinguish:

```text
(namespace URI, local name)
```

instead of using only `local name`. Extension namespaces should be ignored or preserved, never parsed as DMN accidentally.

### 3. High: source-location support is effectively absent

[`VtdXmlCursor.line()` and `column()`](C:/00-finmsg.io/dmn-compiler-toolkit/dmn-frontend-xml/src/main/java/io/finmsg/dmn/frontend/xml/vtd/VtdXmlCursor.java:315) always return `-1`, while [`path()`](C:/00-finmsg.io/dmn-compiler-toolkit/dmn-frontend-xml/src/main/java/io/finmsg/dmn/frontend/xml/vtd/VtdXmlCursor.java:333) only returns the current element name.

[`NodeReader`](C:/00-finmsg.io/dmn-compiler-toolkit/dmn-frontend-xml/src/main/java/io/finmsg/dmn/frontend/xml/dmn/reader/NodeReader.java:24) does not populate `SourceLocation`.

This weakens every later compiler diagnostic. The frontend is the only stage that can reliably associate semantic nodes with XML locations, so source-location capture belongs here.

Recommended representation:

```text
source URI
line
column
structural XML path
```

At minimum, construct an indexed path such as:

```text
/definitions/decision[2]/decisionTable/rule[4]/inputEntry[1]
```

### 4. Medium-high: the public API surface is too large

Nearly all element readers, writers, utilities, the registry, cursor implementation, and constants are public.

The intended external API appears to be only:

- `DmnXmlReader`
- `DmnWriter`
- possibly frontend configuration and result types

Classes such as `DecisionReader`, `NodeReader`, `ReaderRegistry`, and `DefinitionsWriter` are implementation details. Exposing them makes future restructuring a breaking API change.

Recommended package structure:

```text
io.finmsg.dmn.frontend.xml
    DmnXmlReader
    DmnWriter
    DmnReadResult
    DmnReadOptions

io.finmsg.dmn.frontend.xml.internal
    cursor/
    reader/
    writer/
    diagnostics/
```

Use package-private visibility for internal readers and writers.

### 5. Medium-high: tolerant parsing silently loses information

Many readers ignore unknown children, including [`DecisionReader`](C:/00-finmsg.io/dmn-compiler-toolkit/dmn-frontend-xml/src/main/java/io/finmsg/dmn/frontend/xml/dmn/reader/DecisionReader.java:90) and [`DecisionTableReader`](C:/00-finmsg.io/dmn-compiler-toolkit/dmn-frontend-xml/src/main/java/io/finmsg/dmn/frontend/xml/dmn/reader/DecisionTableReader.java:42).

Tolerance is reasonable, but silent loss is dangerous for tooling and round trips. The caller cannot distinguish:

```text
fully understood document
```

from:

```text
partially parsed document with discarded content
```

Introduce explicit modes:

```java
STRICT
COMPATIBLE
LOSSLESS
```

Unknown standard DMN elements should produce diagnostics. Extension elements should either be preserved or reported as intentionally skipped.

### 6. Medium: reader results cannot carry recoverable diagnostics

The only reader result is `Definitions`; failures are exception-based. Unknown enum values are sometimes silently mapped to `UNSPECIFIED`, while other unsupported structures throw `IllegalArgumentException`.

This is inconsistent for a compiler frontend.

A better boundary would be:

```java
record DmnReadResult(
    Definitions model,
    List<DmnXmlDiagnostic> diagnostics
) {}
```

Reserve exceptions for unreadable or structurally unusable XML. Report unsupported elements, invalid attributes, unknown enum values, and recoverable conformance problems as diagnostics.

### 7. Medium: version detection exists, but version-specific behavior does not

[`DmnVersionDetector`](C:/00-finmsg.io/dmn-compiler-toolkit/dmn-frontend-xml/src/main/java/io/finmsg/dmn/frontend/xml/dmn/DmnVersionDetector.java:8) produces a `DmnXmlContext`, but the readers are largely version-agnostic. [`DefinitionsBodyReader`](C:/00-finmsg.io/dmn-compiler-toolkit/dmn-frontend-xml/src/main/java/io/finmsg/dmn/frontend/xml/dmn/reader/DefinitionsBodyReader.java:10) stores the context without using it.

This creates the appearance of multi-version support without an enforceable version policy.

Choose one explicit model:

- Normalize supported DMN versions into one canonical protobuf model, with version-specific mappings; or
- Support only DMN 1.5 and reject other versions clearly.

### 8. Medium: input APIs buffer the complete document

[`VtdXmlCursor(InputStream)`](C:/00-finmsg.io/dmn-compiler-toolkit/dmn-frontend-xml/src/main/java/io/finmsg/dmn/frontend/xml/vtd/VtdXmlCursor.java:26) calls `readAllBytes()`.

That may be inherent to VTD-XML, but the API can misleadingly appear streaming. It also lacks a configurable document-size limit, creating memory-exhaustion risk for untrusted input.

Document the buffering behavior and add configurable limits before advertising the frontend for hostile or large inputs.

### 9. Medium: reader and writer capabilities are asymmetric

The reader supports BKMs, knowledge sources, decision services, decision tables, invocation, contexts, relations, and lists. [`DrgElementWriter`](C:/00-finmsg.io/dmn-compiler-toolkit/dmn-frontend-xml/src/main/java/io/finmsg/dmn/frontend/xml/dmn/writer/DrgElementWriter.java:14) supports only input data and decisions.

Failing explicitly is preferable to silent loss, but the module needs a clear capability contract. Consider separate declarations:

```text
DmnReaderCapabilities
DmnWriterCapabilities
```

Round-trip safety should only be claimed for structures supported in both directions.

### 10. Medium: tests are too sparse for the module’s responsibility

There are only three direct test classes, and the basic reader test covers an almost-empty definitions document. There are no focused tests for:

- cursor navigation invariants
- prefixed and nested namespaces
- namespace collisions
- malformed XML
- unknown standard elements
- extension elements
- invalid attributes and enum values
- source locations
- resource-size limits
- every element reader
- partial-writer failures
- caller-owned stream lifecycle
- multi-version behavior

The Traffic Violation integration tests are useful, but they cannot replace focused frontend tests.

## Strengths

- Clean dependency direction toward protobuf contracts.
- VTD-XML is isolated behind [`XmlCursor`](C:/00-finmsg.io/dmn-compiler-toolkit/dmn-frontend-xml/src/main/java/io/finmsg/dmn/frontend/xml/XmlCursor.java).
- Element readers are generally small and responsibility-focused.
- Recursive FEEL-text readers are assembled centrally through [`ReaderRegistry`](C:/00-finmsg.io/dmn-compiler-toolkit/dmn-frontend-xml/src/main/java/io/finmsg/dmn/frontend/xml/dmn/reader/ReaderRegistry.java).
- The frontend correctly emits FEEL text rather than invoking the FEEL parser.
- Writer failures for unsupported DRG elements are explicit.

## Recommended refactoring order

1. Correct namespace resolution and namespace-aware dispatch.
2. Add cursor conformance tests around navigation and namespaces.
3. Populate source locations.
4. Introduce `DmnReadResult` with structured diagnostics.
5. Make element readers and writers internal.
6. Define strict/tolerant parsing policy.
7. Formalize DMN-version normalization.
8. Expand writer coverage or publish an explicit capability matrix.

Overall assessment: the module decomposition is good, but its XML abstraction is currently too weak to guarantee namespace-correct parsing, and the frontend boundary lacks the diagnostics required of a compiler-grade implementation.