/**
 * Immutable, process-local Runtime IR contracts and lowering/optimization
 * passes.
 *
 * <p>
 * These Java records are not a persistence or wire format. Their record shapes,
 * class names, enum ordinals, and expression traversal ordinals carry no
 * durable compatibility guarantee. A future cache or deployment transport
 * requires a separate explicitly versioned schema as defined by ADR-0025.
 */
package io.finmsg.dmn.ir;
