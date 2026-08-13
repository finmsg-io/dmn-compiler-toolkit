# `dmn-models` TODO

Last reviewed: 2026-08-08

The `dmn-models` module owns structured multi-file DMN model directory suites (`loan-approval`, `order-fulfillment`, `discount-calculation`) and the Java multi-file DMN streaming ingestion/resolution API (`DmnStreamBundle`, `DmnStreamResolver`).

## Current status

| Priority | Work item | Status | Evidence |
| --- | --- | --- | --- |
| P12.1 | Multi-file DMN directory suites in `src/main/resources/models/` | `done` | `loan-approval`, `order-fulfillment`, `discount-calculation` |
| P12.2 | In-memory ZIP/JAR streaming (`fromZip`) without disk unpacking | `done` | `DmnStreamBundle.fromZip` |
| P12.3 | Directory tree (`fromDirectory`) & classpath (`fromClasspath`) streaming | `done` | `DmnStreamBundle.fromDirectory`, `fromClasspath` |
| P12.4 | Stream Map (`fromStreams`) & DMN source collection streaming | `done` | `DmnStreamBundle.fromStreams`, `fromSources` |
| P12.5 | Automatic DRG root entry-point detection (`findRootSource`) | `done` | `DmnStreamBundle.findRootSource` |
| P12.6 | Policy-boundary `DmnModelResolver` implementation | `done` | `DmnStreamResolver` |
| P12.7 | Multi-file streaming test suite | `done` | `DmnStreamBundleTest` |
