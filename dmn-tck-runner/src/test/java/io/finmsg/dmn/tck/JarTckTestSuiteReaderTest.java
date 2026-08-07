package io.finmsg.dmn.tck;

import static org.assertj.core.api.Assertions.assertThat;

import io.finmsg.dmn.compiler.DmnCompilationResult;
import io.finmsg.dmn.compiler.DmnCompiler;
import io.finmsg.dmn.compiler.DmnSource;
import io.finmsg.dmn.compiler.DmnSourceId;
import io.finmsg.dmn.generator.java.DmnJavaGenerator;
import io.finmsg.dmn.generator.java.DmnJavaGeneratorOptions;
import io.finmsg.dmn.generator.java.DmnJavaGeneratorResult;
import io.finmsg.dmn.ir.RuntimeOptimizedModel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

class JarTckTestSuiteReaderTest {

  @Test
  void readsAndExecutesTckTestSuitesDirectlyFromJarArchive() throws Exception {
    // 1. Construct an in-memory JAR archive containing official DMN & TCK XML
    ByteArrayOutputStream jarBaos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(jarBaos)) {
      zos.putNextEntry(new ZipEntry("compliance-level-2/0001-input-data-string/0001-input-data-string.dmn"));
      zos.write("""
          <?xml version="1.0" encoding="UTF-8"?>
          <definitions xmlns="https://www.omg.org/spec/DMN/20230324/MODEL/"
                       id="_0001" name="0001" namespace="https://github.com/dmn-tck/tck">
            <inputData id="i_Name" name="Name"><variable name="Name" typeRef="string"/></inputData>
            <decision id="d_Greeting" name="Greeting">
              <variable name="Greeting" typeRef="string"/>
              <informationRequirement><requiredInput href="#i_Name"/></informationRequirement>
              <literalExpression><text>"Hello " + Name</text></literalExpression>
            </decision>
          </definitions>
          """.getBytes());
      zos.closeEntry();

      zos.putNextEntry(new ZipEntry("compliance-level-2/0001-input-data-string/0001-input-data-string-test-01.xml"));
      zos.write("""
          <?xml version="1.0" encoding="UTF-8"?>
          <testCases xmlns="http://www.omg.org/spec/DMN/20160719/testcase"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xmlns:xsd="http://www.w3.org/2001/XMLSchema">
            <testCase id="0001-jar-01" name="jar greeting test">
              <inputNode name="Name"><value xsi:type="xsd:string">Alice</value></inputNode>
              <resultNode name="Greeting"><expected><value xsi:type="xsd:string">Hello Alice</value></expected></resultNode>
            </testCase>
          </testCases>
          """.getBytes());
      zos.closeEntry();
    }

    byte[] jarBytes = jarBaos.toByteArray();

    // 2. Read test suite directly from JAR stream using JarTckTestSuiteReader
    JarTckTestSuiteReader jarReader = new JarTckTestSuiteReader();
    List<JarTckTestSuiteReader.TckJarModelSuite> suites = jarReader.readFromJarStream(
        new ByteArrayInputStream(jarBytes), URI.create("file:/official-tck-cases.jar"));

    assertThat(suites).hasSize(1);
    JarTckTestSuiteReader.TckJarModelSuite suite = suites.getFirst();
    assertThat(suite.testCases()).hasSize(1);

    // 3. Execute with DmnToolkitTckEngine directly from JAR memory stream
    DmnSource dmnSource = new DmnSource(new DmnSourceId(suite.modelUri()), suite.dmnBytes());
    DmnToolkitTckEngine engine = new DmnToolkitTckEngine();

    TckTestCase testCase = suite.testCases().getFirst();
    TckExecutionResult result = engine.execute(dmnSource, testCase);

    assertThat(result.decisionValues().get("Greeting")).isEqualTo("Hello Alice");

    // 4. Execute with dmn-generator-java in-memory compiled class
    DmnCompilationResult compilation = new DmnCompiler().compile(dmnSource);
    assertThat(compilation.isSuccess()).isTrue();

    RuntimeOptimizedModel optModel = compilation.optimizedRuntimeModel().orElseThrow();
    DmnJavaGenerator generator = new DmnJavaGenerator();
    DmnJavaGeneratorResult genResult = generator.generate(optModel,
        DmnJavaGeneratorOptions.of("io.finmsg.dmn.tck.gen", "JarEngine0001"));

    Class<?> genClass = compileInMemory("io.finmsg.dmn.tck.gen.JarEngine0001",
        genResult.sources().get("io.finmsg.dmn.tck.gen.JarEngine0001"));

    Object engineInstance = genClass.getDeclaredConstructor().newInstance();
    Object[] slots = new Object[]{ "Alice", null };
    Object[] genResultSlots = (Object[]) genClass.getMethod("evaluate", Object[].class).invoke(engineInstance, (Object) slots);

    assertThat(genResultSlots[1]).isEqualTo("Hello Alice");
  }

  private static Class<?> compileInMemory(String fqcn, String code) throws Exception {
    Path tempDir = Files.createTempDirectory("dmn-jar-tck-gen-test");
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
}
