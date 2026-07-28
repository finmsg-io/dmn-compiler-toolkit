package io.finmsg.dmn.frontend.xml.dmn;

public record DmnXmlContext(
    DmnVersion version,
    String modelNamespace,
    String dmndiNamespace,
    String diNamespace,
    String dcNamespace) {}
