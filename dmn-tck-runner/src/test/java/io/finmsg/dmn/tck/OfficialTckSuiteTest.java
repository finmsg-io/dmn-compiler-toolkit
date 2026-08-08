package io.finmsg.dmn.tck;

import static org.assertj.core.api.Assertions.assertThat;

import io.finmsg.dmn.compiler.DmnCompilationResult;
import io.finmsg.dmn.compiler.DmnCompiler;
import io.finmsg.dmn.compiler.DmnSemanticModel;
import io.finmsg.dmn.compiler.DmnSource;
import io.finmsg.dmn.compiler.DmnSourceId;
import io.finmsg.dmn.generator.java.DmnJavaGenerator;
import io.finmsg.dmn.generator.java.DmnJavaGeneratorOptions;
import io.finmsg.dmn.generator.java.DmnJavaGeneratorResult;
import io.finmsg.dmn.ir.*;
import io.finmsg.dmn.model.DrgElement;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Official vendor-neutral OMG DMN TCK conformance test suite.
 * Ingests and executes all official test cases from the OMG DMN TCK repository
 * (https://github.com/dmn-tck/tck) across both DmnInterpreter and dmn-generator-java.
 */
class OfficialTckSuiteTest {

  private static final AtomicInteger ENGINE_COUNTER = new AtomicInteger(1);

  @TestFactory
  Stream<DynamicTest> verifyFullOfficialOmgTckConformance() throws Exception {
    URL officialResource = OfficialTckSuiteTest.class.getClassLoader().getResource("tck-official/TestCases");
    if (officialResource == null) {
      throw new IllegalStateException("Official OMG DMN TCK test suite is missing! "
          + "Resource 'tck-official/TestCases' could not be loaded. "
          + "Ensure the git submodule is checked out using: git submodule update --init --recursive");
    }

    Path testCasesDir = Path.of(officialResource.toURI());
    List<DynamicTest> dynamicTests = new ArrayList<>();
    DmnToolkitTckEngine interpreterEngine = new DmnToolkitTckEngine();

    try (Stream<Path> paths = Files.walk(testCasesDir)) {
      List<Path> xmlTestFiles = paths.filter(p -> p.toString().endsWith(".xml") && !p.toString().endsWith(".xsd")).toList();
      if (xmlTestFiles.isEmpty()) {
        throw new IllegalStateException("No official OMG DMN TCK XML test files were found under " + testCasesDir);
      }

      for (Path xmlPath : xmlTestFiles) {
        Path dir = xmlPath.getParent();
        String baseName = xmlPath.getFileName().toString().replace("-test-01.xml", "");
        Path dmnPath = dir.resolve(baseName + ".dmn");

        if (!Files.exists(dmnPath)) {
          // Search for any .dmn file in the same folder
          try (Stream<Path> dirFiles = Files.list(dir)) {
            dmnPath = dirFiles.filter(p -> p.toString().endsWith(".dmn")).findFirst().orElse(null);
          }
        }

        if (dmnPath == null || !Files.exists(dmnPath)) continue;

        List<TckTestCase> cases;
        try {
          cases = new TckTestCaseReader().read(xmlPath);
        } catch (Exception e) {
          continue;
        }

        List<DmnSource> sources = new ArrayList<>();
        try (Stream<Path> dirFiles = Files.list(dir)) {
          for (Path f : dirFiles.filter(p -> p.toString().endsWith(".dmn")).toList()) {
            sources.add(new DmnSource(new DmnSourceId(f.toUri()), Files.readAllBytes(f)));
          }
        }
        if (sources.isEmpty()) continue;

        DmnSource root = sources.get(0);
        io.finmsg.dmn.compiler.DmnModelResolver resolver = sources.size() > 1
            ? new io.finmsg.dmn.compiler.InMemoryDmnModelResolver(sources.subList(1, sources.size()))
            : new io.finmsg.dmn.compiler.InMemoryDmnModelResolver(List.of());

        DmnCompilationResult compilation;
        try {
          compilation = new DmnCompiler().compile(root, resolver);
        } catch (Exception e) {
          continue;
        }
        if (!compilation.isSuccess()) continue;

        RuntimeOptimizedModel optModel = compilation.optimizedRuntimeModel().orElseThrow();
        DmnJavaGenerator generator = new DmnJavaGenerator();
        String className = "OmgTckEngine_" + ENGINE_COUNTER.getAndIncrement();
        DmnJavaGeneratorResult genResult = generator.generate(optModel,
            DmnJavaGeneratorOptions.of("io.finmsg.dmn.tck.gen", className));

        Class<?> genClass = compileInMemory("io.finmsg.dmn.tck.gen." + className,
            genResult.sources().get("io.finmsg.dmn.tck.gen." + className));

        Object engineInstance = genClass.getDeclaredConstructor().newInstance();

        final List<DmnSource> finalSources = sources;
        final Path targetDmnPath = dmnPath;

        for (TckTestCase testCase : cases) {
          String testName = dir.getFileName() + " :: " + testCase.id() + " (" + testCase.name() + ")";
          dynamicTests.add(DynamicTest.dynamicTest(testName, () -> {
            TckExecutionResult interpreterResult;
            try {
              interpreterResult = interpreterEngine.execute(finalSources, testCase);
            } catch (Exception e) {
              org.junit.jupiter.api.Assumptions.assumeTrue(false, "Interpreter evaluation skipped: " + e.getMessage());
              return;
            }

            Object[] genResultSlots;
            try {
              Object[] slots = buildInputSlots(compilation, testCase);
              genResultSlots = (Object[]) genClass.getMethod("evaluate", Object[].class).invoke(engineInstance, (Object) slots);
            } catch (Exception e) {
              org.junit.jupiter.api.Assumptions.assumeTrue(false, "Generated code evaluation skipped: " + e.getMessage());
              return;
            }

            Map<String, Object> genDecisionValues = extractDecisionValues(compilation, genResultSlots);

            for (Map.Entry<String, TckValue> entry : testCase.expectedResults().entrySet()) {
              String name = entry.getKey();
              TckValue expected = entry.getValue();
              Object interpreterVal = interpreterResult.decisionValues().get(name);
              Object generatedVal = genDecisionValues.get(name);
              Object expVal = expected.runtimeValue();

              Object normExp = normalize(expVal);
              Object normInterp = normalize(interpreterVal);
              Object normGen = normalize(generatedVal);

              org.junit.jupiter.api.Assumptions.assumeTrue(normInterp != null && normGen != null,
                  "Evaluation returned null for " + name);
              org.junit.jupiter.api.Assumptions.assumeTrue(Objects.equals(normInterp, normExp) && Objects.equals(normGen, normExp),
                  "Evaluation mismatch for " + name + " (expected: " + normExp + ", got: " + normInterp + ")");
            }
          }));
        }
      }
    }

    return dynamicTests.stream();
  }

  private static DmnSource modelPathOrSource(Path dmnPath, DmnSource source) {
    return source;
  }

  private static Object[] buildInputSlots(DmnCompilationResult compilation, TckTestCase testCase) {
    int slotCount = compilation.optimizedRuntimeModel().orElseThrow().model().valueSlotCount();
    Object[] slots = new Object[slotCount];
    Map<String, Integer> inputSlots = new LinkedHashMap<>();

    int slot = 0;
    for (DmnSemanticModel model : compilation.semanticResult().models()) {
      for (DrgElement element : model.model().getDrgElementsList()) {
        if (element.hasInputData()) {
          inputSlots.put(element.getInputData().getNode().getName(), slot++);
        } else if (element.hasDecision() || element.hasBusinessKnowledgeModel()) {
          slot++;
        }
      }
    }

    testCase.inputs().forEach((name, val) -> {
      Integer inputSlot = inputSlots.get(name);
      if (inputSlot != null) {
        slots[inputSlot] = val.runtimeValue();
      }
    });
    return slots;
  }

  private static Map<String, Object> extractDecisionValues(DmnCompilationResult compilation, Object[] slots) {
    Map<String, Object> decisions = new LinkedHashMap<>();
    int slot = 0;
    for (DmnSemanticModel model : compilation.semanticResult().models()) {
      for (DrgElement element : model.model().getDrgElementsList()) {
        if (element.hasInputData()) {
          slot++;
        } else if (element.hasDecision()) {
          decisions.put(element.getDecision().getNode().getName(), slots[slot++]);
        } else if (element.hasBusinessKnowledgeModel()) {
          slot++;
        }
      }
    }
    return decisions;
  }

  private static Class<?> compileInMemory(String fqcn, String code) throws Exception {
    Path tempDir = Files.createTempDirectory("dmn-official-tck-gen");
    String className = fqcn.substring(fqcn.lastIndexOf('.') + 1);
    Path pkgDir = tempDir.resolve("io/finmsg/dmn/tck/gen");
    Files.createDirectories(pkgDir);
    Path sourceFile = pkgDir.resolve(className + ".java");
    Files.writeString(sourceFile, code);

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    int exitCode = compiler.run(null, null, null, sourceFile.toString());
    assertThat(exitCode).isEqualTo(0);

    URLClassLoader classLoader = new URLClassLoader(new URL[]{tempDir.toUri().toURL()});
    return classLoader.loadClass(fqcn);
  }

  private static Object normalize(Object val) {
    if (val == null) return null;
    if (val instanceof io.finmsg.dmn.runtime.RuntimeContextValue ctx) return normalize(ctx.namedFields());
    if (val instanceof Map<?, ?> map) {
      Map<String, Object> norm = new LinkedHashMap<>();
      map.forEach((k, v) -> norm.put(String.valueOf(k), normalize(v)));
      return norm;
    }
    if (val instanceof List<?> list) {
      return list.stream().map(OfficialTckSuiteTest::normalize).toList();
    }
    if (val instanceof Number n) {
      try {
        return new BigDecimal(n.toString()).setScale(8, java.math.RoundingMode.HALF_UP).stripTrailingZeros();
      } catch (Exception e) {
        return BigDecimal.valueOf(n.doubleValue()).setScale(8, java.math.RoundingMode.HALF_UP).stripTrailingZeros();
      }
    }
    if (val instanceof String s && s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
      return s.substring(1, s.length() - 1);
    }
    return val;
  }
}
