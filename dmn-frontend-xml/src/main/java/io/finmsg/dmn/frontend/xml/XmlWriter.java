package io.finmsg.dmn.frontend.xml;

public interface XmlWriter<T> {

	void write(XmlEmitter xml, T value);
}
