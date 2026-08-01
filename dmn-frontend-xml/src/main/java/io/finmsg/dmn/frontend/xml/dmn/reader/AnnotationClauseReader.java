package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.AnnotationClause;

public final class AnnotationClauseReader {

    private final NodeReader nodeReader = new NodeReader();

    public AnnotationClause read(XmlCursor cursor) {
        AnnotationClause.Builder builder = AnnotationClause.newBuilder();
        builder.setNode(nodeReader.read(cursor));
        UnsupportedContent.validateChildren(
                cursor, "annotation clause", "documentation", "extensionElements");
        return builder.build();
    }
}
