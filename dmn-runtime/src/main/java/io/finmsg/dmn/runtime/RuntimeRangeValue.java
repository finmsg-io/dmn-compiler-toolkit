package io.finmsg.dmn.runtime;

import io.finmsg.dmn.ir.RuntimeRangeBoundary;

public record RuntimeRangeValue(Object lower, Object upper, RuntimeRangeBoundary lowerBoundary,
		RuntimeRangeBoundary upperBoundary, boolean lowerAbsent, boolean upperAbsent) {

	/**
	 * Convenience constructor: both endpoints are explicitly provided (or
	 * explicitly null). Use the 6-arg form when you need to track absent
	 * (shorthand) endpoints.
	 */
	public RuntimeRangeValue(Object lower, Object upper, RuntimeRangeBoundary lowerBoundary,
			RuntimeRangeBoundary upperBoundary) {
		this(lower, upper, lowerBoundary, upperBoundary, false, false);
	}
}
