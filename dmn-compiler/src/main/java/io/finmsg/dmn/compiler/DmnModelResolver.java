package io.finmsg.dmn.compiler;

/**
 * Policy boundary used by the compiler to locate imported DMN sources.
 * Implementations return every candidate; {@link DmnResolutionResult}
 * normalizes candidate order.
 */
@FunctionalInterface
public interface DmnModelResolver {
	DmnResolutionResult resolve(DmnImportRequest request);
}
