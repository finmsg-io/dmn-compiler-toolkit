package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.Aggregation;
import io.finmsg.dmn.model.DecisionTable;
import io.finmsg.dmn.model.HitPolicy;
import io.finmsg.dmn.model.HitPolicySpec;
import io.finmsg.dmn.model.Orientation;

public final class DecisionTableReader {

  private final NodeReader nodeReader = new NodeReader();
  private final InputClauseReader inputClauseReader = new InputClauseReader();
  private final OutputClauseReader outputClauseReader = new OutputClauseReader();
  private final DecisionRuleReader decisionRuleReader = new DecisionRuleReader();
  private final AnnotationClauseReader annotationClauseReader = new AnnotationClauseReader();

  public DecisionTable read(XmlCursor cursor) {

    DecisionTable.Builder builder = DecisionTable.newBuilder();

    builder.setNode(nodeReader.read(cursor));

    readHitPolicy(cursor, builder);
    readPreferredOrientation(cursor, builder);

    int ruleIndex = 0;

    if (cursor.firstChild()) {
      do {
        switch (cursor.documentLocalName()) {
          case "input" -> builder.addInputs(inputClauseReader.read(cursor));

          case "output" -> builder.addOutputs(outputClauseReader.read(cursor));

          case "rule" ->
              builder.addRules(
                  decisionRuleReader.read(cursor).toBuilder().setRuleIndex(ruleIndex++).build());

          case "annotation" -> builder.addAnnotations(annotationClauseReader.read(cursor));

          case "documentation", "extensionElements" -> { }

          default -> UnsupportedContent.rejectDmnChild(cursor, "decisionTable");
        }

      } while (cursor.nextSibling());

      cursor.parent();
    }

    return builder.build();
  }

  private void readHitPolicy(XmlCursor cursor, DecisionTable.Builder decisionTable) {

    if (!cursor.hasAttribute("hitPolicy") && !cursor.hasAttribute("aggregation")) {
      return;
    }

    HitPolicySpec.Builder hitPolicy = HitPolicySpec.newBuilder();

    if (cursor.hasAttribute("hitPolicy")) {
      hitPolicy.setPolicy(mapHitPolicy(cursor.requiredAttribute("hitPolicy")));
    }

    if (cursor.hasAttribute("aggregation")) {
      hitPolicy.setAggregation(mapAggregation(cursor.requiredAttribute("aggregation")));
    }

    decisionTable.setHitPolicy(hitPolicy);
  }

  private HitPolicy mapHitPolicy(String value) {
    return switch (value.trim()) {
      case "UNIQUE" -> HitPolicy.HIT_POLICY_UNIQUE;
      case "FIRST" -> HitPolicy.HIT_POLICY_FIRST;
      case "PRIORITY" -> HitPolicy.HIT_POLICY_PRIORITY;
      case "ANY" -> HitPolicy.HIT_POLICY_ANY;
      case "COLLECT" -> HitPolicy.HIT_POLICY_COLLECT;
      case "RULE ORDER" -> HitPolicy.HIT_POLICY_RULE_ORDER;
      case "OUTPUT ORDER" -> HitPolicy.HIT_POLICY_OUTPUT_ORDER;
      default -> HitPolicy.HIT_POLICY_UNSPECIFIED;
    };
  }

  private Aggregation mapAggregation(String value) {
    return switch (value.trim()) {
      case "SUM" -> Aggregation.AGGREGATION_SUM;
      case "MIN" -> Aggregation.AGGREGATION_MIN;
      case "MAX" -> Aggregation.AGGREGATION_MAX;
      case "COUNT" -> Aggregation.AGGREGATION_COUNT;
      default -> Aggregation.AGGREGATION_UNSPECIFIED;
    };
  }

  private void readPreferredOrientation(XmlCursor cursor, DecisionTable.Builder builder) {

    if (!cursor.hasAttribute("preferredOrientation")) {
      return;
    }

    Orientation orientation =
        switch (cursor.requiredAttribute("preferredOrientation")) {
          case "Rule-as-Row" -> Orientation.ORIENTATION_RULE_AS_ROW;
          case "Rule-as-Column" -> Orientation.ORIENTATION_RULE_AS_COLUMN;
          case "CrossTable" -> Orientation.ORIENTATION_CROSS_TABLE;
          default -> Orientation.ORIENTATION_UNSPECIFIED;
        };

    builder.setPreferredOrientation(orientation);
  }
}
