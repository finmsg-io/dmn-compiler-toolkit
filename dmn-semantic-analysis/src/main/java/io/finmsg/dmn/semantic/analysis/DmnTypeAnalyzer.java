package io.finmsg.dmn.semantic.analysis;

import io.finmsg.dmn.model.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Applies {@link FeelTypeAnalyzer} to every supported parsed FEEL node in a DMN model. */
public final class DmnTypeAnalyzer implements DmnSemanticPass<DmnSemanticAnalysisResult> {

  private static final TypeReference ANY = builtin(BuiltinType.BUILTIN_TYPE_ANY);
  private static final TypeReference NUMBER = builtin(BuiltinType.BUILTIN_TYPE_NUMBER);
  private static final TypeReference NULL = builtin(BuiltinType.BUILTIN_TYPE_NULL);
  private final FeelTypeAnalyzer feelTypes = new FeelTypeAnalyzer();

  @Override
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
    private final Map<String, List<BusinessKnowledgeModel>> bkmsByName = new LinkedHashMap<>();

    private Session(Definitions input) {
      this.input = input;
      input.getItemDefinitionsList().forEach(item -> itemTypes.put(item.getNode().getName(), item));
      for (DrgElement element : input.getDrgElementsList()) {
        switch (element.getElementCase()) {
          case INPUT_DATA -> addSymbol(element.getInputData().getNode(),
              element.getInputData().getVariable().getType());
          case DECISION -> addSymbol(element.getDecision().getNode(),
              element.getDecision().getVariable().getType());
          case BUSINESS_KNOWLEDGE_MODEL -> {
            BusinessKnowledgeModel bkm = element.getBusinessKnowledgeModel();
            addSymbol(bkm.getNode(), bkm.getVariable().getType());
            if (!bkm.getNode().getName().isBlank()) {
              bkmsByName.computeIfAbsent(bkm.getNode().getName(), ignored -> new ArrayList<>())
                  .add(bkm);
            }
          }
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
      for (int i = 0; i < input.getItemDefinitionsCount(); i++) {
        output.setItemDefinitions(i, typeItemDefinition(input.getItemDefinitions(i), i));
      }
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

    private ItemDefinition typeItemDefinition(ItemDefinition item, int index) {
      String name = item.getNode().getName().isBlank() ? Integer.toString(index)
          : item.getNode().getName();
      String path = "definitions/itemDefinition[" + name + "]";
      ItemDefinition.Builder output = item.toBuilder();
      if (item.hasConstraint() && item.getConstraint().hasParsed()) {
        output.setConstraint(typeConstraint(item.getConstraint(), item.getType(),
            path + "/typeConstraint", item.getNode().getSourceLocation()));
      }
      for (int i = 0; i < item.getComponentsCount(); i++) {
        ItemComponent component = item.getComponents(i);
        if (component.hasConstraint() && component.getConstraint().hasParsed()) {
          String componentName = component.getNode().getName().isBlank() ? Integer.toString(i)
              : component.getNode().getName();
          output.setComponents(i, component.toBuilder().setConstraint(
              typeConstraint(component.getConstraint(), component.getType(),
                  path + "/component[" + componentName + "]/typeConstraint",
                  component.getNode().getSourceLocation())));
        }
      }
      return output.build();
    }

    private TypeConstraint typeConstraint(TypeConstraint constraint, TypeReference subject,
        String path, SourceLocation location) {
      Expression wrapped = Expression.newBuilder()
          .setUnaryTests(constraint.getParsed().getTests()).build();
      FeelTypeAnalysisResult typed = infer(wrapped, Map.of(), path, location);
      UnaryTestsExpression tests = typed.expression().getUnaryTests();
      validateUnaryTests(tests, subject, "TYPE_CONSTRAINT_TYPE_MISMATCH", path, location);
      return constraint.toBuilder().setParsed(
          constraint.getParsed().toBuilder().setTests(tests)).build();
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
      TypeReference expected = bkm.getVariable().getType();
      if (expected.hasFunction()) {
        expected = expected.getFunction().getReturnType();
      }
      validateValueType(feelType(function.getLogic()), expected,
          "BKM_RETURN_TYPE_MISMATCH",
          "definitions/businessKnowledgeModel[" + bkm.getNode().getName() + "]/variable",
          bkm.getNode().getSourceLocation());
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
        InputClause.Builder typedInput = inputClause.toBuilder();
        if (inputClause.hasInputExpression()) {
          typedInput.setInputExpression(typeFeel(inputClause.getInputExpression(), scope,
              path + "/input[" + i + "]/expression", location));
        }
        if (inputClause.hasInputValues()) {
          typedInput.setInputValues(typeFeel(inputClause.getInputValues(), scope,
              path + "/input[" + i + "]/inputValues", location));
        }
        output.setInputs(i, typedInput);
      }
      validateOutputNames(table, path, location);
      for (int i = 0; i < table.getOutputsCount(); i++) {
        OutputClause clause = table.getOutputs(i);
        OutputClause.Builder typedOutput = clause.toBuilder();
        if (clause.hasOutputValues()) {
          typedOutput.setOutputValues(typeFeel(clause.getOutputValues(), scope,
              path + "/output[" + i + "]/outputValues", location));
        }
        if (clause.hasDefaultOutputEntry()) {
          typedOutput.setDefaultOutputEntry(typeExpressionNode(clause.getDefaultOutputEntry(), scope,
              path + "/output[" + i + "]/defaultOutputEntry", location));
        }
        output.setOutputs(i, typedOutput);
      }
      for (int i = 0; i < table.getRulesCount(); i++) {
        DecisionRule rule = table.getRules(i);
        DecisionRule.Builder typedRule = rule.toBuilder();
        if (rule.getInputEntriesCount() != table.getInputsCount()) {
          diagnostic("INVALID_INPUT_ENTRY_COUNT", path + "/rule[" + i + "]",
              "Rule has " + rule.getInputEntriesCount() + " input entries but the table has "
                  + table.getInputsCount() + " input clauses.", location);
        }
        if (rule.getOutputEntriesCount() != table.getOutputsCount()) {
          diagnostic("INVALID_OUTPUT_ENTRY_COUNT", path + "/rule[" + i + "]",
              "Rule has " + rule.getOutputEntriesCount() + " output entries but the table has "
                  + table.getOutputsCount() + " output clauses.", location);
        }
        for (int j = 0; j < rule.getInputEntriesCount(); j++) {
          UnaryTest test = rule.getInputEntries(j);
          if (test.hasParsed()) {
            Expression wrapped = Expression.newBuilder().setUnaryTests(test.getParsed().getTests()).build();
            FeelTypeAnalysisResult typed = infer(wrapped, scope,
                path + "/rule[" + i + "]/inputEntry[" + j + "]", location);
            typedRule.setInputEntries(j, test.toBuilder().setParsed(
                test.getParsed().toBuilder().setTests(typed.expression().getUnaryTests())));
            if (j < output.getInputsCount()) {
              TypeReference subject = inputClauseType(output.getInputs(j));
              validateUnaryTests(typed.expression().getUnaryTests(), subject,
                  path + "/rule[" + i + "]/inputEntry[" + j + "]", location);
            }
          }
        }
        for (int j = 0; j < rule.getOutputEntriesCount(); j++) {
          Feel typedEntry = typeFeel(rule.getOutputEntries(j), scope,
              path + "/rule[" + i + "]/outputEntry[" + j + "]", location);
          typedRule.setOutputEntries(j, typedEntry);
          if (j < output.getOutputsCount()) {
            validateValueType(feelType(typedEntry), output.getOutputs(j).getType(),
                "RULE_OUTPUT_TYPE_MISMATCH",
                path + "/rule[" + i + "]/outputEntry[" + j + "]", location);
          }
        }
        output.setRules(i, typedRule);
      }
      DecisionTable typedTable = output.build();
      validateClauseValueTypes(typedTable, path, location);
      validateHitPolicy(typedTable, path, location);
      return typedTable;
    }

    private BoxedExpression typeBoxed(BoxedExpression boxed, Map<String, TypeReference> scope,
        String path, SourceLocation location) {
      if (!boxed.hasParsed()) {
        return boxed;
      }
      BoxedExpressionParsed parsed = boxed.getParsed();
      BoxedExpressionParsed typed = switch (parsed.getTypeCase()) {
        case CONTEXT -> parsed.toBuilder().setContext(
            typeContext(parsed.getContext(), scope, path, location)).build();
        case RELATION -> parsed.toBuilder().setRelation(
            typeRelation(parsed.getRelation(), scope, path, location)).build();
        case LIST -> {
          ListExpressionParsed.Builder list = parsed.getList().toBuilder();
          for (int i = 0; i < parsed.getList().getElementsCount(); i++) {
            list.setElements(i, typeExpressionParsed(parsed.getList().getElements(i), scope,
                path + "/list/element[" + i + "]", location));
          }
          yield parsed.toBuilder().setList(list).build();
        }
        case FUNCTION_DEFINITION -> {
          FunctionDefinitionParsed function = parsed.getFunctionDefinition();
          Map<String, TypeReference> local = new LinkedHashMap<>(scope);
          function.getParametersList().forEach(parameter ->
              local.put(parameter.getNode().getName(), parameter.getType()));
          FunctionDefinitionParsed.Builder typedFunction = function.toBuilder();
          if (function.hasBody()) {
            typedFunction.setBody(typeExpressionParsed(function.getBody(), local,
                path + "/function/body", location));
          }
          yield parsed.toBuilder().setFunctionDefinition(typedFunction).build();
        }
        case TYPE_NOT_SET -> parsed;
      };
      return boxed.toBuilder().setParsed(typed).build();
    }

    private ContextParsed typeContext(ContextParsed context, Map<String, TypeReference> scope,
        String path, SourceLocation location) {
      ContextParsed.Builder typedContext = context.toBuilder().clearEntries();
      Map<String, TypeReference> local = new LinkedHashMap<>(scope);
      for (int i = 0; i < context.getEntriesCount(); i++) {
        ContextEntryParsed entry = context.getEntries(i);
        ContextEntryParsed.Builder typedEntry = entry.toBuilder();
        TypeReference inferred = TypeReference.getDefaultInstance();
        if (entry.hasExpression()) {
          ExpressionParsed expression = typeExpressionParsed(entry.getExpression(), local,
              path + "/context/entry[" + i + "]", location);
          inferred = expressionParsedType(expression);
          typedEntry.setExpression(expression);
        }
        if (entry.hasVariable() && !entry.getVariable().getNode().getName().isBlank()) {
          TypeReference declared = entry.getVariable().getType();
          validateValueType(inferred, declared, "CONTEXT_ENTRY_TYPE_MISMATCH",
              path + "/context/entry[" + i + "]", location);
          local.put(entry.getVariable().getNode().getName(),
              declared.getKindCase() == TypeReference.KindCase.KIND_NOT_SET ? inferred : declared);
        }
        typedContext.addEntries(typedEntry);
      }
      return typedContext.build();
    }

    private RelationParsed typeRelation(RelationParsed relation, Map<String, TypeReference> scope,
        String path, SourceLocation location) {
      RelationParsed.Builder output = relation.toBuilder();
      Map<String, Integer> columnNames = new LinkedHashMap<>();
      List<TypeReference> inferredColumns = new ArrayList<>();
      for (int i = 0; i < relation.getColumnsCount(); i++) {
        String name = relation.getColumns(i).getVariable().getNode().getName();
        inferredColumns.add(TypeReference.getDefaultInstance());
        if (name.isBlank()) {
          diagnostic("MISSING_RELATION_COLUMN_NAME", path + "/relation/column[" + i + "]",
              "A relation column must have a name.", location);
        } else if (columnNames.putIfAbsent(name, i) != null) {
          diagnostic("DUPLICATE_RELATION_COLUMN_NAME", path + "/relation/column[" + i + "]",
              "Duplicate relation column name '" + name + "'.", location);
        }
      }
      for (int i = 0; i < relation.getRowsCount(); i++) {
        RelationRowParsed row = relation.getRows(i);
        if (row.getExpressionsCount() != relation.getColumnsCount()) {
          diagnostic("RELATION_ROW_WIDTH_MISMATCH", path + "/relation/row[" + i + "]",
              "Relation row has " + row.getExpressionsCount() + " cells but "
                  + relation.getColumnsCount() + " columns are declared.", location);
        }
        RelationRowParsed.Builder typedRow = row.toBuilder();
        for (int j = 0; j < row.getExpressionsCount(); j++) {
          ExpressionParsed expression = typeExpressionParsed(row.getExpressions(j), scope,
              path + "/relation/row[" + i + "]/cell[" + j + "]", location);
          typedRow.setExpressions(j, expression);
          if (j < relation.getColumnsCount()) {
            TypeReference cellType = expressionParsedType(expression);
            inferredColumns.set(j, merge(inferredColumns.get(j), cellType));
            validateValueType(cellType, relation.getColumns(j).getVariable().getType(),
                "RELATION_CELL_TYPE_MISMATCH",
                path + "/relation/row[" + i + "]/cell[" + j + "]", location);
          }
        }
        output.setRows(i, typedRow);
      }
      for (int i = 0; i < relation.getColumnsCount(); i++) {
        RelationColumnParsed column = relation.getColumns(i);
        if (column.getVariable().getType().getKindCase() == TypeReference.KindCase.KIND_NOT_SET
            && inferredColumns.get(i).getKindCase() != TypeReference.KindCase.KIND_NOT_SET) {
          output.setColumns(i, column.toBuilder().setVariable(
              column.getVariable().toBuilder().setType(inferredColumns.get(i))));
        }
      }
      return output.build();
    }

    private ExpressionParsed typeExpressionParsed(ExpressionParsed expression,
        Map<String, TypeReference> scope, String path, SourceLocation location) {
      return switch (expression.getTypeCase()) {
        case FEEL -> {
          FeelTypeAnalysisResult result = infer(expression.getFeel().getAst(), scope, path, location);
          yield expression.toBuilder().setFeel(
              expression.getFeel().toBuilder().setAst(result.expression())).build();
        }
        case BOXED -> {
          BoxedExpression typed = typeBoxed(
              BoxedExpression.newBuilder().setParsed(expression.getBoxed()).build(),
              scope, path, location);
          yield expression.toBuilder().setBoxed(typed.getParsed()).build();
        }
        case TYPE_NOT_SET -> expression;
      };
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
      Invocation typed = output.build();
      validateBkmInvocation(typed, scope, path, location);
      return typed;
    }

    private void validateBkmInvocation(Invocation invocation, Map<String, TypeReference> scope,
        String path, SourceLocation location) {
      if (!invocation.hasExpression() || !invocation.getExpression().hasParsed()
          || !invocation.getExpression().getParsed().getAst().hasName()) {
        return;
      }
      String targetName = invocation.getExpression().getParsed().getAst().getName().getName();
      List<BusinessKnowledgeModel> candidates = bkmsByName.get(targetName);
      if (!scope.containsKey(targetName) || candidates == null || candidates.size() != 1) {
        return;
      }
      Map<String, TypeReference> parameterTypes = new LinkedHashMap<>();
      for (InformationItem parameter : candidates.getFirst().getFunction().getFormalParametersList()) {
        parameterTypes.putIfAbsent(parameter.getNode().getName(), parameter.getType());
      }
      for (int i = 0; i < invocation.getBindingsCount(); i++) {
        Binding binding = invocation.getBindings(i);
        TypeReference expected = parameterTypes.get(binding.getParameter());
        if (expected != null && binding.hasExpression()) {
          validateValueType(feelType(binding.getExpression()), expected,
              "INVOCATION_ARGUMENT_TYPE_MISMATCH", path + "/binding[" + i + "]", location);
        }
      }
    }

    private Feel typeFeel(Feel feel, Map<String, TypeReference> scope,
        String path, SourceLocation location) {
      if (!feel.hasParsed()) {
        return feel;
      }
      FeelTypeAnalysisResult result = infer(feel.getParsed().getAst(), scope, path, location);
      return feel.toBuilder().setParsed(feel.getParsed().toBuilder().setAst(result.expression())).build();
    }

    private ExpressionNode typeExpressionNode(
        ExpressionNode node, Map<String, TypeReference> scope,
        String path, SourceLocation location) {
      if (!node.hasParsed()) {
        return node;
      }
      ExpressionParsed parsed = node.getParsed();
      return switch (parsed.getTypeCase()) {
        case FEEL -> {
          FeelTypeAnalysisResult result = infer(parsed.getFeel().getAst(), scope, path, location);
          yield node.toBuilder().setParsed(parsed.toBuilder().setFeel(
              parsed.getFeel().toBuilder().setAst(result.expression()))).build();
        }
        case BOXED -> {
          BoxedExpression typed = typeBoxed(
              BoxedExpression.newBuilder().setParsed(parsed.getBoxed()).build(),
              scope, path, location);
          yield node.toBuilder().setParsed(parsed.toBuilder().setBoxed(typed.getParsed())).build();
        }
        case TYPE_NOT_SET -> node;
      };
    }

    private void validateOutputNames(
        DecisionTable table, String path, SourceLocation location) {
      if (table.getOutputsCount() <= 1) {
        return;
      }
      Map<String, Integer> names = new LinkedHashMap<>();
      for (int i = 0; i < table.getOutputsCount(); i++) {
        String name = table.getOutputs(i).getNode().getName();
        if (name.isBlank()) {
          diagnostic("MISSING_OUTPUT_NAME", path + "/output[" + i + "]",
              "Every output clause in a multi-output decision table must have a name.", location);
        } else if (names.putIfAbsent(name, i) != null) {
          diagnostic("DUPLICATE_OUTPUT_NAME", path + "/output[" + i + "]",
              "Duplicate output clause name '" + name + "'.", location);
        }
      }
    }

    private void validateClauseValueTypes(
        DecisionTable table, String path, SourceLocation location) {
      for (int i = 0; i < table.getInputsCount(); i++) {
        InputClause clause = table.getInputs(i);
        if (clause.hasInputValues() && clause.getInputValues().hasParsed()
            && clause.getInputValues().getParsed().getAst().hasUnaryTests()) {
          validateUnaryTests(clause.getInputValues().getParsed().getAst().getUnaryTests(),
              inputClauseType(clause), path + "/input[" + i + "]/inputValues", location);
        }
      }
      for (int i = 0; i < table.getOutputsCount(); i++) {
        OutputClause clause = table.getOutputs(i);
        if (clause.hasOutputValues() && clause.getOutputValues().hasParsed()
            && clause.getOutputValues().getParsed().getAst().hasUnaryTests()) {
          validateUnaryTests(clause.getOutputValues().getParsed().getAst().getUnaryTests(),
              clause.getType(), path + "/output[" + i + "]/outputValues", location);
        }
        if (clause.hasDefaultOutputEntry()) {
          validateValueType(expressionNodeType(clause.getDefaultOutputEntry()), clause.getType(),
              "DEFAULT_OUTPUT_TYPE_MISMATCH",
              path + "/output[" + i + "]/defaultOutputEntry", location);
        }
      }
    }

    private void validateUnaryTests(
        UnaryTestsExpression tests, TypeReference subject,
        String path, SourceLocation location) {
      validateUnaryTests(tests, subject, "UNARY_TEST_TYPE_MISMATCH", path, location);
    }

    private void validateUnaryTests(
        UnaryTestsExpression tests, TypeReference subject, String code,
        String path, SourceLocation location) {
      if (isUnknown(subject)) {
        return;
      }
      for (int i = 0; i < tests.getTestsCount(); i++) {
        PositiveUnaryTest test = tests.getTests(i);
        switch (test.getTypeCase()) {
          case COMPARISON -> validateValueType(
              test.getComparison().getEndpoint().getInferredType(), subject,
              code, path + "/test[" + i + "]", location);
          case RANGE -> {
            if (test.getRange().hasLower()) {
              validateValueType(test.getRange().getLower().getInferredType(), subject,
                  code, path + "/test[" + i + "]/lower", location);
            }
            if (test.getRange().hasUpper()) {
              validateValueType(test.getRange().getUpper().getInferredType(), subject,
                  code, path + "/test[" + i + "]/upper", location);
            }
          }
          case EXPRESSION -> validateValueType(test.getExpression().getInferredType(), subject,
              code, path + "/test[" + i + "]", location);
          case TYPE_NOT_SET -> { }
        }
      }
    }

    private void validateValueType(
        TypeReference actual, TypeReference expected, String code,
        String path, SourceLocation location) {
      if (!isUnknown(actual) && !isUnknown(expected) && !assignable(actual, expected)) {
        diagnostic(code, path,
            "Expected " + typeName(expected) + " but found " + typeName(actual) + ".",
            location);
      }
    }

    private void validateHitPolicy(
        DecisionTable table, String path, SourceLocation location) {
      HitPolicy policy = table.getHitPolicy().getPolicy();
      Aggregation aggregation = table.getHitPolicy().getAggregation();
      if (policy != HitPolicy.HIT_POLICY_COLLECT
          && aggregation != Aggregation.AGGREGATION_UNSPECIFIED) {
        diagnostic("INVALID_HIT_POLICY_AGGREGATION", path + "/hitPolicy",
            "Aggregation is only valid with the COLLECT hit policy.", location);
      }
      if (policy == HitPolicy.HIT_POLICY_COLLECT
          && aggregation != Aggregation.AGGREGATION_UNSPECIFIED) {
        if (table.getOutputsCount() != 1) {
          diagnostic("INVALID_COLLECT_AGGREGATION", path + "/hitPolicy",
              "A COLLECT aggregation requires exactly one output clause.", location);
        } else {
          TypeReference outputType = decisionTableOutputType(table, 0);
          if (aggregation == Aggregation.AGGREGATION_SUM && !isNumeric(outputType)) {
            diagnostic("INVALID_COLLECT_AGGREGATION_TYPE", path + "/hitPolicy",
                "SUM aggregation requires a numeric output type.", location);
          }
          if ((aggregation == Aggregation.AGGREGATION_MIN
              || aggregation == Aggregation.AGGREGATION_MAX)
              && !isOrderable(outputType)) {
            diagnostic("INVALID_COLLECT_AGGREGATION_TYPE", path + "/hitPolicy",
                aggregation.name().replace("AGGREGATION_", "")
                    + " aggregation requires an orderable output type.", location);
          }
        }
      }
      if (policy == HitPolicy.HIT_POLICY_PRIORITY
          || policy == HitPolicy.HIT_POLICY_OUTPUT_ORDER) {
        for (int i = 0; i < table.getOutputsCount(); i++) {
          if (!table.getOutputs(i).hasOutputValues()) {
            diagnostic("MISSING_OUTPUT_VALUES", path + "/output[" + i + "]",
                policy.name().replace("HIT_POLICY_", "")
                    + " requires ordered output values.", location);
          }
        }
      }
    }

    private TypeReference inputClauseType(InputClause clause) {
      return !isUnknown(clause.getType())
          ? clause.getType() : feelType(clause.getInputExpression());
    }

    private static TypeReference expressionNodeType(ExpressionNode node) {
      if (!node.hasParsed()) {
        return TypeReference.getDefaultInstance();
      }
      return switch (node.getParsed().getTypeCase()) {
        case FEEL -> node.getParsed().getFeel().getAst().getInferredType();
        case BOXED, TYPE_NOT_SET -> TypeReference.getDefaultInstance();
      };
    }

    private boolean isNumeric(TypeReference type) {
      TypeReference resolved = type.hasNamed() ? resolveNamed(type) : type;
      return resolved.equals(NUMBER) || isUnknown(resolved);
    }

    private boolean isOrderable(TypeReference type) {
      TypeReference resolved = type.hasNamed() ? resolveNamed(type) : type;
      if (isUnknown(resolved)) {
        return true;
      }
      if (!resolved.hasBuiltin()) {
        return false;
      }
      return switch (resolved.getBuiltin()) {
        case BUILTIN_TYPE_NUMBER, BUILTIN_TYPE_STRING, BUILTIN_TYPE_DATE,
             BUILTIN_TYPE_TIME, BUILTIN_TYPE_DATE_AND_TIME,
             BUILTIN_TYPE_DURATION, BUILTIN_TYPE_YEARS_AND_MONTHS_DURATION,
             BUILTIN_TYPE_DAYS_AND_TIME_DURATION -> true;
        default -> false;
      };
    }

    private void diagnostic(
        String code, String path, String message, SourceLocation location) {
      diagnostics.add(new DmnSemanticDiagnostic(code, path, message, location));
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
        case RELATION -> {
          ContextTypeReference.Builder row = ContextTypeReference.newBuilder();
          for (RelationColumnParsed column : parsed.getRelation().getColumnsList()) {
            row.addEntries(ContextEntryTypeReference.newBuilder()
                .setName(column.getVariable().getNode().getName())
                .setType(column.getVariable().getType()));
          }
          yield list(TypeReference.newBuilder().setContext(row).build());
        }
        case TYPE_NOT_SET -> TypeReference.getDefaultInstance();
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
