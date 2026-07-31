package io.finmsg.dmn.semantic.analysis;

import io.finmsg.dmn.model.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** First semantic-analysis pass: declaration collection and FEEL name resolution. */
public final class DmnSemanticAnalyzer {

  private static final Set<String> BUILTIN_NAMES = Set.of(
      "date", "time", "date and time", "duration", "years and months duration",
      "string", "number", "context", "list", "range");

  public DmnSemanticAnalysisResult analyze(Definitions parsedModel) {
    Objects.requireNonNull(parsedModel, "parsedModel");
    Session session = new Session(parsedModel);
    session.analyze();
    return new DmnSemanticAnalysisResult(parsedModel, session.diagnostics);
  }

  private static final class Session {

    private final Definitions model;
    private final List<DmnSemanticDiagnostic> diagnostics = new ArrayList<>();
    private final Map<String, List<Symbol>> globalsByName = new LinkedHashMap<>();
    private final Map<String, Symbol> globalsById = new HashMap<>();
    private final Map<String, ItemDefinition> itemDefinitions = new LinkedHashMap<>();

    private Session(Definitions model) {
      this.model = model;
    }

    private void analyze() {
      collectItemDefinitions();
      collectGlobalSymbols();
      analyzeTypeConstraints();

      for (int i = 0; i < model.getDrgElementsCount(); i++) {
        DrgElement element = model.getDrgElements(i);
        switch (element.getElementCase()) {
          case DECISION -> analyzeDecision(element.getDecision(), i);
          case BUSINESS_KNOWLEDGE_MODEL -> analyzeBkm(element.getBusinessKnowledgeModel(), i);
          case INPUT_DATA, KNOWLEDGE_SOURCE, DECISION_SERVICE, ELEMENT_NOT_SET -> { }
        }
      }
    }

    private void collectItemDefinitions() {
      for (int i = 0; i < model.getItemDefinitionsCount(); i++) {
        ItemDefinition item = model.getItemDefinitions(i);
        String name = item.getNode().getName();
        String path = "definitions/itemDefinition[" + displayName(name, i) + "]";
        if (!name.isBlank() && itemDefinitions.putIfAbsent(name, item) != null) {
          error("DUPLICATE_TYPE", path, "Duplicate item definition '" + name + "'.",
              item.getNode().getSourceLocation());
        }
      }
    }

    private void collectGlobalSymbols() {
      for (int i = 0; i < model.getDrgElementsCount(); i++) {
        DrgElement element = model.getDrgElements(i);
        Symbol symbol = switch (element.getElementCase()) {
          case INPUT_DATA -> symbol(
              element.getInputData().getNode(), element.getInputData().getVariable().getType(),
              SymbolKind.INPUT_DATA);
          case DECISION -> symbol(
              element.getDecision().getNode(), element.getDecision().getVariable().getType(),
              SymbolKind.DECISION);
          case BUSINESS_KNOWLEDGE_MODEL -> symbol(
              element.getBusinessKnowledgeModel().getNode(),
              element.getBusinessKnowledgeModel().getVariable().getType(), SymbolKind.BKM);
          case KNOWLEDGE_SOURCE, DECISION_SERVICE, ELEMENT_NOT_SET -> null;
        };
        if (symbol == null || symbol.name.isBlank()) {
          continue;
        }
        List<Symbol> sameName = globalsByName.computeIfAbsent(symbol.name,
            ignored -> new ArrayList<>());
        sameName.add(symbol);
        if (sameName.size() == 2) {
          error("DUPLICATE_NAME", "definitions/drgElement[" + symbol.name + "]",
              "Duplicate global name '" + symbol.name + "'.", symbol.location);
        }
        if (!symbol.id.isBlank()) {
          globalsById.put(symbol.id, symbol);
        }
      }
    }

    private Symbol symbol(Node node, TypeReference type, SymbolKind kind) {
      return new Symbol(node.getName(), node.getId(), type, kind, node.getSourceLocation());
    }

    private void analyzeTypeConstraints() {
      Scope scope = new Scope(null);
      for (int i = 0; i < model.getItemDefinitionsCount(); i++) {
        ItemDefinition item = model.getItemDefinitions(i);
        String path = "definitions/itemDefinition["
            + displayName(item.getNode().getName(), i) + "]";
        if (item.hasConstraint() && item.getConstraint().hasParsed()) {
          analyzeUnaryTests(item.getConstraint().getParsed().getTests(), scope,
              path + "/typeConstraint", item.getNode().getSourceLocation());
        }
        for (int j = 0; j < item.getComponentsCount(); j++) {
          ItemComponent component = item.getComponents(j);
          if (component.hasConstraint() && component.getConstraint().hasParsed()) {
            analyzeUnaryTests(component.getConstraint().getParsed().getTests(), scope,
                path + "/component[" + displayName(component.getNode().getName(), j)
                    + "]/typeConstraint",
                component.getNode().getSourceLocation());
          }
        }
      }
    }

    private void analyzeDecision(Decision decision, int index) {
      String path = "definitions/decision["
          + displayName(decision.getNode().getName(), index) + "]";
      Scope scope = new Scope(null);
      for (InformationRequirement requirement : decision.getInformationRequirementsList()) {
        switch (requirement.getRequiredCase()) {
          case INPUT -> addRequired(scope, requirement.getInput().getHref());
          case DECISION -> addRequired(scope, requirement.getDecision().getHref());
          case REQUIRED_NOT_SET -> { }
        }
      }
      for (KnowledgeRequirement requirement : decision.getKnowledgeRequirementsList()) {
        addRequired(scope, requirement.getRequiredKnowledge().getHref());
      }
      if (decision.hasLogic()) {
        analyzeDecisionLogic(decision.getLogic(), scope, path + "/logic",
            decision.getNode().getSourceLocation());
      }
    }

    private void analyzeBkm(BusinessKnowledgeModel bkm, int index) {
      String path = "definitions/businessKnowledgeModel["
          + displayName(bkm.getNode().getName(), index) + "]";
      Scope scope = new Scope(null);
      for (KnowledgeRequirement requirement : bkm.getKnowledgeRequirementsList()) {
        addRequired(scope, requirement.getRequiredKnowledge().getHref());
      }
      if (!bkm.hasFunction()) {
        return;
      }
      FunctionDefinition function = bkm.getFunction();
      Scope functionScope = new Scope(scope);
      for (int i = 0; i < function.getFormalParametersCount(); i++) {
        InformationItem parameter = function.getFormalParameters(i);
        define(functionScope, parameter.getNode().getName(), parameter.getType(), SymbolKind.PARAMETER,
            path + "/parameter[" + i + "]", parameter.getNode().getSourceLocation());
      }
      if (function.hasLogic() && function.getLogic().hasParsed()) {
        analyzeExpression(function.getLogic().getParsed().getAst(), functionScope,
            path + "/logic", bkm.getNode().getSourceLocation());
      }
    }

    private void addRequired(Scope scope, String href) {
      Symbol symbol = globalsById.get(referenceId(href));
      if (symbol != null) {
        scope.define(symbol);
      }
    }

    private void analyzeDecisionLogic(
        DecisionLogic logic, Scope scope, String path, SourceLocation location) {
      switch (logic.getTypeCase()) {
        case LITERAL_EXPRESSION -> analyzeFeel(logic.getLiteralExpression(), scope,
            path + "/literalExpression", location);
        case DECISION_TABLE -> analyzeDecisionTable(logic.getDecisionTable(), scope,
            path + "/decisionTable", location);
        case BOXED_EXPRESSION -> analyzeBoxed(logic.getBoxedExpression(), scope,
            path + "/boxedExpression", location);
        case INVOCATION -> analyzeInvocation(logic.getInvocation(), scope,
            path + "/invocation", location);
        case TYPE_NOT_SET -> { }
      }
    }

    private void analyzeDecisionTable(
        DecisionTable table, Scope scope, String path, SourceLocation location) {
      for (int i = 0; i < table.getInputsCount(); i++) {
        InputClause input = table.getInputs(i);
        analyzeFeel(input.getInputExpression(), scope, path + "/input[" + i + "]", location);
        if (input.hasInputValues() && input.getInputValues().hasParsed()) {
          analyzeUnaryTests(input.getInputValues().getParsed().getAst().getUnaryTests(), scope,
              path + "/input[" + i + "]/inputValues", location);
        }
      }
      for (int i = 0; i < table.getOutputsCount(); i++) {
        OutputClause output = table.getOutputs(i);
        if (output.hasOutputValues() && output.getOutputValues().hasParsed()) {
          analyzeUnaryTests(output.getOutputValues().getParsed().getAst().getUnaryTests(), scope,
              path + "/output[" + i + "]/outputValues", location);
        }
        if (output.hasDefaultOutputEntry() && output.getDefaultOutputEntry().hasParsed()) {
          analyzeExpressionParsed(output.getDefaultOutputEntry().getParsed(), scope,
              path + "/output[" + i + "]/defaultOutputEntry", location);
        }
      }
      for (int i = 0; i < table.getRulesCount(); i++) {
        DecisionRule rule = table.getRules(i);
        for (int j = 0; j < rule.getInputEntriesCount(); j++) {
          UnaryTest entry = rule.getInputEntries(j);
          if (entry.hasParsed()) {
            analyzeUnaryTests(entry.getParsed().getTests(), scope,
                path + "/rule[" + i + "]/inputEntry[" + j + "]", location);
          }
        }
        for (int j = 0; j < rule.getOutputEntriesCount(); j++) {
          analyzeFeel(rule.getOutputEntries(j), scope,
              path + "/rule[" + i + "]/outputEntry[" + j + "]", location);
        }
      }
    }

    private void analyzeInvocation(
        Invocation invocation, Scope scope, String path, SourceLocation location) {
      analyzeFeel(invocation.getExpression(), scope, path + "/expression", location);
      for (int i = 0; i < invocation.getBindingsCount(); i++) {
        analyzeFeel(invocation.getBindings(i).getExpression(), scope,
            path + "/binding[" + i + "]", location);
      }
    }

    private void analyzeFeel(Feel feel, Scope scope, String path, SourceLocation location) {
      if (feel.hasParsed()) {
        analyzeExpression(feel.getParsed().getAst(), scope, path, location);
      }
    }

    private void analyzeBoxed(
        BoxedExpression boxed, Scope scope, String path, SourceLocation location) {
      if (!boxed.hasParsed()) {
        return;
      }
      BoxedExpressionParsed parsed = boxed.getParsed();
      switch (parsed.getTypeCase()) {
        case CONTEXT -> analyzeContext(parsed.getContext(), scope, path + "/context", location);
        case RELATION -> {
          for (int i = 0; i < parsed.getRelation().getRowsCount(); i++) {
            RelationRowParsed row = parsed.getRelation().getRows(i);
            for (int j = 0; j < row.getExpressionsCount(); j++) {
              analyzeExpressionParsed(row.getExpressions(j), scope,
                  path + "/relation/row[" + i + "]/cell[" + j + "]", location);
            }
          }
        }
        case LIST -> {
          for (int i = 0; i < parsed.getList().getElementsCount(); i++) {
            analyzeExpressionParsed(parsed.getList().getElements(i), scope,
                path + "/list/element[" + i + "]", location);
          }
        }
        case FUNCTION_DEFINITION -> {
          Scope functionScope = new Scope(scope);
          FunctionDefinitionParsed function = parsed.getFunctionDefinition();
          for (int i = 0; i < function.getParametersCount(); i++) {
            InformationItem parameter = function.getParameters(i);
            define(functionScope, parameter.getNode().getName(), parameter.getType(),
                SymbolKind.PARAMETER, path + "/parameter[" + i + "]", location);
          }
          if (function.hasBody()) {
            analyzeExpressionParsed(function.getBody(), functionScope, path + "/body", location);
          }
        }
        case TYPE_NOT_SET -> { }
      }
    }

    private void analyzeContext(
        ContextParsed context, Scope parent, String path, SourceLocation location) {
      Scope scope = new Scope(parent);
      for (int i = 0; i < context.getEntriesCount(); i++) {
        ContextEntryParsed entry = context.getEntries(i);
        if (entry.hasExpression()) {
          analyzeExpressionParsed(entry.getExpression(), scope,
              path + "/entry[" + i + "]/expression", location);
        }
        if (entry.hasVariable()) {
          InformationItem variable = entry.getVariable();
          define(scope, variable.getNode().getName(), variable.getType(), SymbolKind.LOCAL,
              path + "/entry[" + i + "]", variable.getNode().getSourceLocation());
        }
      }
    }

    private void analyzeExpressionParsed(
        ExpressionParsed parsed, Scope scope, String path, SourceLocation location) {
      switch (parsed.getTypeCase()) {
        case FEEL -> analyzeExpression(parsed.getFeel().getAst(), scope, path, location);
        case BOXED -> analyzeParsedBoxed(parsed.getBoxed(), scope, path, location);
        case TYPE_NOT_SET -> { }
      }
    }

    private void analyzeParsedBoxed(
        BoxedExpressionParsed parsed, Scope scope, String path, SourceLocation location) {
      BoxedExpression wrapper = BoxedExpression.newBuilder().setParsed(parsed).build();
      analyzeBoxed(wrapper, scope, path, location);
    }

    private TypeReference analyzeExpression(
        Expression expression, Scope scope, String path, SourceLocation location) {
      return switch (expression.getNodeCase()) {
        case LITERAL -> TypeReference.getDefaultInstance();
        case NAME -> resolveName(expression.getName().getName(), scope, path, location);
        case UNARY -> analyzeExpression(expression.getUnary().getExpression(), scope,
            path + "/unary", location);
        case BINARY -> {
          analyzeExpression(expression.getBinary().getLeft(), scope, path + "/left", location);
          analyzeExpression(expression.getBinary().getRight(), scope, path + "/right", location);
          yield TypeReference.getDefaultInstance();
        }
        case FUNCTION_CALL -> {
          for (int i = 0; i < expression.getFunctionCall().getArgumentsCount(); i++) {
            analyzeExpression(expression.getFunctionCall().getArguments(i), scope,
                path + "/argument[" + i + "]", location);
          }
          yield TypeReference.getDefaultInstance();
        }
        case IF_EXPRESSION -> {
          analyzeExpression(expression.getIfExpression().getCondition(), scope,
              path + "/condition", location);
          analyzeExpression(expression.getIfExpression().getThenExpression(), scope,
              path + "/then", location);
          analyzeExpression(expression.getIfExpression().getElseExpression(), scope,
              path + "/else", location);
          yield TypeReference.getDefaultInstance();
        }
        case CONTEXT -> analyzeFeelContext(expression.getContext(), scope, path, location);
        case LIST -> {
          for (int i = 0; i < expression.getList().getElementsCount(); i++) {
            analyzeExpression(expression.getList().getElements(i), scope,
                path + "/element[" + i + "]", location);
          }
          yield TypeReference.getDefaultInstance();
        }
        case FOR_EXPRESSION -> analyzeFor(expression.getForExpression(), scope, path, location);
        case QUANTIFIED -> analyzeQuantified(expression.getQuantified(), scope, path, location);
        case FILTER -> {
          analyzeExpression(expression.getFilter().getSource(), scope, path + "/source", location);
          analyzeExpression(expression.getFilter().getFilter(), scope, path + "/filter", location);
          yield TypeReference.getDefaultInstance();
        }
        case PATH -> analyzePath(expression.getPath(), scope, path, location);
        case DESCENDANT -> analyzeDescendant(expression.getDescendant(), scope, path, location);
        case INVOCATION -> analyzeAstInvocation(expression.getInvocation(), scope, path, location);
        case RANGE -> {
          if (expression.getRange().hasLower()) {
            analyzeExpression(expression.getRange().getLower(), scope, path + "/lower", location);
          }
          if (expression.getRange().hasUpper()) {
            analyzeExpression(expression.getRange().getUpper(), scope, path + "/upper", location);
          }
          yield TypeReference.getDefaultInstance();
        }
        case BETWEEN -> {
          analyzeExpression(expression.getBetween().getValue(), scope, path + "/value", location);
          analyzeExpression(expression.getBetween().getLower(), scope, path + "/lower", location);
          analyzeExpression(expression.getBetween().getUpper(), scope, path + "/upper", location);
          yield TypeReference.getDefaultInstance();
        }
        case IN -> {
          analyzeExpression(expression.getIn().getValue(), scope, path + "/value", location);
          analyzeUnaryTests(expression.getIn().getTests(), scope, path + "/tests", location);
          yield TypeReference.getDefaultInstance();
        }
        case INSTANCE_OF -> analyzeExpression(expression.getInstanceOf().getExpression(), scope,
            path + "/expression", location);
        case FUNCTION_DEFINITION -> analyzeAstFunction(
            expression.getFunctionDefinition(), scope, path, location);
        case UNARY_TESTS -> {
          analyzeUnaryTests(expression.getUnaryTests(), scope, path, location);
          yield TypeReference.getDefaultInstance();
        }
        case DECISION_TABLE, NODE_NOT_SET -> TypeReference.getDefaultInstance();
      };
    }

    private TypeReference analyzeFeelContext(
        ContextExpression context, Scope parent, String path, SourceLocation location) {
      Scope scope = new Scope(parent);
      for (int i = 0; i < context.getEntriesCount(); i++) {
        io.finmsg.dmn.model.ContextEntry entry = context.getEntries(i);
        TypeReference type = analyzeExpression(entry.getExpression(), scope,
            path + "/entry[" + i + "]", location);
        define(scope, entry.getName(), type, SymbolKind.LOCAL,
            path + "/entry[" + i + "]", location);
      }
      return TypeReference.getDefaultInstance();
    }

    private TypeReference analyzeFor(
        ForExpression value, Scope parent, String path, SourceLocation location) {
      Scope scope = new Scope(parent);
      for (int i = 0; i < value.getIterationsCount(); i++) {
        IterationContext iteration = value.getIterations(i);
        TypeReference type = analyzeExpression(iteration.getStart(), scope,
            path + "/iteration[" + i + "]/in", location);
        if (iteration.hasEnd()) {
          analyzeExpression(iteration.getEnd(), scope,
              path + "/iteration[" + i + "]/end", location);
        }
        define(scope, iteration.getVariable(), type, SymbolKind.LOCAL,
            path + "/iteration[" + i + "]", location);
      }
      return analyzeExpression(value.getReturnExpression(), scope, path + "/return", location);
    }

    private TypeReference analyzeQuantified(
        QuantifiedExpression value, Scope parent, String path, SourceLocation location) {
      Scope scope = new Scope(parent);
      for (int i = 0; i < value.getBindingsCount(); i++) {
        IterationBinding binding = value.getBindings(i);
        TypeReference type = analyzeExpression(binding.getIn(), scope,
            path + "/binding[" + i + "]/in", location);
        define(scope, binding.getVariable(), type, SymbolKind.LOCAL,
            path + "/binding[" + i + "]", location);
      }
      analyzeExpression(value.getSatisfies(), scope, path + "/satisfies", location);
      return TypeReference.getDefaultInstance();
    }

    private TypeReference analyzePath(
        PathExpression value, Scope scope, String path, SourceLocation location) {
      TypeReference source = analyzeExpression(value.getSource(), scope, path + "/source", location);
      return resolveMember(source, value.getMember(), path, location);
    }

    private TypeReference analyzeDescendant(
        DescendantExpression value, Scope scope, String path, SourceLocation location) {
      TypeReference source = analyzeExpression(value.getSource(), scope, path + "/source", location);
      return resolveMember(source, value.getMember(), path, location);
    }

    private TypeReference analyzeAstInvocation(
        InvocationExpression value, Scope scope, String path, SourceLocation location) {
      if (!(value.getTarget().hasName()
          && BUILTIN_NAMES.contains(value.getTarget().getName().getName()))) {
        analyzeExpression(value.getTarget(), scope, path + "/target", location);
      }
      for (int i = 0; i < value.getArgumentsCount(); i++) {
        analyzeExpression(value.getArguments(i).getExpression(), scope,
            path + "/argument[" + i + "]", location);
      }
      for (int i = 0; i < value.getPositionalArgumentsCount(); i++) {
        analyzeExpression(value.getPositionalArguments(i), scope,
            path + "/argument[" + i + "]", location);
      }
      return TypeReference.getDefaultInstance();
    }

    private TypeReference analyzeAstFunction(
        FunctionDefinitionExpression value, Scope parent, String path, SourceLocation location) {
      Scope scope = new Scope(parent);
      for (int i = 0; i < value.getParametersCount(); i++) {
        define(scope, value.getParameters(i).getName(), TypeReference.getDefaultInstance(),
            SymbolKind.PARAMETER, path + "/parameter[" + i + "]", location);
      }
      return analyzeExpression(value.getBody(), scope, path + "/body", location);
    }

    private void analyzeUnaryTests(
        UnaryTestsExpression tests, Scope scope, String path, SourceLocation location) {
      for (int i = 0; i < tests.getTestsCount(); i++) {
        PositiveUnaryTest test = tests.getTests(i);
        switch (test.getTypeCase()) {
          case COMPARISON -> analyzeExpression(test.getComparison().getEndpoint(), scope,
              path + "/test[" + i + "]", location);
          case RANGE -> {
            if (test.getRange().hasLower()) {
              analyzeExpression(test.getRange().getLower(), scope,
                  path + "/test[" + i + "]/lower", location);
            }
            if (test.getRange().hasUpper()) {
              analyzeExpression(test.getRange().getUpper(), scope,
                  path + "/test[" + i + "]/upper", location);
            }
          }
          case EXPRESSION -> analyzeExpression(test.getExpression(), scope,
              path + "/test[" + i + "]", location);
          case TYPE_NOT_SET -> { }
        }
      }
    }

    private TypeReference resolveName(
        String name, Scope scope, String path, SourceLocation location) {
      List<Symbol> resolved = scope.resolve(name);
      if (resolved != null) {
        if (resolved.size() > 1) {
          error("AMBIGUOUS_NAME", path, "Name '" + name + "' is ambiguous.", location);
          return TypeReference.getDefaultInstance();
        }
        return resolved.getFirst().type;
      }
      if (globalsByName.containsKey(name)) {
        error("UNAVAILABLE_NAME", path,
            "Name '" + name + "' exists but is not available through a requirement.", location);
      } else {
        error("UNKNOWN_NAME", path, "Unknown name '" + name + "'.", location);
      }
      return TypeReference.getDefaultInstance();
    }

    private TypeReference resolveMember(
        TypeReference source, String member, String path, SourceLocation location) {
      if (!source.hasNamed()) {
        return TypeReference.getDefaultInstance();
      }
      ItemDefinition item = itemDefinitions.get(source.getNamed().getName());
      if (item == null) {
        error("UNKNOWN_TYPE", path,
            "Unknown type '" + source.getNamed().getName() + "'.", location);
        return TypeReference.getDefaultInstance();
      }
      List<ItemComponent> matches = item.getComponentsList().stream()
          .filter(component -> component.getNode().getName().equals(member))
          .toList();
      if (matches.isEmpty()) {
        error("INVALID_PROPERTY", path,
            "Type '" + source.getNamed().getName() + "' has no property '" + member + "'.",
            location);
        return TypeReference.getDefaultInstance();
      }
      if (matches.size() > 1) {
        error("AMBIGUOUS_PROPERTY", path,
            "Property '" + member + "' is ambiguous on type '"
                + source.getNamed().getName() + "'.", location);
        return TypeReference.getDefaultInstance();
      }
      return matches.getFirst().getType();
    }

    private void define(
        Scope scope, String name, TypeReference type, SymbolKind kind,
        String path, SourceLocation location) {
      if (name.isBlank()) {
        return;
      }
      if (!scope.define(new Symbol(name, "", type, kind, location))) {
        error("DUPLICATE_NAME", path, "Duplicate name '" + name + "'.", location);
      }
    }

    private void error(String code, String path, String message, SourceLocation location) {
      diagnostics.add(new DmnSemanticDiagnostic(code, path, message, location));
    }

    private static String referenceId(String href) {
      int hash = href.lastIndexOf('#');
      return hash >= 0 ? href.substring(hash + 1) : href;
    }

    private static String displayName(String name, int index) {
      return name.isBlank() ? Integer.toString(index) : name;
    }
  }

  private enum SymbolKind {
    INPUT_DATA,
    DECISION,
    BKM,
    PARAMETER,
    LOCAL
  }

  private record Symbol(
      String name,
      String id,
      TypeReference type,
      SymbolKind kind,
      SourceLocation location) {
  }

  private static final class Scope {
    private final Scope parent;
    private final Map<String, List<Symbol>> symbols = new LinkedHashMap<>();

    private Scope(Scope parent) {
      this.parent = parent;
    }

    private boolean define(Symbol symbol) {
      List<Symbol> values = symbols.computeIfAbsent(symbol.name, ignored -> new ArrayList<>());
      values.add(symbol);
      return values.size() == 1;
    }

    private List<Symbol> resolve(String name) {
      List<Symbol> local = symbols.get(name);
      if (local != null) {
        return local;
      }
      return parent == null ? null : parent.resolve(name);
    }
  }
}
