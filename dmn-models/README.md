# `dmn-models` — DMN Multi-File Sample Suite & Java Streaming API

Provides structured multi-file DMN model directory suites and a Java streaming ingestion/resolution API (`DmnStreamBundle`, `DmnStreamResolver`) for streaming multi-file DMN models from ZIP archives, directory trees, classpath resources, and input streams.

---

## Features

### 1. Multi-File DMN Model Directories
Houses pre-configured sample multi-file DMN model suites in separate directories under `src/main/resources/models/`:
- **`models/loan-approval/`**: `main-loan.dmn` importing `credit-score.dmn` and `applicant-risk.dmn`.
- **`models/order-fulfillment/`**: `fulfillment-root.dmn` importing `inventory-check.dmn` and `shipping-calculator.dmn`.
- **`models/discount-calculation/`**: `pricing-root.dmn` importing `tier-rules.dmn`.

### 2. Multi-File Java Streaming API (`DmnStreamBundle`)
- **ZIP/JAR Streaming**: Ingests multi-file DMN ZIP/JAR archives directly from `InputStream` or byte arrays without unpacking to disk.
- **Directory Tree Streaming**: Walks directory paths (`Files.walk`) to load location-addressed DMN models.
- **Classpath Resource Bundles**: Streams classpath model directories or JAR entries (`fromClasspath("models/loan-approval")`).
- **Stream Map Ingestion**: Accepts raw `Map<String, InputStream>` or collections of `DmnSource`.
- **Automatic Root Detection**: `findRootSource()` dynamically identifies the entry-point root DMN model in an imported model graph.
- **`DmnModelResolver` Adapter**: `asResolver()` adapts the bundle directly into a policy-boundary resolver for `DmnCompiler` and `DmnModelLoader`.

---

## Usage Examples

### 1. Streaming from a ZIP Archive (No Disk Unpacking)
```java
InputStream zipStream = getZipInputStream();
DmnStreamBundle bundle = DmnStreamBundle.fromZip(zipStream);

// Automatically finds the root DMN model and compiles the multi-file graph
DmnCompilationResult compilation = bundle.compile(compiler);
DmnCompiledModel compiledModel = compilation.compiledModel().orElseThrow();
EvaluationResponse response = compiledModel.evaluateInputs(Map.of());
```

### 2. Streaming from Classpath Resource Directory
```java
// Loads models/loan-approval/main-loan.dmn, credit-score.dmn, applicant-risk.dmn
DmnStreamBundle bundle = DmnStreamBundle.fromClasspath("models/loan-approval");

DmnCompilationResult result = bundle.compile(compiler);
DmnCompiledModel model = result.compiledModel().orElseThrow();
```

### 3. Streaming from Filesystem Directory Path
```java
Path dir = Path.of("/var/dmn/models/loan-suite");
DmnStreamBundle bundle = DmnStreamBundle.fromDirectory(dir);

DmnModelLoadResult loadResult = bundle.load(loader);
```

### 4. Streaming from InputStreams Map
```java
Map<String, InputStream> streams = Map.of(
    "root.dmn", rootInputStream,
    "base.dmn", baseInputStream
);

DmnStreamBundle bundle = DmnStreamBundle.fromStreams(streams);
DmnCompilationResult result = bundle.compile(compiler);
```

---

## Testing & Verification
- Unit tested in `DmnStreamBundleTest` across ZIP, classpath, directory, and stream map ingestion sources.
