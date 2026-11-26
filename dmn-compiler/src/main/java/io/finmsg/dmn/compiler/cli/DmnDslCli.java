package io.finmsg.dmn.compiler.cli;

import io.finmsg.dmn.compiler.*;
import io.finmsg.dmn.compiler.dmnir.DmnIrScalarDmnExporter;
import io.finmsg.dmn.compiler.dmnir.DmnIrScalarProducer;
import io.finmsg.dmn.dmnir.codec.DmnIrArtifactReader;
import io.finmsg.dmn.frontend.dsl.DmnDslReader;
import io.finmsg.dmn.frontend.xml.dmn.DmnVersion;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Command-line interface for authoring, checking, formatting, testing,
 * compiling, and exporting {@code .decision} DSL and DMN models.
 *
 * <p>
 * Exit codes:
 * <ul>
 * <li>{@code 0}: success</li>
 * <li>{@code 1}: parse, semantic, compile, test, or export failure</li>
 * <li>{@code 2}: CLI usage / argument error</li>
 * </ul>
 */
public final class DmnDslCli {

	public static final int EXIT_OK = 0;
	public static final int EXIT_FAILURE = 1;
	public static final int EXIT_USAGE = 2;

	private final PrintStream out;
	private final PrintStream err;

	public DmnDslCli(PrintStream out, PrintStream err) {
		this.out = Objects.requireNonNull(out, "out");
		this.err = Objects.requireNonNull(err, "err");
	}

	public static void main(String[] args) {
		int code = new DmnDslCli(System.out, System.err).run(args);
		if (code != 0) {
			System.exit(code);
		}
	}

	public int run(String[] args) {
		if (args == null || args.length == 0 || args[0].equals("-h") || args[0].equals("--help")) {
			printUsage();
			return args != null && args.length > 0 ? EXIT_OK : EXIT_USAGE;
		}
		String cmd = args[0];
		try {
			return switch (cmd) {
				case "check" -> runCheck(args);
				case "format" -> runFormat(args);
				case "test" -> runTest(args);
				case "compile" -> runCompile(args);
				case "export" -> runExport(args);
				default -> {
					err.println("Unknown command: " + cmd);
					printUsage();
					yield EXIT_USAGE;
				}
			};
		} catch (Exception e) {
			err.println("Error: " + e.getMessage());
			return EXIT_FAILURE;
		}
	}

	private int runCheck(String[] args) throws Exception {
		if (args.length != 2) {
			err.println("Usage: check <file>");
			return EXIT_USAGE;
		}
		Path file = Path.of(args[1]).toAbsolutePath().normalize();
		DmnSource src = readSource(file);
		DmnModelResolver resolver = new FilesystemDmnModelResolver(file.getParent());
		DmnCompilationResult result = new DmnCompiler().compile(src, resolver, DmnCompilerOptions.optimized());
		if (!result.isSuccess()) {
			for (var diag : result.diagnostics()) {
				err.println(diag);
			}
			return EXIT_FAILURE;
		}
		out.println("OK: " + file.getFileName() + " (" + result.loadedModels().models().size() + " model(s))");
		return EXIT_OK;
	}

	private int runFormat(String[] args) throws Exception {
		if (args.length < 2 || args.length > 3) {
			err.println("Usage: format <file.decision> [--write]");
			return EXIT_USAGE;
		}
		Path file = Path.of(args[1]).toAbsolutePath().normalize();
		boolean writeInPlace = args.length == 3 && args[2].equals("--write");
		if (args.length == 3 && !writeInPlace) {
			err.println("Unknown option: " + args[2]);
			return EXIT_USAGE;
		}
		String content = Files.readString(file, StandardCharsets.UTF_8);
		String formatted = new DmnDslReader().format(content);
		if (writeInPlace) {
			Files.writeString(file, formatted, StandardCharsets.UTF_8);
			out.println("Formatted " + file);
		} else {
			out.print(formatted);
		}
		return EXIT_OK;
	}

	private int runTest(String[] args) throws Exception {
		if (args.length != 2) {
			err.println("Usage: test <file.decision>");
			return EXIT_USAGE;
		}
		Path file = Path.of(args[1]).toAbsolutePath().normalize();
		DmnSource src = readSource(file);
		DmnModelResolver resolver = new FilesystemDmnModelResolver(file.getParent());
		DmnDslTestRunner.TestReport report = new DmnDslTestRunner().run(src, resolver);
		for (var result : report.results()) {
			if (result.passed()) {
				out.println("PASS: " + result.testName());
			} else {
				err.println("FAIL: " + result.testName());
				for (String failure : result.failures()) {
					err.println("  - " + failure);
				}
			}
		}
		out.println("Tests: " + report.passed() + "/" + report.total() + " passed, " + report.failed() + " failed");
		return report.allPassed() ? EXIT_OK : EXIT_FAILURE;
	}

	private int runCompile(String[] args) throws Exception {
		if (args.length != 4 || !args[2].equals("--out")) {
			err.println("Usage: compile <file> --out <output.dmnir>");
			return EXIT_USAGE;
		}
		Path file = Path.of(args[1]).toAbsolutePath().normalize();
		Path outFile = Path.of(args[3]).toAbsolutePath().normalize();
		DmnSource src = readSource(file);
		DmnModelResolver resolver = new FilesystemDmnModelResolver(file.getParent());
		byte[] bytes = new DmnIrScalarProducer().produce(src, resolver).artifact().toByteArray();
		if (outFile.getParent() != null) {
			Files.createDirectories(outFile.getParent());
		}
		Files.write(outFile, bytes);
		out.println("Compiled " + file.getFileName() + " -> " + outFile + " (" + bytes.length + " bytes)");
		return EXIT_OK;
	}

	private int runExport(String[] args) throws Exception {
		if (args.length < 4 || !args[2].equals("--out")) {
			err.println("Usage: export <file.dmnir|file.decision> --out <outDir> [--dmn-version 1.6|1.5|1.3]");
			return EXIT_USAGE;
		}
		Path file = Path.of(args[1]).toAbsolutePath().normalize();
		Path outDir = Path.of(args[3]).toAbsolutePath().normalize();
		DmnVersion version = DmnVersion.DMN_1_6;
		if (args.length == 6 && args[4].equals("--dmn-version")) {
			version = switch (args[5]) {
				case "1.6" -> DmnVersion.DMN_1_6;
				case "1.5" -> DmnVersion.DMN_1_5;
				case "1.3" -> DmnVersion.DMN_1_3;
				default -> {
					err.println("Unsupported DMN version: " + args[5] + " (supported: 1.6, 1.5, 1.3)");
					yield null;
				}
			};
			if (version == null) {
				return EXIT_USAGE;
			}
		} else if (args.length != 4) {
			err.println("Usage: export <file.dmnir|file.decision> --out <outDir> [--dmn-version 1.6|1.5|1.3]");
			return EXIT_USAGE;
		}

		DmnIrArtifactReader.VerifiedArtifact verified;
		if (file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".dmnir")) {
			verified = new DmnIrArtifactReader().read(Files.readAllBytes(file));
		} else {
			DmnSource src = readSource(file);
			DmnModelResolver resolver = new FilesystemDmnModelResolver(file.getParent());
			verified = new DmnIrScalarProducer().produce(src, resolver);
		}

		var bundle = new DmnIrScalarDmnExporter().export(verified, version);
		Files.createDirectories(outDir);
		for (var doc : bundle.documents().entrySet()) {
			Files.writeString(outDir.resolve(doc.getKey()), doc.getValue(), StandardCharsets.UTF_8);
		}
		out.println("Exported " + bundle.documents().size() + " " + version + " DMN file(s) to " + outDir + " (root: "
				+ bundle.rootDocument() + ")");
		return EXIT_OK;
	}

	private static DmnSource readSource(Path file) throws Exception {
		return new DmnSource(DmnSourceId.of(file.toUri().toString()), Files.readAllBytes(file));
	}

	private void printUsage() {
		out.println("""
				Usage: dmn-dsl <command> [options]
				Commands:
				  check   <file>                                          Parse and compile model + imports
				  format  <file.decision> [--write]                       Format .decision canonically
				  test    <file.decision>                                 Run inline test blocks
				  compile <file> --out <model.dmnir>                      Compile to portable .dmnir artifact
				  export  <file.dmnir|file.decision> --out <dir> [--dmn-version 1.6|1.5|1.3]
				                                                          Export conforming DMN XML (default 1.6)
				""");
	}
}
