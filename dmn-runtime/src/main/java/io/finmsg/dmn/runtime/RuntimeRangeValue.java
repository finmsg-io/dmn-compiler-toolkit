package io.finmsg.dmn.runtime;

import io.finmsg.dmn.ir.RuntimeRangeBoundary;

public record RuntimeRangeValue(Object lower, Object upper, RuntimeRangeBoundary lowerBoundary,
		RuntimeRangeBoundary upperBoundary) {
}
