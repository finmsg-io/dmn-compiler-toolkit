package io.finmsg.dmn.tck;

import io.finmsg.dmn.runtime.ExternalJavaPolicy;
import io.finmsg.dmn.runtime.ExternalJavaPolicy.Method;
import java.util.Set;

/** Exact targets in official 0076, including deliberately missing targets. */
final class OfficialExternalJavaPolicy {
	static final ExternalJavaPolicy POLICY = ExternalJavaPolicy.allowOnly(Set.of(
			new Method("java.lang.Math", "cos(double)"), new Method("java.lang.Math", "foo(double)"),
			new Method("java.lang.Foo", "valueOf(double)"),
			new Method("java.lang.Math", "max(java.lang.String, java.lang.String)"),
			new Method("java.lang.Math", "max(double,double)"), new Method("java.lang.Math", "max(double, double)"),
			new Method("java.lang.Math", "max(int,int)"), new Method("java.lang.Math", "max(long,long)"),
			new Method("java.lang.Math", "max(float,float)"), new Method("java.lang.Short", "valueOf(short)"),
			new Method("java.lang.Byte", "valueOf(byte)"), new Method("java.lang.String", "valueOf(char)"),
			new Method("java.lang.Integer", "valueOf(java.lang.String)"),
			new Method("java.lang.Float", "valueOf(java.lang.String)"),
			new Method("java.lang.Double", "valueOf(java.lang.String)"),
			new Method("java.lang.String", "format(java.lang.String, [Ljava.lang.Object;)")));
}
