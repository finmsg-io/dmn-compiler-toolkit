# `dmn-frontend-xml` architecture assessment — resolution status

This document records the disposition of the earlier frontend architecture review. It is no
longer a description of the current implementation; use the
[current TODO list](todos-dmn-frntend-xml.md) and
[completeness audit](../../audits/dmn-frontend-xml-completeness.md) for active work.

## Resolved findings

| Original finding | Resolution |
| --- | --- |
| Document-global namespace resolution | Replaced by cursor-scoped effective namespace matching. |
| Local-name-only reader dispatch | DMN dispatch is namespace-aware; foreign collisions are tested. |
| Missing source locations | Opt-in system ID, line, column, and byte offset capture implemented. |
| Silent/exception-only read boundary | `DmnReadResult`, structured diagnostics, and stable legacy APIs implemented. |
| Unbounded input buffering | Path, stream, and byte-array entry points enforce a configurable size limit. |
| Reader/writer asymmetry | Symmetric coverage implemented for the current protobuf-supported subset. |
| Incomplete imports | Namespace, name, location URI, and import type read/write mappings completed. |
| Missing node metadata | Documentation and one-level structured extension elements preserved. |
| Default-namespace writer conflict | Collision-free prefixed-DMN output implemented. |
| Sparse round-trip coverage | Focused namespace, diagnostic, expression, DRG, and writer round trips added. |

## Partially resolved findings

### Unsupported content policy

Well-formed but unsupported decision content produces `DMN-XML-004`. Some other readers still
skip unknown DMN children. The remaining task is to apply the same policy consistently while
continuing to permit foreign extension namespaces.

### Version policy

Supported DMN vocabulary namespaces are detected and normalized into one semantic model, and
the original non-default vocabulary URI can be written back. The remaining work is a broader
multi-version fixture matrix, not basic version detection.

### Public API encapsulation

The stable compiler-facing boundary is `DmnXmlReader`, `DmnWriter`, `DmnReadOptions`, and
`DmnReadResult`. Internal reader/writer classes remain public in places; reducing visibility is
cleanup rather than a correctness blocker.

## Newly identified blocker

QName type references remain namespace-lossy: `risk:Applicant` becomes a named type called
`Applicant` without its resolved namespace. Fixing scoped QName reading/writing is required
before the subset is declared complete for cross-model types.

## Deferred model scope

DMNDI, artifacts, associations, business-context metadata, deeper arbitrary extension trees,
and recursive item components require protobuf/model decisions. They are not implementation
defects in the current reader/writer mapping.
