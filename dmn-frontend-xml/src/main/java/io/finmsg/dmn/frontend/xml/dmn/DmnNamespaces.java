package io.finmsg.dmn.frontend.xml.dmn;

public final class DmnNamespaces {

  public static final String DMN_1_5 = "https://www.omg.org/spec/DMN/20230324/MODEL/";

  public static final String DMN_1_6 = "https://www.omg.org/spec/DMN/20240513/MODEL/";

  private DmnNamespaces() {}

  public static DmnVersion detect(String namespaceUri) {

    return switch (namespaceUri) {

      //            case "https://www.omg.org/spec/DMN/20240513/MODEL/" ->
      //                    DmnVersion.DMN_1_7;

      case DMN_1_6 -> DmnVersion.DMN_1_6;

      case DMN_1_5 -> DmnVersion.DMN_1_5;

      case "https://www.omg.org/spec/DMN/20211108/MODEL/" -> DmnVersion.DMN_1_4;

      case "https://www.omg.org/spec/DMN/20191111/MODEL/" -> DmnVersion.DMN_1_3;

      case "https://www.omg.org/spec/DMN/20180521/MODEL/" -> DmnVersion.DMN_1_2;

      default -> throw new IllegalArgumentException("Unsupported DMN namespace: " + namespaceUri);
    };
  }
}
