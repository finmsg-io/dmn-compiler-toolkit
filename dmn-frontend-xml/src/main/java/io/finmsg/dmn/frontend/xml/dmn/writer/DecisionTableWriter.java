package io.finmsg.dmn.frontend.xml.dmn.writer;

import io.finmsg.dmn.frontend.xml.XmlEmitter;
import io.finmsg.dmn.frontend.xml.XmlWriter;
import io.finmsg.dmn.model.Aggregation;
import io.finmsg.dmn.model.DecisionTable;
import io.finmsg.dmn.model.HitPolicy;
import io.finmsg.dmn.model.Orientation;

public final class DecisionTableWriter implements XmlWriter<DecisionTable> {

  private final NodeWriter nodeWriter = new NodeWriter();
  private final InputClauseWriter inputWriter = new InputClauseWriter();
  private final OutputClauseWriter outputWriter = new OutputClauseWriter();
  private final DecisionRuleWriter ruleWriter = new DecisionRuleWriter();

  @Override
  public void write(XmlEmitter xml, DecisionTable value) {
    xml.startElement("decisionTable");
    nodeWriter.writeAttributes(xml, value.getNode());
    if (value.hasHitPolicy()) {
      xml.attribute("hitPolicy", hitPolicy(value.getHitPolicy().getPolicy()));
      xml.attribute("aggregation", aggregation(value.getHitPolicy().getAggregation()));
    }
    xml.attribute("preferredOrientation", orientation(value.getPreferredOrientation()));
    nodeWriter.writeChildren(xml, value.getNode());
    value.getInputsList().forEach(input -> inputWriter.write(xml, input));
    value.getOutputsList().forEach(output -> outputWriter.write(xml, output));
    value.getAnnotationsList().forEach(annotation -> {
      xml.startElement("annotation");
      nodeWriter.writeAttributes(xml, annotation.getNode());
      nodeWriter.writeChildren(xml, annotation.getNode());
      xml.endElement();
    });
    value.getRulesList().forEach(rule -> ruleWriter.write(xml, rule));
    xml.endElement();
  }

  private static String hitPolicy(HitPolicy value) {
    return switch (value) {
      case HIT_POLICY_UNIQUE -> "UNIQUE";
      case HIT_POLICY_FIRST -> "FIRST";
      case HIT_POLICY_PRIORITY -> "PRIORITY";
      case HIT_POLICY_ANY -> "ANY";
      case HIT_POLICY_COLLECT -> "COLLECT";
      case HIT_POLICY_RULE_ORDER -> "RULE ORDER";
      case HIT_POLICY_OUTPUT_ORDER -> "OUTPUT ORDER";
      case HIT_POLICY_UNSPECIFIED, UNRECOGNIZED -> "";
    };
  }

  private static String aggregation(Aggregation value) {
    return switch (value) {
      case AGGREGATION_SUM -> "SUM";
      case AGGREGATION_MIN -> "MIN";
      case AGGREGATION_MAX -> "MAX";
      case AGGREGATION_COUNT -> "COUNT";
      case AGGREGATION_UNSPECIFIED, UNRECOGNIZED -> "";
    };
  }

  private static String orientation(Orientation value) {
    return switch (value) {
      case ORIENTATION_RULE_AS_ROW -> "Rule-as-Row";
      case ORIENTATION_RULE_AS_COLUMN -> "Rule-as-Column";
      case ORIENTATION_CROSS_TABLE -> "CrossTable";
      case ORIENTATION_UNSPECIFIED, UNRECOGNIZED -> "";
    };
  }
}
