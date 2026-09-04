# Java Developer Guide

This guide provides software engineers with everything needed to build, embed, and execute decision models using the **DMN Compiler Toolkit**.

---

## 1. Quickstart & Maven Coordinates

Add the toolkit core and runtime dependencies to your `pom.xml`:

```xml
<dependencies>
    <!-- Core DMN Runtime (for interpretation or model execution) -->
    <dependency>
        <groupId>io.finmsg.dmn</groupId>
        <artifactId>dmn-runtime</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>

    <!-- Optional: Toolkit Compiler (for compiling DMN XML to Java or IR) -->
    <dependency>
        <groupId>io.finmsg.dmn</groupId>
        <artifactId>dmn-compiler</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <scope>test</scope> <!-- Or compile-time build step -->
    </dependency>
</dependencies>
```

---

## 2. Using the DMN Interpreter (`DmnRuntime`)

To parse and execute a DMN XML model dynamically in your Java application:

```java
import io.finmsg.dmn.compiler.DmnCompiler;
import io.finmsg.dmn.compiler.CompilationResult;
import io.finmsg.dmn.runtime.DmnRuntime;
import io.finmsg.dmn.runtime.ExecutionContext;
import io.finmsg.dmn.runtime.DecisionResult;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Map;

public class LoanApprovalExample {

    public static void main(String[] args) throws Exception {
        // 1. Compile DMN XML model to immutable Runtime IR
        CompilationResult compilation = DmnCompiler.compile(
            LoanApprovalExample.class.getResourceAsStream("/models/loan-approval.dmn")
        );

        if (compilation.hasErrors()) {
            compilation.getDiagnostics().forEach(System.err::println);
            throw new IllegalStateException("DMN Compilation failed");
        }

        // 2. Initialize thread-safe runtime engine
        DmnRuntime runtime = DmnRuntime.of(compilation.getRuntimeModel());

        // 3. Prepare execution context
        Map<String, Object> inputData = Map.of(
            "creditScore", 750,
            "monthlyIncome", new BigDecimal("8500.00"),
            "loanAmount", new BigDecimal("250000.00")
        );

        // 4. Evaluate decision
        DecisionResult result = runtime.evaluateDecision("LoanEligibility", inputData);

        System.out.println("Eligibility Status: " + result.getValue());
    }
}
```

---

## 3. Ahead-of-Time (AOT) Java Bytecode Generation

For maximum performance (sub-microsecond execution), compile DMN models to pure Java source code during your build:

```java
import io.finmsg.dmn.compiler.DmnCompiler;
import io.finmsg.dmn.generator.java.JavaCodeGenerator;
import io.finmsg.dmn.generator.java.JavaGeneratorConfig;

import java.nio.file.Path;

public class CodeGenBuildTask {
    public static void main(String[] args) throws Exception {
        CompilationResult compilation = DmnCompiler.compile(Path.of("src/main/resources/models/loan-approval.dmn"));

        JavaCodeGenerator generator = new JavaCodeGenerator(
            JavaGeneratorConfig.builder()
                .packageName("com.mycompany.decisions")
                .className("LoanApprovalEngine")
                .build()
        );

        // Generates clean, zero-dependency Java source code
        String javaSource = generator.generateSource(compilation.getRuntimeModel());
        System.out.println(javaSource);
    }
}
```

### Direct strongly-typed execution of generated code:

```java
import com.mycompany.decisions.LoanApprovalEngine;

public class HighFrequencyScoringApp {
    private final LoanApprovalEngine engine = new LoanApprovalEngine();

    public boolean checkLoan(int score, double income, double amount) {
        // Direct method call - 0 reflection, 0 GC overhead, ~180 nanoseconds
        return engine.evaluateLoanEligibility(score, income, amount);
    }
}
```

---

## 4. Diagnostics & Error Handling

All compilation and evaluation errors produce structured `Diagnostic` objects with severity, error codes, and source line numbers:

```java
compilation.getDiagnostics().forEach(diag -> {
    System.out.printf("[%s] %s (Line %d, Col %d): %s%n",
        diag.getSeverity(),
        diag.getCode(),
        diag.getLineNumber(),
        diag.getColumnNumber(),
        diag.getMessage()
    );
});
```

---

## 5. CLI & Build Tools

The toolkit provides command-line utilities in `tools/`:

- **TCK Conformance Dashboard**: `python tools/generate_tck_dashboard.py` (generates `docs/tck-dashboard.html`)
- **TCK Regression Diff**: `python tools/diff_tck_accounting.py --baseline <base.json> --current <curr.json>`
- **Release Preflight Dry-Run**: `python tools/release_dry_run.py`
- **Scalability Benchmarks Summarizer**: `python tools/summarize_scalability_benchmarks.py`
