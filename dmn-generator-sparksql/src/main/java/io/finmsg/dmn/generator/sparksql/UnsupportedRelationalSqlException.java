package io.finmsg.dmn.generator.sparksql;

/**
 * Exception thrown when a DMN model contains dynamic functional programming
 * constructs (e.g. curried functions or dynamic function pointers in contexts)
 * that cannot be compiled into pure relational ANSI/Spark SQL without UDFs.
 */
public final class UnsupportedRelationalSqlException extends RuntimeException {

	public UnsupportedRelationalSqlException(String message) {
		super(message);
	}

	public UnsupportedRelationalSqlException(String message, Throwable cause) {
		super(message, cause);
	}
}
