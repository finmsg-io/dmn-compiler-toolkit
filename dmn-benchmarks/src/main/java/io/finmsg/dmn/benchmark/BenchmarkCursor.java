package io.finmsg.dmn.benchmark;

import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

/** Mutable payload cursor owned by one JMH worker thread. */
@State(Scope.Thread)
public class BenchmarkCursor {

	private int index;

	public int next(int size) {
		return Math.floorMod(index++, size);
	}
}
