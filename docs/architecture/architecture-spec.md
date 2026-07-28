# DMN Compiler Toolkit

## Architecture Specification

**Version 1.1 --- Implementation-Aligned Baseline**

------------------------------------------------------------------------

## Document Status

This version aligns the architecture specification with the
implementation available in July 2026.

The document distinguishes three states:

-   **IMPLEMENTED** --- present in the current codebase and exercised by
    the Traffic Violation DMN reader flow.
-   **PROVISIONAL** --- designed or partially represented in protobuf,
    but not yet implemented as a complete compiler stage.
-   **FUTURE** --- target architecture with no active implementation
    yet.

Current implementation baseline:

``` text
dmn-compiler-toolkit
│
├── dmn-protobuf
│   └── protobuf schemas and generated Java semantic-model classes
│
└── dmn-frontend-xml
    └── VTD-XML based DMN XML reader producing Definitions protobuf
```

The current executable pipeline is:

``` text
DMN XML
    |
    v
VTD-XML cursor
    |
    v
DMN element readers
    |
    v
protobuf Semantic Model

FEEL parsing, semantic analysis, optimization, Runtime IR, code generation,
and runtime execution are subsequent stages and are not yet implemented.
```

------------------------------------------------------------------------

# Table of Contents

1.  [Vision](./chapters/01-Vision%20[STABLE].md)
2.  [Architecture Principles](./chapters/02-Architecture%20Principles%20[STABLE].md)
3.  [Overall Architecture](./chapters/03-Overall%20Architecture%20[STABLE%20TARGET%20%20PARTIALLY%20IMPLEMENTED].md)
4.  [Logical Component Architecture](./chapters/04-Logical%20Component%20Architecture%20[STABLE%20TARGET%20%20PARTIALLY%20IMPLEMENTED].md)
5.  [Maven Modules](./chapters/05-Maven%20Modules%20[IMPLEMENTED%20%20TARGET].md)
6.  [Package Layout](./chapters/06-Package%20Layout%20[IMPLEMENTED%20%20PROVISIONAL].md)
7.  [Semantic Model](./chapters/07-Semantic%20Model%20[IMPLEMENTED].md)
8.  [FEEL AST](./chapters/08-FEEL%20AST%20[PROVISIONAL%20SCHEMA].md)
9.  [Runtime IR](./chapters/09-Runtime%20IR%20[PROVISIONAL].md)
10. [Compiler Passes](./chapters/10-Compiler%20Passes%20[PROVISIONAL].md)
11. [XML Frontend](./chapters/11-XML%20Frontend%20[IMPLEMENTED].md)
12. [FEEL Parser](./chapters/12-FEEL%20Parser%20[PROVISIONAL].md)
13. [Java Generator](./chapters/13-Java%20Generator%20[FUTURE].md)
14. [Future Generators](./chapters/14-Future%20Generators%20[FUTURE].md)
15. [Testing Strategy](./chapters/15-Testing%20Strategy%20[PROVISIONAL].md)
16. [Performance](./chapters//16-Performance%20[FUTURE].md)
17. [Public API](./chapters/17-Public%20API%20[FUTURE].md)
18. [Roadmap](./chapters/18-Roadmap%20[IMPLEMENTATION-ALIGNED].md)
19. [Internal Compiler Architecture](./chapters/19-Internal%20Compiler%20Architecture%20[FUTURE].md)
20. [Appendices](./chapters/20-Appendices.md)

------------------------------------------------------------------------
