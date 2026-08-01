package io.finmsg.dmn.semantic.analysis;

import io.finmsg.dmn.model.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Applies {@link FeelTypeAnalyzer} to every supported parsed FEEL node in a DMN model. */
public final class DmnTypeAnalyzer {

  private static final TypeReference ANY = builtin(BuiltinType.BUILTIN_TYPE_ANY);
  private static final TypeReference NUMBER = builtin(BuiltinType.BUILTIN_TYPE_NUMBER);
  private static final TypeReference NULL = builtin(BuiltinType.BUILTIN_TYPE_NULL);
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
      DecisionLogic typedLogic = typeLogic(decision.getLogic(), scope, path,
          decision.getNode().getSourceLocation());
      TypeReference declared = decision.getVariable().getType();
      TypeReference inferred = logicType(typedLogic);
      if (!isUnknown(declared) && !isUnknown(inferred) && !assignable(inferred, declared)) {
        diagnostics.add(new DmnSemanticDiagnostic(
            "DECISION_TYPE_MISMATCH",
            "definitions/decision[" + decision.getNode().getName() + "]/variable",
            "Decision declares type " + typeName(declared)
                + " but its logic produces " + typeName(inferred) + ".",
            decision.getNode().getSourceLocation()));
      }
      return decision.toBuilder().setLogic(typedLogic).build();
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

    private TypeReference logicType(DecisionLogic logic) {
      return switch (logic.getTypeCase()) {
        case LITERAL_EXPRESSION -> feelType(logic.getLiteralExpression());
        case DECISION_TABLE -> decisionTableType(logic.getDecisionTable());
        case BOXED_EXPRESSION -> boxedType(logic.getBoxedExpression());
        case INVOCATION -> invocationType(logic.getInvocation());
        case TYPE_NOT_SET -> TypeReference.getDefaultInstance();
      };
    }

    private TypeReference decisionTableType(DecisionTable table) {
      if (table.getOutputsCount() == 0) {
        return TypeReference.getDefaultInstance();
      }
      TypeReference result;
      if (table.getOutputsCount() == 1) {
        result = decisionTableOutputType(table, 0);
      } else {
        ContextTypeReference.Builder context = ContextTypeReference.newBuilder();
        for (int i = 0; i < table.getOutputsCount(); i++) {
          String name = table.getOutputs(i).getNode().getName();
          context.addEntries(ContextEntryTypeReference.newBuilder()
              .setName(name.isBlank() ? Integer.toString(i) : name)
              .setType(decisionTableOutputType(table, i)));
        }
        result = TypeReference.newBuilder().setContext(context).build();
      }
      return switch (table.getHitPolicy().getPolicy()) {
        case HIT_POLICY_COLLECT -> switch (table.getHitPolicy().getAggregation()) {
          case AGGREGATION_COUNT, AGGREGATION_SUM -> NUMBER;
          case AGGREGATION_MIN, AGGREGATION_MAX -> result;
          case AGGREGATION_UNSPECIFIED, UNRECOGNIZED -> list(result);
        };
        case HIT_POLICY_RULE_ORDER, HIT_POLICY_OUTPUT_ORDER -> list(result);
        default -> result;
      };
    }

    private TypeReference decisionTableOutputType(DecisionTable table, int outputIndex) {
      TypeReference declared = table.getOutputs(outputIndex).getType();
      if (!isUnknown(declared)) {
        return declared;
      }
      TypeReference inferred = TypeReference.getDefaultInstance();
      for (DecisionRule rule : table.getRulesList()) {
        if (outputIndex < rule.getOutputEntriesCount()) {
          inferred = merge(inferred, feelType(rule.getOutputEntries(outputIndex)));
        }
      }
      return inferred;
    }

    private TypeReference boxedType(BoxedExpression boxed) {
      if (!boxed.hasParsed()) {
        return TypeReference.getDefaultInstance();
      }
      BoxedExpressionParsed parsed = boxed.getParsed();
      return switch (parsed.getTypeCase()) {
        case CONTEXT -> parsed.getContext().getEntriesCount() == 0
            ? TypeReference.getDefaultInstance()
            : expressionParsedType(parsed.getContext().getEntries(
                parsed.getContext().getEntriesCount() - 1).getExpression());
        case LIST -> {
          TypeReference element = TypeReference.getDefaultInstance();
          for (ExpressionParsed expression : parsed.getList().getElementsList()) {
            element = merge(element, expressionParsedType(expression));
          }
          yield list(element);
        }
        case FUNCTION_DEFINITION -> {
          FunctionDefinitionParsed function = parsed.getFunctionDefinition();
          FunctionTypeReference.Builder type = FunctionTypeReference.newBuilder();
          for (InformationItem parameter : function.getParametersList()) {
            type.addParameterType(parameter.getType());
          }
          type.setReturnType(expressionParsedType(function.getBody()));
          yield TypeReference.newBuilder().setFunction(type).build();
        }
        case RELATION, TYPE_NOT_SET -> TypeReference.getDefaultInstance();
      };
    }

    private TypeReference expressionParsedType(ExpressionParsed expression) {
      return switch (expression.getTypeCase()) {
        case FEEL -> expression.getFeel().getAst().getInferredType();
        case BOXED -> boxedType(BoxedExpression.newBuilder().setParsed(expression.getBoxed()).build());
        case TYPE_NOT_SET -> TypeReference.getDefaultInstance();
      };
    }

    private TypeReference invocationType(Invocation invocation) {
      TypeReference target = feelType(invocation.getExpression());
      return target.hasFunction() ? target.getFunction().getReturnType() : target;
    }

    private static TypeReference feelType(Feel feel) {
      return feel.hasParsed()
          ? feel.getParsed().getAst().getInferredType()
          : TypeReference.getDefaultInstance();
    }

    private boolean assignable(TypeReference actual, TypeReference expected) {
      if (isUnknown(actual) || isUnknown(expected) || actual.equals(expected)
          || actual.equals(NULL)) {
        return true;
      }
      if (expected.hasNamed()) {
        TypeReference resolved = resolveNamed(expected);
        return !resolved.equals(expected) && assignable(actual, resolved);
      }
      if (actual.hasNamed()) {
        TypeReference resolved = resolveNamed(actual);
        return !resolved.equals(actual) && assignable(resolved, expected);
      }
      if (actual.hasList() && expected.hasList()) {
        return assignable(actual.getList().getElementType(), expected.getList().getElementType());
      }
      if (actual.hasRange() && expected.hasRange()) {
        return assignable(actual.getRange().getElementType(), expected.getRange().getElementType());
      }
      if (actual.hasContext() && expected.hasContext()) {
        for (ContextEntryTypeReference expectedEntry : expected.getContext().getEntriesList()) {
          ContextEntryTypeReference actualEntry = actual.getContext().getEntriesList().stream()
              .filter(entry -> entry.getName().equals(expectedEntry.getName()))
              .findFirst().orElse(null);
          if (actualEntry == null || !assignable(actualEntry.getType(), expectedEntry.getType())) {
            return false;
          }
        }
        return true;
      }
      return false;
    }

    private TypeReference resolveNamed(TypeReference type) {
      TypeReference builtin = namedBuiltin(type.getNamed().getName());
      if (builtin != null) {
        return builtin;
      }
      ItemDefinition item = itemTypes.get(type.getNamed().getName());
      if (item == null) {
        return type;
      }
      TypeReference resolved;
      if (item.getComponentsCount() > 0) {
        ContextTypeReference.Builder context = ContextTypeReference.newBuilder();
        for (ItemComponent component : item.getComponentsList()) {
          TypeReference componentType = component.getType();
          context.addEntries(ContextEntryTypeReference.newBuilder()
              .setName(component.getNode().getName())
              .setType(component.getIsCollection() ? list(componentType) : componentType));
        }
        resolved = TypeReference.newBuilder().setContext(context).build();
      } else if (item.hasType()) {
        resolved = item.getType();
      } else {
        return type;
      }
      return item.getIsCollection() ? list(resolved) : resolved;
    }

    private static TypeReference namedBuiltin(String name) {
      return switch (name) {
        case "any" -> ANY;
        case "number" -> NUMBER;
        case "string" -> builtin(BuiltinType.BUILTIN_TYPE_STRING);
        case "boolean" -> builtin(BuiltinType.BUILTIN_TYPE_BOOLEAN);
        case "date" -> builtin(BuiltinType.BUILTIN_TYPE_DATE);
        case "time" -> builtin(BuiltinType.BUILTIN_TYPE_TIME);
        case "date and time" -> builtin(BuiltinType.BUILTIN_TYPE_DATE_AND_TIME);
        case "duration" -> builtin(BuiltinType.BUILTIN_TYPE_DURATION);
        case "years and months duration" ->
            builtin(BuiltinType.BUILTIN_TYPE_YEARS_AND_MONTHS_DURATION);
        case "days and time duration" ->
            builtin(BuiltinType.BUILTIN_TYPE_DAYS_AND_TIME_DURATION);
        default -> null;
      };
    }

    private static TypeReference merge(TypeReference left, TypeReference right) {
      if (left.getKindCase() == TypeReference.KindCase.KIND_NOT_SET) {
        return right;
      }
      if (right.getKindCase() == TypeReference.KindCase.KIND_NOT_SET) {
        return left;
      }
      if (left.equals(NULL)) {
        return right;
      }
      if (right.equals(NULL)) {
        return left;
      }
      return left.equals(right) ? left : ANY;
    }

    private static boolean isUnknown(TypeReference type) {
      return type.getKindCase() == TypeReference.KindCase.KIND_NOT_SET
          || type.hasBuiltin() && type.getBuiltin() == BuiltinType.BUILTIN_TYPE_ANY;
    }

    private static TypeReference list(TypeReference element) {
      return TypeReference.newBuilder()
          .setList(ListTypeReference.newBuilder().setElementType(element)).build();
    }

    private static String typeName(TypeReference type) {
      return switch (type.getKindCase()) {
        case BUILTIN -> type.getBuiltin().name()
            .replace("BUILTIN_TYPE_", "").toLowerCase().replace('_', ' ');
        case NAMED -> type.getNamed().getName();
        case LIST -> "list<" + typeName(type.getList().getElementType()) + ">";
        case FUNCTION -> "function";
        case RANGE -> "range<" + typeName(type.getRange().getElementType()) + ">";
        case CONTEXT -> "context";
        case KIND_NOT_SET -> "any";
        default -> "any";
      };
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

  private static TypeReference builtin(BuiltinType type) {
    return TypeReference.newBuilder().setBuiltin(type).build();
  }
}
