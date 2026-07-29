package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.FunctionDefinition;
import io.finmsg.dmn.model.FunctionKind;

public final class FunctionDefinitionReader {

  private final InformationItemReader informationItemReader = new InformationItemReader();
  private final FeelReader feelReader = new FeelReader();

  public FunctionDefinition read(XmlCursor cursor) {
    FunctionDefinition.Builder builder = FunctionDefinition.newBuilder();

    //
    // kind
    //

    if (cursor.hasAttribute("kind")) {
      if (cursor.hasAttribute("kind")) {
        builder.setKind(toFunctionKind(
                cursor.requiredAttribute("kind")));
      }
    }

    if (cursor.firstChild("formalParameter")) {
      do {
        builder.addFormalParameters(
                informationItemReader.read(cursor));
      } while (cursor.nextSibling("formalParameter"));

      cursor.parent();
    }

    if (cursor.firstChild()) {
      builder.setLogic(
              feelReader.read(cursor));
      cursor.parent();
    }    return builder.build();
  }

  private static FunctionKind toFunctionKind(String kind) {

    return switch (kind.toLowerCase()) {
      case "feel" -> FunctionKind.FUNCTION_KIND_FEEL;
      case "java" -> FunctionKind.FUNCTION_KIND_JAVA;
      case "pmml" -> FunctionKind.FUNCTION_KIND_PMML;
      default -> FunctionKind.FUNCTION_KIND_UNSPECIFIED;
    };
  }
}
