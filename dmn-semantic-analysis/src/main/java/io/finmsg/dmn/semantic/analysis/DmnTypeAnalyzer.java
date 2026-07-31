package io.finmsg.dmn.semantic.analysis;

import io.finmsg.dmn.model.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Applies {@link FeelTypeAnalyzer} to every supported parsed FEEL node in a DMN model. */
public final class DmnTypeAnalyzer {

  private final FeelTypeAnalyzer feelTypes = new FeelTypeAnalyzer();

  public DmnSemanticAnalysisResult analyze(Definitions parsedModel) {
    Objects.requireNonNull(parsedModel, "parsedModel");
    Session session = new Session(parsedModel);
    return new DmnSemanticAnalysisResult(session.analyze(), session.diagnostics);
  }

  private final class Session {
    private final Definitions input;
    private final List<DmnSemanticDiagnostic> diagnostics = new ArrayList<>();
    private final Map<String, ItemDefinition> itemTypes = new LinkedHashMap<>();
    private final Map<String, Symbol> symbolsById = new LinkedHashMap<>();

    private Session(Definitions input) {
      this.input = input;
      input.getItemDefinitionsList().forEach(item -> itemTypes.put(item.getNode().getName(), item));
      for (DrgElement element : input.getDrgElementsList()) {
        switch (element.getElementCase()) {
          case INPUT_DATA -> addSymbol(element.getInputData().getNode(),
              element.getInputData().getVariable().getType());
          case DECISION -> addSymbol(element.getDecision().getNode(),
              element.getDecision().getVariable().getType());
          case BUSINESS_KNOWLEDGE_MODEL -> addSymbol(element.getBusinessKnowledgeModel().getNode(),
              element.getBusinessKnowledgeModel().getVariable().getType());
          default -> { }
        }
      }
    }

    private void addSymbol(Node node, TypeReference type) {
      if (!node.getId().isBlank()) {
        symbolsById.put(node.getId(), new Symbol(node.getName(), type));
      }
    }

    private Definitions analyze() {
      Definitions.Builder output = input.toBuilder();
      for (int i = 0; i < input.getDrgElementsCount(); i++) {
        DrgElement element = input.getDrgElements(i);
        if (element.hasDecision()) {
          output.setDrgElements(i, element.toBuilder().setDecision(typeDecision(element.getDecision())));
        } else if (element.hasBusinessKnowledgeModel()) {
          output.setDrgElements(i, element.toBuilder().setBusinessKnowledgeModel(
              typeBkm(element.getBusinessKnowledgeModel())));
        }
      }
      return output.build();
    }

    private Decision typeDecision(Decision decision) {
      Map<String, TypeReference> scope = new LinkedHashMap<>();
      for (InformationRequirement requirement : decision.getInformationRequirementsList()) {
        switch (requirement.getRequiredCase()) {
          case INPUT -> include(scope, requirement.getInput().getHref());
          case DECISION -> include(scope, requirement.getDecision().getHref());
          default -> { }
        }
      }
      for (KnowledgeRequirement requirement : decision.getKnowledgeRequirementsList()) {
        include(scope, requirement.getRequiredKnowledge().getHref());
      }
      if (!decision.hasLogic()) {
        return decision;
      }
      String path = "definitions/decision[" + decision.getNode().getName() + "]/logic";
      return decision.toBuilder().setLogic(typeLogic(decision.getLogic(), scope, path,
          decision.getNode().getSourceLocation())).build();
    }

    private BusinessKnowledgeModel typeBkm(BusinessKnowledgeModel bkm) {
      if (!bkm.hasFunction() || !bkm.getFunction().hasLogic()) {
        return bkm;
      }
      Map<String, TypeReference> scope = new LinkedHashMap<>();
      bkm.getKnowledgeRequirementsList().forEach(r ->
          include(scope, r.getRequiredKnowledge().getHref()));
      bkm.getFunction().getFormalParametersList().forEach(parameter ->
          scope.put(parameter.getNode().getName(), parameter.getType()));
      FunctionDefinition function = bkm.getFunction().toBuilder()
          .setLogic(typeFeel(bkm.getFunction().getLogic(), scope,
              "definitions/businessKnowledgeModel[" + bkm.getNode().getName() + "]/logic",
              bkm.getNode().getSourceLocation()))
          .build();
      return bkm.toBuilder().setFunction(function).build();
    }

    private void include(Map<String, TypeReference> scope, String href) {
      String id = href.substring(href.lastIndexOf('#') + 1);
      Symbol symbol = symbolsById.get(id);
      if (symbol != null) {
        scope.put(symbol.name, symbol.type);
      }
    }

    private DecisionLogic typeLogic(DecisionLogic logic, Map<String, TypeReference> scope,
        String path, SourceLocation location) {
      return switch (logic.getTypeCase()) {
        case LITERAL_EXPRESSION -> logic.toBuilder().setLiteralExpression(
            typeFeel(logic.getLiteralExpression(), scope, path + "/literalExpression", location))
            .build();
        case DECISION_TABLE -> logic.toBuilder().setDecisionTable(
            typeTable(logic.getDecisionTable(), scope, path + "/decisionTable", location)).build();
        case BOXED_EXPRESSION -> logic.toBuilder().setBoxedExpression(
            typeBoxed(logic.getBoxedExpression(), scope, path + "/boxedExpression", location))
            .build();
        case INVOCATION -> logic.toBuilder().setInvocation(
            typeInvocation(logic.getInvocation(), scope, path + "/invocation", location)).build();
        case TYPE_NOT_SET -> logic;
      };
    }

    private DecisionTable typeTable(DecisionTable table, Map<String, TypeReference> scope,
        String path, SourceLocation location) {
      DecisionTable.Builder output = table.toBuilder();
      for (int i = 0; i < table.getInputsCount(); i++) {
        InputClause inputClause = table.getInputs(i);
        if (inputClause.hasInputExpression()) {
          output.setInputs(i, inputClause.toBuilder().setInputExpression(typeFeel(
              inputClause.getInputExpression(), scope, path + "/input[" + i + "]", location)));
        }
      }
      for (int i = 0; i < table.getRulesCount(); i++) {
        DecisionRule rule = table.getRules(i);
        DecisionRule.Builder typedRule = rule.toBuilder();
        for (int j = 0; j < rule.getInputEntriesCount(); j++) {
          UnaryTest test = rule.getInputEntries(j);
          if (test.hasParsed()) {
            Expression wrapped = Expression.newBuilder().setUnaryTests(test.getParsed().getTests()).build();
            FeelTypeAnalysisResult typed = infer(wrapped, scope,
                path + "/rule[" + i + "]/inputEntry[" + j + "]", location);
            typedRule.setInputEntries(j, test.toBuilder().setParsed(
                test.getParsed().toBuilder().setTests(typed.expression().getUnaryTests())));
          }
        }
        for (int j = 0; j < rule.getOutputEntriesCount(); j++) {
          typedRule.setOutputEntries(j, typeFeel(rule.getOutputEntries(j), scope,
              path + "/rule[" + i + "]/outputEntry[" + j + "]", location));
        }
        output.setRules(i, typedRule);
      }
      return output.build();
    }

    private BoxedExpression typeBoxed(BoxedExpression boxed, Map<String, TypeReference> scope,
        String path, SourceLocation location) {
      if (!boxed.hasParsed() || !boxed.getParsed().hasContext()) {
        return boxed;
      }
      ContextParsed context = boxed.getParsed().getContext();
      ContextParsed.Builder typedContext = context.toBuilder().clearEntries();
      Map<String, TypeReference> local = new LinkedHashMap<>(scope);
      for (int i = 0; i < context.getEntriesCount(); i++) {
        ContextEntryParsed entry = context.getEntries(i);
        ContextEntryParsed.Builder typedEntry = entry.toBuilder();
        TypeReference inferred = TypeReference.getDefaultInstance();
        if (entry.hasExpression() && entry.getExpression().hasFeel()) {
          FeelParsed feel = entry.getExpression().getFeel();
          FeelTypeAnalysisResult result = infer(feel.getAst(), local,
              path + "/context/entry[" + i + "]", location);
          inferred = result.expression().getInferredType();
          typedEntry.setExpression(entry.getExpression().toBuilder().setFeel(
              feel.toBuilder().setAst(result.expression())));
        }
        if (entry.hasVariable() && !entry.getVariable().getNode().getName().isBlank()) {
          TypeReference declared = entry.getVariable().getType();
          local.put(entry.getVariable().getNode().getName(),
              declared.getKindCase() == TypeReference.KindCase.KIND_NOT_SET ? inferred : declared);
        }
        typedContext.addEntries(typedEntry);
      }
      return boxed.toBuilder().setParsed(boxed.getParsed().toBuilder().setContext(typedContext)).build();
    }

    private Invocation typeInvocation(Invocation invocation, Map<String, TypeReference> scope,
        String path, SourceLocation location) {
      Invocation.Builder output = invocation.toBuilder();
      if (invocation.hasExpression()) {
        output.setExpression(typeFeel(invocation.getExpression(), scope, path + "/expression", location));
      }
      for (int i = 0; i < invocation.getBindingsCount(); i++) {
        Binding binding = invocation.getBindings(i);
        if (binding.hasExpression()) {
          output.setBindings(i, binding.toBuilder().setExpression(typeFeel(binding.getExpression(),
              scope, path + "/binding[" + i + "]", location)));
        }
      }
      return output.build();
    }

    private Feel typeFeel(Feel feel, Map<String, TypeReference> scope,
        String path, SourceLocation location) {
      if (!feel.hasParsed()) {
        return feel;
      }
      FeelTypeAnalysisResult result = infer(feel.getParsed().getAst(), scope, path, location);
      return feel.toBuilder().setParsed(feel.getParsed().toBuilder().setAst(result.expression())).build();
    }

    private FeelTypeAnalysisResult infer(Expression expression, Map<String, TypeReference> scope,
        String path, SourceLocation location) {
      FeelTypeAnalysisResult result = feelTypes.analyze(expression,
          new FeelTypeEnvironment(scope, itemTypes), path, location);
      diagnostics.addAll(result.diagnostics());
      return result;
    }
  }

  private record Symbol(String name, TypeReference type) { }
}
