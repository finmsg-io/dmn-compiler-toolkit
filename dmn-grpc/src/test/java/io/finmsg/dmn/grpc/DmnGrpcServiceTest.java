package io.finmsg.dmn.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import io.finmsg.dmn.benchmark.provider.ReferenceModelRegistry;
import io.finmsg.dmn.generator.java.DmnJavaGenerator;
import io.finmsg.dmn.generator.java.DmnJavaGeneratorOptions;
import io.finmsg.dmn.generator.java.DmnJavaGeneratorResult;
import io.finmsg.dmn.ir.RuntimeOptimizedModel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DmnGrpcServiceTest {

	@Test
	@DisplayName("DmnGrpcValueConverter: Bidirectional scalar, list, and context conversion")
	void testValueConverterBidirectional() {
		// Number conversion
		BigDecimal num = new BigDecimal("42.50");
		Value numVal = DmnGrpcValueConverter.toProtoValue(num);
		assertThat(DmnGrpcValueConverter.toJavaObject(numVal)).isEqualTo(num);

		// Boolean conversion
		Value boolVal = DmnGrpcValueConverter.toProtoValue(true);
		assertThat(DmnGrpcValueConverter.toJavaObject(boolVal)).isEqualTo(true);

		// String conversion
		Value strVal = DmnGrpcValueConverter.toProtoValue("Hello DMN");
		assertThat(DmnGrpcValueConverter.toJavaObject(strVal)).isEqualTo("Hello DMN");

		// List conversion
		Value listVal = DmnGrpcValueConverter.toProtoValue(List.of("A", "B", "C"));
		assertThat(DmnGrpcValueConverter.toJavaObject(listVal)).isEqualTo(List.of("A", "B", "C"));

		// Context conversion
		Value ctxVal = DmnGrpcValueConverter.toProtoValue(Map.of("k1", "v1", "k2", 100));
		Object resMap = DmnGrpcValueConverter.toJavaObject(ctxVal);
		assertThat(resMap).isInstanceOf(Map.class);
	}

	@Test
	@DisplayName("Full gRPC integration: In-process Netty-free server execution on traffic-violation.dmn")
	void testGrpcServiceInProcessExecution() throws Exception {
		ReferenceModelRegistry.CompiledModelHolder holder = ReferenceModelRegistry
				.loadFromClasspath("models/traffic-violation.dmn");

		RuntimeOptimizedModel optModel = holder.compilationResult().optimizedRuntimeModel().orElseThrow();

		// 1. Generate compiled AOT Java decision engine
		String genPkg = "io.finmsg.dmn.grpc.gen";
		String engineClassName = "TrafficEngine_" + UUID.randomUUID().toString().replace("-", "");
		String engineFqcn = genPkg + "." + engineClassName;

		DmnJavaGenerator javaGen = new DmnJavaGenerator();
		DmnJavaGeneratorResult engineResult = javaGen.generate(optModel,
				DmnJavaGeneratorOptions.of(genPkg, engineClassName));

		// 2. Generate gRPC service handler wrapping engine
		String grpcClassName = "TrafficGrpcService_" + UUID.randomUUID().toString().replace("-", "");
		String grpcFqcn = genPkg + "." + grpcClassName;

		DmnGrpcGenerator grpcGen = new DmnGrpcGenerator();
		DmnGrpcGeneratorResult grpcResult = grpcGen.generate(holder.compilationResult(), engineFqcn,
				DmnGrpcGeneratorOptions.of(genPkg, grpcClassName));

		// 3. Compile both Java classes in-memory
		Class<?> serviceClass = compileInMemory(
				Map.of(engineFqcn, engineResult.mainSource(), grpcFqcn, grpcResult.mainSource()), grpcFqcn);

		DmnEvaluationServiceGrpc.DmnEvaluationServiceImplBase serviceInstance = (DmnEvaluationServiceGrpc.DmnEvaluationServiceImplBase) serviceClass
				.getDeclaredConstructor().newInstance();

		// 4. Start pure grpc-java in-process server (zero network sockets!)
		String serverName = InProcessServerBuilder.generateName();
		Server server = InProcessServerBuilder.forName(serverName).directExecutor().addService(serviceInstance).build()
				.start();

		try {
			// 5. Connect pure grpc-java in-process client stub
			var channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
			DmnEvaluationServiceGrpc.DmnEvaluationServiceBlockingStub stub = DmnEvaluationServiceGrpc
					.newBlockingStub(channel);

			DmnEvaluationRequest request = DmnEvaluationRequest.newBuilder()
					.setModelNamespace("https://finmsg.io/models/traffic").setModelName(holder.modelName())
					.putInputs("Speed", DmnGrpcValueConverter.toProtoValue(new BigDecimal("140")))
					.putInputs("SpeedLimit", DmnGrpcValueConverter.toProtoValue(new BigDecimal("100"))).build();

			DmnEvaluationResponse response = stub.evaluate(request);

			assertThat(response).isNotNull();
			assertThat(response.getOutputsMap()).containsKey("Violation Points");

			Value vpValue = response.getOutputsMap().get("Violation Points");
			Object vpObj = DmnGrpcValueConverter.toJavaObject(vpValue);
			assertThat(vpObj).isNotNull();
			assertThat(vpObj).isEqualTo(new BigDecimal("10"));

			channel.shutdown();
		} finally {
			server.shutdown();
		}
	}

	private static Class<?> compileInMemory(Map<String, String> sources, String mainFqcn) throws Exception {
		Path tempDir = Files.createTempDirectory("dmn-grpc-test-gen");
		for (Map.Entry<String, String> entry : sources.entrySet()) {
			String fqcn = entry.getKey();
			String className = fqcn.substring(fqcn.lastIndexOf('.') + 1);
			Path pkgDir = tempDir.resolve("io/finmsg/dmn/grpc/gen");
			Files.createDirectories(pkgDir);
			Path sourceFile = pkgDir.resolve(className + ".java");
			Files.writeString(sourceFile, entry.getValue(), java.nio.charset.StandardCharsets.UTF_8);
		}

		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		List<String> options = List.of("-d", tempDir.toString(), "-classpath", System.getProperty("java.class.path"),
				"-encoding", "UTF-8");

		List<File> files = new ArrayList<>();
		for (String fqcn : sources.keySet()) {
			String className = fqcn.substring(fqcn.lastIndexOf('.') + 1);
			files.add(tempDir.resolve("io/finmsg/dmn/grpc/gen").resolve(className + ".java").toFile());
		}

		var fileManager = compiler.getStandardFileManager(null, null, null);
		var compilationUnits = fileManager.getJavaFileObjectsFromFiles(files);
		var task = compiler.getTask(null, fileManager, null, options, null, compilationUnits);
		boolean success = task.call();
		fileManager.close();

		if (!success) {
			throw new IllegalStateException("InMemory compilation failed for " + mainFqcn);
		}

		URLClassLoader classLoader = new URLClassLoader(new URL[]{tempDir.toUri().toURL()},
				DmnGrpcServiceTest.class.getClassLoader());
		return classLoader.loadClass(mainFqcn);
	}
}
