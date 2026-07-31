package io.finmsg.dmn.feel.parser;

import io.finmsg.dmn.model.Binding;
import io.finmsg.dmn.model.BoxedExpression;
import io.finmsg.dmn.model.BoxedExpressionParsed;
import io.finmsg.dmn.model.BoxedExpressionText;
import io.finmsg.dmn.model.BusinessKnowledgeModel;
import io.finmsg.dmn.model.ContextEntryParsed;
import io.finmsg.dmn.model.ContextParsed;
import io.finmsg.dmn.model.ContextText;
import io.finmsg.dmn.model.Decision;
import io.finmsg.dmn.model.DecisionLogic;
import io.finmsg.dmn.model.DecisionRule;
import io.finmsg.dmn.model.DecisionTable;
import io.finmsg.dmn.model.Definitions;
import io.finmsg.dmn.model.DrgElement;
import io.finmsg.dmn.model.Expression;
import io.finmsg.dmn.model.ExpressionNode;
import io.finmsg.dmn.model.ExpressionParsed;
import io.finmsg.dmn.model.ExpressionText;
import io.finmsg.dmn.model.Feel;
import io.finmsg.dmn.model.FeelParsed;
import io.finmsg.dmn.model.FeelText;
import io.finmsg.dmn.model.FunctionDefinition;
import io.finmsg.dmn.model.FunctionDefinitionParsed;
import io.finmsg.dmn.model.FunctionDefinitionText;
import io.finmsg.dmn.model.FunctionKind;
import io.finmsg.dmn.model.InputClause;
import io.finmsg.dmn.model.Invocation;
import io.finmsg.dmn.model.ItemComponent;
import io.finmsg.dmn.model.ItemDefinition;
import io.finmsg.dmn.model.ListExpressionParsed;
import io.finmsg.dmn.model.ListExpressionText;
import io.finmsg.dmn.model.OutputClause;
import io.finmsg.dmn.model.RelationColumnParsed;
import io.finmsg.dmn.model.RelationParsed;
import io.finmsg.dmn.model.RelationRowParsed;
import io.finmsg.dmn.model.RelationText;
import io.finmsg.dmn.model.TypeConstraint;
import io.finmsg.dmn.model.UnaryTest;
import io.finmsg.dmn.model.UnaryTestParsed;
import java.util.Objects;

/**
 * Parses every textual FEEL node in a DMN protobuf model.
 *
 * <p>The input model is never changed. The returned model is a copied protobuf tree in which
 * textual FEEL representations are replaced with parsed representations.
 */
public final class DmnFeelParser {

  private final FeelParserFacade parser;

  public DmnFeelParser() {
    this(new FeelParserFacade());
  }

  DmnFeelParser(FeelParserFacade parser) {
    this.parser = Objects.requireNonNull(parser, "parser");
  }

  public Definitions parse(Definitions semanticModel) {
    Objects.requireNonNull(semanticModel, "semanticModel");

    Definitions.Builder parsed = semanticModel.toBuilder();

    for (int i = 0; i < semanticModel.getItemDefinitionsCount(); i++) {
      parsed.setItemDefinitions(i, parseItemDefinition(semanticModel.getItemDefinitions(i)));
    }

    for (int i = 0; i < semanticModel.getDrgElementsCount(); i++) {
      parsed.setDrgElements(i, parseDrgElement(semanticModel.getDrgElements(i)));
    }

    return parsed.build();
  }

  private ItemDefinition parseItemDefinition(ItemDefinition value) {
    ItemDefinition.Builder parsed = value.toBuilder();

    for (int i = 0; i < value.getComponentsCount(); i++) {
      parsed.setComponents(i, parseItemComponent(value.getComponents(i)));
    }

    if (value.hasConstraint()) {
      parsed.setConstraint(parseTypeConstraint(value.getConstraint()));
    }

    return parsed.build();
  }

  private ItemComponent parseItemComponent(ItemComponent value) {
    ItemComponent.Builder parsed = value.toBuilder();
    if (value.hasConstraint()) {
      parsed.setConstraint(parseTypeConstraint(value.getConstraint()));
    }
    return parsed.build();
  }

  private TypeConstraint parseTypeConstraint(TypeConstraint value) {
    return switch (value.getRepresentationCase()) {
      case TEXT ->
          value.toBuilder().setParsed(parseUnaryTests(value.getText())).build();
      case PARSED, REPRESENTATION_NOT_SET -> value;
    };
  }

  private DrgElement parseDrgElement(DrgElement value) {
    return switch (value.getElementCase()) {
      case DECISION -> value.toBuilder().setDecision(parseDecision(value.getDecision())).build();
      case BUSINESS_KNOWLEDGE_MODEL ->
          value.toBuilder()
              .setBusinessKnowledgeModel(
                  parseBusinessKnowledgeModel(value.getBusinessKnowledgeModel()))
              .build();
      case INPUT_DATA, KNOWLEDGE_SOURCE, DECISION_SERVICE, ELEMENT_NOT_SET -> value;
    };
  }

  private Decision parseDecision(Decision value) {
    Decision.Builder parsed = value.toBuilder();
    if (value.hasLogic()) {
      parsed.setLogic(parseDecisionLogic(value.getLogic()));
    }
    return parsed.build();
  }

  private DecisionLogic parseDecisionLogic(DecisionLogic value) {
    return switch (value.getTypeCase()) {
      case LITERAL_EXPRESSION ->
          value.toBuilder()
              .setLiteralExpression(parseFeel(value.getLiteralExpression()))
              .build();
      case DECISION_TABLE ->
          value.toBuilder()
              .setDecisionTable(parseDecisionTable(value.getDecisionTable()))
              .build();
      case BOXED_EXPRESSION ->
          value.toBuilder()
              .setBoxedExpression(parseBoxedExpression(value.getBoxedExpression()))
              .build();
      case INVOCATION ->
          value.toBuilder().setInvocation(parseInvocation(value.getInvocation())).build();
      case TYPE_NOT_SET -> value;
    };
  }

  private BusinessKnowledgeModel parseBusinessKnowledgeModel(BusinessKnowledgeModel value) {
    BusinessKnowledgeModel.Builder parsed = value.toBuilder();
    if (value.hasFunction()) {
      parsed.setFunction(parseFunctionDefinition(value.getFunction()));
    }
    return parsed.build();
  }

  private FunctionDefinition parseFunctionDefinition(FunctionDefinition value) {
    FunctionDefinition.Builder parsed = value.toBuilder();
    if (value.hasLogic()
        && value.getKind() != FunctionKind.FUNCTION_KIND_JAVA
        && value.getKind() != FunctionKind.FUNCTION_KIND_PMML) {
      parsed.setLogic(parseFeel(value.getLogic()));
    }
    return parsed.build();
  }

  private Invocation parseInvocation(Invocation value) {
    Invocation.Builder parsed = value.toBuilder();

    if (value.hasExpression()) {
      parsed.setExpression(parseFeel(value.getExpression()));
    }

    for (int i = 0; i < value.getBindingsCount(); i++) {
      parsed.setBindings(i, parseBinding(value.getBindings(i)));
    }

    return parsed.build();
  }

  private Binding parseBinding(Binding value) {
    Binding.Builder parsed = value.toBuilder();
    if (value.hasExpression()) {
      parsed.setExpression(parseFeel(value.getExpression()));
    }
    return parsed.build();
  }

  private DecisionTable parseDecisionTable(DecisionTable value) {
    DecisionTable.Builder parsed = value.toBuilder();

    for (int i = 0; i < value.getInputsCount(); i++) {
      parsed.setInputs(i, parseInputClause(value.getInputs(i)));
    }

    for (int i = 0; i < value.getOutputsCount(); i++) {
      parsed.setOutputs(i, parseOutputClause(value.getOutputs(i)));
    }

    for (int i = 0; i < value.getRulesCount(); i++) {
      parsed.setRules(i, parseDecisionRule(value.getRules(i)));
    }

    return parsed.build();
  }

  private InputClause parseInputClause(InputClause value) {
    InputClause.Builder parsed = value.toBuilder();
    if (value.hasInputExpression()) {
      parsed.setInputExpression(parseFeel(value.getInputExpression()));
    }
    if (value.hasInputValues()) {
      parsed.setInputValues(parseFeelUnaryTests(value.getInputValues()));
    }
    return parsed.build();
  }

  private OutputClause parseOutputClause(OutputClause value) {
    OutputClause.Builder parsed = value.toBuilder();
    if (value.hasOutputValues()) {
      parsed.setOutputValues(parseFeelUnaryTests(value.getOutputValues()));
    }
    if (value.hasDefaultOutputEntry()) {
      parsed.setDefaultOutputEntry(parseExpressionNode(value.getDefaultOutputEntry()));
    }
    return parsed.build();
  }

  private DecisionRule parseDecisionRule(DecisionRule value) {
    DecisionRule.Builder parsed = value.toBuilder();

    for (int i = 0; i < value.getInputEntriesCount(); i++) {
      parsed.setInputEntries(i, parseUnaryTest(value.getInputEntries(i)));
    }

    for (int i = 0; i < value.getOutputEntriesCount(); i++) {
      parsed.setOutputEntries(i, parseFeel(value.getOutputEntries(i)));
    }

    return parsed.build();
  }

  private UnaryTest parseUnaryTest(UnaryTest value) {
    return switch (value.getRepresentationCase()) {
      case TEXT -> value.toBuilder().setParsed(parseUnaryTests(value.getText())).build();
      case PARSED, REPRESENTATION_NOT_SET -> value;
    };
  }

  private Feel parseFeel(Feel value) {
    return switch (value.getRepresentationCase()) {
      case TEXT ->
          value.toBuilder().setParsed(parser.parseExpressionAst(value.getText().getText())).build();
      case PARSED, REPRESENTATION_NOT_SET -> value;
    };
  }

  private Feel parseFeelUnaryTests(Feel value) {
    return switch (value.getRepresentationCase()) {
      case TEXT -> {
        UnaryTestParsed unaryTests = parseUnaryTests(value.getText());
        FeelParsed parsed =
            FeelParsed.newBuilder()
                .setAst(Expression.newBuilder().setUnaryTests(unaryTests.getTests()))
                .build();
        yield value.toBuilder().setParsed(parsed).build();
      }
      case PARSED, REPRESENTATION_NOT_SET -> value;
    };
  }

  private UnaryTestParsed parseUnaryTests(FeelText value) {
    return parser.parseUnaryTestsAst(value.getText());
  }

  private ExpressionNode parseExpressionNode(ExpressionNode value) {
    return switch (value.getRepresentationCase()) {
      case TEXT -> value.toBuilder().setParsed(parseExpressionText(value.getText())).build();
      case PARSED, REPRESENTATION_NOT_SET -> value;
    };
  }

  private ExpressionParsed parseExpressionText(ExpressionText value) {
    return switch (value.getTypeCase()) {
      case FEEL ->
          ExpressionParsed.newBuilder()
              .setFeel(parser.parseExpressionAst(value.getFeel().getText()))
              .build();
      case BOXED ->
          ExpressionParsed.newBuilder().setBoxed(parseBoxedExpressionText(value.getBoxed())).build();
      case TYPE_NOT_SET -> ExpressionParsed.getDefaultInstance();
    };
  }

  private BoxedExpression parseBoxedExpression(BoxedExpression value) {
    return switch (value.getRepresentationCase()) {
      case TEXT ->
          value.toBuilder().setParsed(parseBoxedExpressionText(value.getText())).build();
      case PARSED, REPRESENTATION_NOT_SET -> value;
    };
  }

  private BoxedExpressionParsed parseBoxedExpressionText(BoxedExpressionText value) {
    return switch (value.getTypeCase()) {
      case CONTEXT ->
          BoxedExpressionParsed.newBuilder().setContext(parseContext(value.getContext())).build();
      case RELATION ->
          BoxedExpressionParsed.newBuilder().setRelation(parseRelation(value.getRelation())).build();
      case LIST ->
          BoxedExpressionParsed.newBuilder().setList(parseList(value.getList())).build();
      case FUNCTION_DEFINITION ->
          BoxedExpressionParsed.newBuilder()
              .setFunctionDefinition(parseFunctionDefinitionText(value.getFunctionDefinition()))
              .build();
      case TYPE_NOT_SET -> BoxedExpressionParsed.getDefaultInstance();
    };
  }

  private ContextParsed parseContext(ContextText value) {
    ContextParsed.Builder parsed = ContextParsed.newBuilder();
    for (var entry : value.getEntriesList()) {
      ContextEntryParsed.Builder parsedEntry =
          ContextEntryParsed.newBuilder().setVariable(entry.getVariable());
      if (entry.hasExpression()) {
        parsedEntry.setExpression(parseExpressionText(entry.getExpression()));
      }
      parsed.addEntries(parsedEntry);
    }
    return parsed.build();
  }

  private RelationParsed parseRelation(RelationText value) {
    RelationParsed.Builder parsed = RelationParsed.newBuilder();

    for (var column : value.getColumnsList()) {
      parsed.addColumns(
          RelationColumnParsed.newBuilder().setVariable(column.getVariable()));
    }

    for (var row : value.getRowsList()) {
      RelationRowParsed.Builder parsedRow = RelationRowParsed.newBuilder();
      for (ExpressionText expression : row.getExpressionsList()) {
        parsedRow.addExpressions(parseExpressionText(expression));
      }
      parsed.addRows(parsedRow);
    }

    return parsed.build();
  }

  private ListExpressionParsed parseList(ListExpressionText value) {
    ListExpressionParsed.Builder parsed = ListExpressionParsed.newBuilder();
    for (ExpressionText element : value.getElementsList()) {
      parsed.addElements(parseExpressionText(element));
    }
    return parsed.build();
  }

  private FunctionDefinitionParsed parseFunctionDefinitionText(FunctionDefinitionText value) {
    FunctionDefinitionParsed.Builder parsed =
        FunctionDefinitionParsed.newBuilder().addAllParameters(value.getParametersList());
    if (value.hasBody()) {
      parsed.setBody(parseExpressionText(value.getBody()));
    }
    return parsed.build();
  }
}
