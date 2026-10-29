package io.finmsg.dmn.tck;

import io.finmsg.dmn.compiler.DmnCompilationResult;
import io.finmsg.dmn.generator.java.*;
import io.finmsg.dmn.grpc.*;
import io.grpc.*;
import io.grpc.stub.MetadataUtils;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.tools.ToolProvider;

/** Executes generated production handlers over loopback HTTP/2 and protobuf. */
final class GrpcTckExecution implements AutoCloseable {
	private static final AtomicInteger IDS = new AtomicInteger();
	private static final Metadata.Key<String> MODEL_HEADER = Metadata.Key.of("x-dmn-tck-model",
			Metadata.ASCII_STRING_MARSHALLER);
	private static final Context.Key<String> MODEL_CONTEXT = Context.key("dmn-tck-model");
	private static Host host;
	private final String route;
	private final URLClassLoader loader;

	private GrpcTckExecution(DmnEvaluationServiceGrpc.DmnEvaluationServiceImplBase service, URLClassLoader loader,
			String route) throws Exception {
		this.route = route;
		this.loader = loader;
		transport().services.put(route, service);
	}

	static GrpcTckExecution prepare(DmnCompilationResult compilation) throws Exception {
		String pkg = "io.finmsg.dmn.tck.grpcgen";
		String name = "GrpcTck" + IDS.incrementAndGet();
		var engine = new DmnJavaGenerator().generate(compilation.optimizedRuntimeModel().orElseThrow(),
				DmnJavaGeneratorOptions.of(pkg, name + "Engine"));
		var handler = new DmnGrpcGenerator().generate(compilation, pkg + "." + name + "Engine",
				DmnGrpcGeneratorOptions.of(pkg, name + "Service"));
		Path directory = Files.createTempDirectory("dmn-grpc-tck-");
		var options = new ArrayList<>(List.of("-encoding", "UTF-8", "-classpath", System.getProperty("java.class.path"),
				"-d", directory.toString()));
		var sources = new LinkedHashMap<String, String>();
		sources.putAll(engine.sources());
		sources.putAll(handler.sources());
		for (var source : sources.entrySet()) {
			Path file = directory.resolve(source.getKey().replace('.', '/') + ".java");
			Files.createDirectories(file.getParent());
			Files.writeString(file, source.getValue(), java.nio.charset.StandardCharsets.UTF_8);
			options.add(file.toString());
		}
		var diagnostics = new java.io.ByteArrayOutputStream();
		if (ToolProvider.getSystemJavaCompiler().run(null, null, diagnostics, options.toArray(String[]::new)) != 0)
			throw new IllegalStateException("Generated gRPC compilation failed: " + diagnostics);
		var loader = new URLClassLoader(new URL[]{directory.toUri().toURL()}, GrpcTckExecution.class.getClassLoader());
		try {
			var service = (DmnEvaluationServiceGrpc.DmnEvaluationServiceImplBase) loader
					.loadClass(pkg + "." + name + "Service")
					.getDeclaredConstructor(io.finmsg.dmn.runtime.ExternalJavaPolicy.class)
					.newInstance(OfficialExternalJavaPolicy.POLICY);
			return new GrpcTckExecution(service, loader, name);
		} catch (Throwable failure) {
			loader.close();
			throw failure;
		}
	}

	DmnEvaluationResponse execute(DmnEvaluationRequest request, long timeoutSeconds) throws Exception {
		var headers = new Metadata();
		headers.put(MODEL_HEADER, route);
		return DmnEvaluationServiceGrpc.newBlockingStub(transport().channel)
				.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
				.withDeadlineAfter(timeoutSeconds, TimeUnit.SECONDS).evaluate(request);
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
		final Map<String, DmnEvaluationServiceGrpc.DmnEvaluationServiceImplBase> services = new ConcurrentHashMap<>();
		final Server server;
		final ManagedChannel channel;

		Host() throws Exception {
			var router = new DmnEvaluationServiceGrpc.DmnEvaluationServiceImplBase() {
				@Override
				public void evaluate(DmnEvaluationRequest request,
						io.grpc.stub.StreamObserver<DmnEvaluationResponse> observer) {
					var handler = services.get(MODEL_CONTEXT.get());
					if (handler == null) {
						observer.onError(
								Status.NOT_FOUND.withDescription("Unknown test-host model route").asRuntimeException());
						return;
					}
					handler.evaluate(request, observer);
				}
			};
			var interceptor = new ServerInterceptor() {
				@Override
				public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
						Metadata headers, ServerCallHandler<ReqT, RespT> next) {
					return Contexts.interceptCall(Context.current().withValue(MODEL_CONTEXT, headers.get(MODEL_HEADER)),
							call, headers, next);
				}
			};
			server = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0)).directExecutor()
					.addService(ServerInterceptors.intercept(router, interceptor)).build().start();
			channel = NettyChannelBuilder.forAddress("127.0.0.1", server.getPort()).usePlaintext().directExecutor()
					.build();
		}
	}

	@Override
	public void close() throws Exception {
		if (host != null)
			host.services.remove(route);
		loader.close();
	}
}
