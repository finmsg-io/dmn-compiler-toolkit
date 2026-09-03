package io.finmsg.dmn.compiler;

/** Selects which executable Runtime IR stage is returned by the compiler. */
public enum RuntimeModelMode {
	/** Preserve the lowered Runtime IR without transformational optimization. */
	LOWERED,

	/** Apply the production Runtime IR optimization pipeline. */
	OPTIMIZED
}
