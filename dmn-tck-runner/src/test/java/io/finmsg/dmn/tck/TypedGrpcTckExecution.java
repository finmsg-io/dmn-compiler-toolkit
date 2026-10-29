package io.finmsg.dmn.tck;

import com.google.protobuf.*;
import io.finmsg.dmn.compiler.DmnCompilationResult;
import io.finmsg.dmn.grpc.*;
import io.finmsg.dmn.ir.*;
import io.finmsg.dmn.runtime.ExternalJavaPolicy;
import io.grpc.*;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.tools.ToolProvider;

/** Model-specific protobuf and production typed handlers over real HTTP/2. */
final class TypedGrpcTckExecution implements AutoCloseable {
	static final class PreparationFailure extends RuntimeException {
		PreparationFailure(Throwable cause) {
			super("Typed service preparation failed: " + cause.getMessage(), cause);
		}
	}
	private static final AtomicInteger IDS = new AtomicInteger();
	private static Host host;
	private final URLClassLoader loader;
	private final Descriptors.FileDescriptor schema;
	private final RuntimeModel model;
	private final Map<String, Integer> inputSlots;
	private final String serviceName;
	private record Invocation(RuntimeBkm bkm, List<String> outputs) {
	}
	private final Map<String, Invocation> invocations;

	private TypedGrpcTckExecution(URLClassLoader loader, Descriptors.FileDescriptor schema, RuntimeModel model,
			ServerServiceDefinition service, Map<String, Integer> inputSlots, Map<String, Invocation> invocations)
			throws Exception {
		this.loader = loader;
		this.schema = schema;
		this.model = model;
		this.inputSlots = inputSlots;
		this.invocations = invocations;
		serviceName = service.getServiceDescriptor().getName();
		transport().services.put(serviceName, service);
		service.getMethods().forEach(
				method -> transportUnchecked().methods.put(method.getMethodDescriptor().getFullMethodName(), method));
	}

	static TypedGrpcTckExecution prepare(DmnCompilationResult compilation, ExternalJavaPolicy policy) {
		try {
			int id = IDS.incrementAndGet();
			var options = new TypedGrpcGeneratorOptions("finmsg.dmn.typed.n" + id, "io.finmsg.dmn.tck.typedgen",
					"TypedModel" + id + "Proto", "TypedModel" + id + "Service");
			var generated = new DmnTypedGrpcGenerator().generate(compilation.optimizedRuntimeModel().orElseThrow(),
					options);
			Path directory = Files.createTempDirectory("dmn-typed-grpc-tck-");
			Path proto = directory.resolve(generated.protoFileName());
			Files.writeString(proto, generated.protoContent(), java.nio.charset.StandardCharsets.UTF_8);
			run(List.of(tool("protoc-", "typed.grpc.protoc").toString(), "--proto_path=" + directory,
					"--proto_path=" + Path.of("../dmn-grpc/src/main/proto/io/finmsg/dmn/grpc").toAbsolutePath(),
					"--java_out=" + directory, "--grpc-java_out=" + directory,
					"--plugin=protoc-gen-grpc-java=" + tool("protoc-gen-grpc-java-", "typed.grpc.plugin"),
					proto.toString()), directory);
			for (var source : generated.javaSources().entrySet()) {
				Path file = directory.resolve(source.getKey().replace('.', '/') + ".java");
				Files.createDirectories(file.getParent());
				Files.writeString(file, source.getValue(), java.nio.charset.StandardCharsets.UTF_8);
			}
			var arguments = new ArrayList<>(List.of("-encoding", "UTF-8", "-classpath",
					System.getProperty("java.class.path"), "-d", directory.toString()));
			try (var files = Files.walk(directory)) {
				files.filter(file -> file.toString().endsWith(".java")).forEach(file -> arguments.add(file.toString()));
			}
			var diagnostics = new java.io.ByteArrayOutputStream();
			if (ToolProvider.getSystemJavaCompiler().run(null, null, diagnostics,
					arguments.toArray(String[]::new)) != 0)
				throw new IllegalStateException(diagnostics.toString(java.nio.charset.StandardCharsets.UTF_8));
			var loader = new URLClassLoader(new URL[]{directory.toUri().toURL()},
					TypedGrpcTckExecution.class.getClassLoader());
			try {
				var schema = (Descriptors.FileDescriptor) loader
						.loadClass(options.javaPackage() + "." + options.outerClassName()).getMethod("getDescriptor")
						.invoke(null);
				var service = (BindableService) loader
						.loadClass(options.javaPackage() + "." + options.serviceName() + "Handler")
						.getConstructor(ExternalJavaPolicy.class).newInstance(policy);
				return new TypedGrpcTckExecution(loader, schema,
						compilation.optimizedRuntimeModel().orElseThrow().model(), service.bindService(),
						compilation.compiledModel().orElseThrow().inputSlots(), invocationMetadata(compilation));
			} catch (Throwable failure) {
				loader.close();
				throw failure;
			}
		} catch (Exception failure) {
			throw new PreparationFailure(failure);
		}
	}

	private static Path tool(String prefix, String property) throws Exception {
		String configured = System.getProperty(property);
		if (configured != null)
			return Path.of(configured).toAbsolutePath();
		Path directory = Path.of("../dmn-grpc/target/protoc-plugins");
		try (var files = Files.list(directory)) {
			return files.filter(file -> file.getFileName().toString().startsWith(prefix))
					.filter(file -> !prefix.equals("protoc-")
							|| !file.getFileName().toString().startsWith("protoc-gen-"))
					.sorted().findFirst().orElseThrow(() -> new IllegalStateException(
							"Missing protobuf tool; install dmn-grpc or set " + property))
					.toAbsolutePath();
		}
	}

	private static void run(List<String> command, Path directory) throws Exception {
		Path log = directory.resolve("protoc.log");
		Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
		try {
			if (!process.waitFor(60, TimeUnit.SECONDS))
				throw new IllegalStateException("Typed protoc exceeded 60 seconds");
			if (process.exitValue() != 0)
				throw new IllegalStateException(Files.readString(log));
		} finally {
			if (process.isAlive())
				process.destroyForcibly();
		}
	}

	Map<String, Object> execute(Map<String, Object> inputs, Map<String, Integer> requested, long timeoutSeconds,
			Runnable requestSent, Runnable responseReceived) throws Exception {
		var input = inputMessage(inputs);
		var actual = new LinkedHashMap<String, Object>();
		for (var output : requested.entrySet()) {
			int slot = output.getValue();
			var method = schema.getServices().getFirst().findMethodByName("EvaluateDecision_" + slot);
			if (method == null)
				throw new IllegalStateException("Missing typed RPC for decision " + output.getKey());
			var request = DynamicMessage.newBuilder(method.getInputType());
			request.setField(method.getInputType().findFieldByName("inputs"), input);
			var response = call(method, request.build(), timeoutSeconds, requestSent, responseReceived);
			RuntimeType type = model.decisions().stream().filter(decision -> decision.resultSlot() == slot).findFirst()
					.orElseThrow().type();
			actual.put(output.getKey(), DmnTypedGrpcValueConverter.read(response, "result", type));
		}
		return actual;
	}

	private DynamicMessage inputMessage(Map<String, Object> inputs) {
		var input = DynamicMessage.newBuilder(schema.findMessageTypeByName("InputData"));
		for (var parameter : inputSlots.entrySet()) {
			if (inputs.containsKey(parameter.getKey())) {
				RuntimeType type = model.inputs().stream().filter(value -> value.valueSlot() == parameter.getValue())
						.findFirst().orElseThrow().type();
				DmnTypedGrpcValueConverter.write(input, "input_" + parameter.getValue(), type,
						inputs.get(parameter.getKey()));
			}
		}
		return input.build();
	}

	private DynamicMessage call(Descriptors.MethodDescriptor method, DynamicMessage request, long timeoutSeconds,
			Runnable requestSent, Runnable responseReceived) throws Exception {
		var rpc = MethodDescriptor.<DynamicMessage, DynamicMessage>newBuilder()
				.setType(MethodDescriptor.MethodType.UNARY).setFullMethodName(serviceName + "/" + method.getName())
				.setRequestMarshaller(ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(method.getInputType())))
				.setResponseMarshaller(ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(method.getOutputType())))
				.build();
		requestSent.run();
		var response = ClientCalls.blockingUnaryCall(transport().channel, rpc,
				CallOptions.DEFAULT.withDeadlineAfter(timeoutSeconds, TimeUnit.SECONDS), request);
		responseReceived.run();
		if (!DmnTypedGrpcValueConverter.has(response, "result"))
			throw new AssertionError("Typed RPC omitted result");
		return response;
	}

	Map<String, Object> executeInvocation(String name, Map<String, Object> inputs, long timeoutSeconds,
			Runnable requestSent, Runnable responseReceived) throws Exception {
		var invocation = Objects.requireNonNull(invocations.get(name), "Unknown typed invocable " + name);
		var method = schema.getServices().getFirst()
				.findMethodByName("InvokeKnowledge_" + invocation.bkm().resultSlot());
		if (method == null)
			throw new IllegalStateException("Missing typed invocation RPC " + name);
		var request = DynamicMessage.newBuilder(method.getInputType());
		request.setField(method.getInputType().findFieldByName("inputs"), inputMessage(inputs));
		var argumentField = method.getInputType().findFieldByName("arguments");
		var arguments = request.newBuilderForField(argumentField);
		var parameters = invocation.bkm().function().orElseThrow().parameters();
		for (int i = 0; i < parameters.size(); i++) {
			var parameter = parameters.get(i);
			if (inputs.containsKey(parameter.name()))
				DmnTypedGrpcValueConverter.write(arguments, "argument_" + i, parameter.type(),
						inputs.get(parameter.name()));
		}
		request.setField(argumentField, arguments.build());
		var supplied = method.getInputType().findFieldByName("supplied_argument_names");
		inputs.keySet().forEach(value -> request.addRepeatedField(supplied, value));
		var response = call(method, request.build(), timeoutSeconds, requestSent, responseReceived);
		RuntimeType type = invocation.bkm().function().orElseThrow().type();
		RuntimeType resultType = type.kind() == RuntimeTypeKind.FUNCTION && type.returnType() != null
				? type.returnType()
				: RuntimeType.scalar(RuntimeTypeKind.ANY);
		Object result = DmnTypedGrpcValueConverter.read(response, "result", resultType);
		var actual = new LinkedHashMap<String, Object>();
		for (String output : invocation.outputs()) {
			if (invocation.outputs().size() > 1 && result != null
					&& (!(result instanceof Map<?, ?> map) || !map.containsKey(output)))
				throw new AssertionError("Typed invocation omitted declared output " + output);
			actual.put(output,
					invocation.outputs().size() == 1
							? result
							: result == null ? null : ((Map<?, ?>) result).get(output));
		}
		return actual;
	}

	private static Map<String, Invocation> invocationMetadata(DmnCompilationResult compilation) {
		var slots = DmnToolkitTckEngine.getRuntimeBkmSlots(compilation);
		var runtime = compilation.optimizedRuntimeModel().orElseThrow().model();
		var result = new LinkedHashMap<String, Invocation>();
		for (var semantic : compilation.semanticResult().models()) {
			for (var element : semantic.model().getDrgElementsList()) {
				String name;
				var outputs = new ArrayList<String>();
				if (element.hasDecisionService()) {
					name = element.getDecisionService().getNode().getName();
					for (var reference : element.getDecisionService().getOutputDecisionsList()) {
						String id = reference.getHref().substring(reference.getHref().lastIndexOf('#') + 1);
						outputs.add(semantic.model().getDrgElementsList().stream()
								.filter(node -> node.hasDecision() && node.getDecision().getNode().getId().equals(id))
								.map(node -> node.getDecision().getNode().getName()).findFirst().orElseThrow());
					}
				} else if (element.hasBusinessKnowledgeModel()) {
					name = element.getBusinessKnowledgeModel().getNode().getName();
					outputs.add(name);
				} else
					continue;
				Integer slot = slots.get(name);
				if (slot == null)
					continue;
				RuntimeBkm bkm = runtime.businessKnowledgeModels().stream().filter(value -> value.resultSlot() == slot)
						.findFirst().orElseThrow();
				result.put(name, new Invocation(bkm, List.copyOf(outputs)));
			}
		}
		return result;
	}

	private static Host transportUnchecked() {
		return Objects.requireNonNull(host);
	}
	private static synchronized Host transport() throws Exception {
		if (host == null)
			host = new Host();
		return host;
	}
	static synchronized void closeTransport() throws Exception {
		if (host == null)
			return;
		Host closing = host;
		host = null;
		closing.channel.shutdownNow();
		closing.server.shutdownNow();
		closing.channel.awaitTermination(5, TimeUnit.SECONDS);
		closing.server.awaitTermination(5, TimeUnit.SECONDS);
	}
	private static final class Host {
		final Map<String, ServerServiceDefinition> services = new ConcurrentHashMap<>();
		final Map<String, ServerMethodDefinition<?, ?>> methods = new ConcurrentHashMap<>();
		final Server server;
		final ManagedChannel channel;
		Host() throws Exception {
			var registry = new HandlerRegistry() {
				@Override
				public List<ServerServiceDefinition> getServices() {
					return List.copyOf(services.values());
				}
				@Override
				public ServerMethodDefinition<?, ?> lookupMethod(String method, String authority) {
					return methods.get(method);
				}
			};
			server = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0)).directExecutor()
					.fallbackHandlerRegistry(registry).build().start();
			channel = NettyChannelBuilder.forAddress("127.0.0.1", server.getPort()).usePlaintext().directExecutor()
					.build();
		}
	}
	@Override
	public void close() throws Exception {
		if (host != null) {
			host.services.remove(serviceName);
			host.methods.keySet().removeIf(method -> method.startsWith(serviceName + "/"));
		}
		loader.close();
	}
}
