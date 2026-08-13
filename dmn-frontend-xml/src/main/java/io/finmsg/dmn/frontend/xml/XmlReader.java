package io.finmsg.dmn.frontend.xml;

public interface XmlReader<T> {

	T read(XmlCursor cursor);
}
