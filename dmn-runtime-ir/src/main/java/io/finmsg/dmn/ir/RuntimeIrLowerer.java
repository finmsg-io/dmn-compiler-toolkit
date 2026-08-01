package io.finmsg.dmn.ir;

import io.finmsg.dmn.model.*;
import io.finmsg.dmn.semantic.analysis.DmnSemanticPipelineResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Optional;

/** Lowers successfully typed semantic results into structural Runtime IR. */
public final class RuntimeIrLowerer {

  public RuntimeModel lower(DmnSemanticPipelineResult analysis) {
    return lowerModelSet(List.of(Objects.requireNonNull(analysis, "analysis")));
  }

  /** Lowers an ordered set of analyzed models into one linked, namespace-free runtime model. */
  public RuntimeModel lowerModelSet(List<DmnSemanticPipelineResult> analyses) {
    RuntimeModelIndex index = RuntimeModelIndex.create(analyses);

    List<RuntimeInput> inputs = new ArrayList<>();
    List<RuntimeDecision> decisions = new ArrayList<>();
    List<RuntimeBkm> bkms = new ArrayList<>();
    int runtimeId = 0;
    for (RuntimeModelIndex.ModelView view : index.models()) {
      DmnSemanticPipelineResult analysis = view.analysis();
      Definitions model = analysis.model();
      Map<String, ItemDefinition> itemTypes = view.itemTypes();
      Map<String, Integer> modelIds = view.runtimeIds();
      Map<String, Integer> modelSlots = view.valueSlots();
      for (DrgElement element : model.getDrgElementsList()) {
        switch (element.getElementCase()) {
        case INPUT_DATA -> {
          InputData input = element.getInputData();
          inputs.add(new RuntimeInput(runtimeId, runtimeId,
              RuntimeTypeLowerer.lower(input.getVariable().getType(), itemTypes)));
          runtimeId++;
        }
        case DECISION -> {
          Decision decision = element.getDecision();
          Map<String, LocalSlotAddress> localSlots = new HashMap<>();
          int[] nextLocalSlot = {0};
          Optional<RuntimeExpression> expression = lowerDecisionExpression(
              decision, analysis.bindings(), modelSlots, itemTypes,
              localSlots, nextLocalSlot);
          Optional<RuntimeDecisionTable> decisionTable = lowerDecisionTable(
              decision, analysis.bindings(), modelSlots, itemTypes,
              localSlots, nextLocalSlot);
          Set<Integer> dependencies = new LinkedHashSet<>(
              decisionDependencies(decision, modelIds));
          expression.ifPresent(value -> collectReferencedSlots(value, dependencies));
          decisionTable.ifPresent(value -> collectReferencedSlots(value, dependencies));
          decisions.add(new RuntimeDecision(runtimeId, runtimeId,
              RuntimeTypeLowerer.lower(decision.getVariable().getType(), itemTypes),
              List.copyOf(dependencies), expression, decisionTable,
              nextLocalSlot[0]));
          runtimeId++;
        }
        case BUSINESS_KNOWLEDGE_MODEL -> {
          BusinessKnowledgeModel bkm = element.getBusinessKnowledgeModel();
          RuntimeFunctionDefinition function = lowerBkmFunction(
              bkm, analysis.bindings(), modelSlots, itemTypes);
          Set<Integer> dependencies = new LinkedHashSet<>(
              bkmDependencies(bkm, modelIds));
          collectReferencedSlots(function, dependencies);
          bkms.add(new RuntimeBkm(runtimeId, runtimeId,
              RuntimeTypeLowerer.lower(bkm.getVariable().getType(), itemTypes),
              List.copyOf(dependencies),
              functionKind(bkm.getFunction().getKind()),
              Optional.of(function)));
          runtimeId++;
        }
        default -> { }
        }
      }
    }
    List<Integer> order = runtimeEvaluationOrder(decisions, bkms);
    return new RuntimeModel(inputs, decisions, bkms, order, index.valueCount());
  }

  private static RuntimeFunctionDefinition lowerBkmFunction(
      BusinessKnowledgeModel bkm,
      List<io.finmsg.dmn.semantic.analysis.DmnSymbolBinding> bindings,
      Map<String, Integer> slots,
      Map<String, ItemDefinition> itemTypes) {
    if (!bkm.hasFunction() || !bkm.getFunction().hasLogic()
        || !bkm.getFunction().getLogic().hasParsed()) {
      throw new RuntimeIrLoweringException(
          "BKM '" + bkm.getNode().getName() + "' requires parsed executable function logic.");
    }
    FunctionDefinition function = bkm.getFunction();
    String path = "definitions/businessKnowledgeModel[" + bkm.getNode().getName() + "]";
    Map<String, LocalSlotAddress> localSlots = new HashMap<>();
    int[] nextLocalSlot = {0};
    List<RuntimeFunctionParameter> parameters = new ArrayList<>();
    for (int index = 0; index < function.getFormalParametersCount(); index++) {
      InformationItem parameter = function.getFormalParameters(index);
      int localSlot = nextLocalSlot[0]++;
      localSlots.put(path + "/parameter[" + index + "]", new LocalSlotAddress(0, localSlot));
      parameters.add(new RuntimeFunctionParameter(parameter.getNode().getName(), localSlot,
          RuntimeTypeLowerer.lower(parameter.getType(), itemTypes)));
    }
    RuntimeExpression body = RuntimeExpressionLowerer.lowerExpression(
        function.getLogic().getParsed().getAst(),
        path + "/logic", bindings, slots, itemTypes, localSlots, nextLocalSlot);
    RuntimeType type = RuntimeTypeLowerer.lower(bkm.getVariable().getType(), itemTypes);
    return new RuntimeFunctionDefinition(parameters, Optional.of(body),
        function.getKind() == FunctionKind.FUNCTION_KIND_JAVA
            || function.getKind() == FunctionKind.FUNCTION_KIND_PMML,
        nextLocalSlot[0],
        type);
  }

  private static RuntimeFunctionKind functionKind(FunctionKind kind) {
    return switch (kind) {
      case FUNCTION_KIND_UNSPECIFIED, FUNCTION_KIND_FEEL -> RuntimeFunctionKind.FEEL;
      case FUNCTION_KIND_JAVA -> RuntimeFunctionKind.JAVA;
      case FUNCTION_KIND_PMML -> RuntimeFunctionKind.PMML;
      case UNRECOGNIZED -> throw new RuntimeIrLoweringException(
          "Unsupported BKM function kind " + kind + ".");
    };
  }

  private static Optional<RuntimeExpression> lowerDecisionExpression(
      Decision decision,
      List<io.finmsg.dmn.semantic.analysis.DmnSymbolBinding> bindings,
      Map<String, Integer> slots,
      Map<String, ItemDefinition> itemTypes,
      Map<String, LocalSlotAddress> localSlots,
      int[] nextLocalSlot) {
    if (!decision.hasLogic()) {
      return Optional.empty();
    }
    if (decision.getLogic().hasDecisionTable()) {
      return Optional.empty();
    }
    String basePath = "definitions/decision[" + decision.getNode().getName() + "]/logic";
    return switch (decision.getLogic().getTypeCase()) {
      case LITERAL_EXPRESSION -> {
        if (!decision.getLogic().getLiteralExpression().hasParsed()) {
          throw new RuntimeIrLoweringException(
              "Decision '" + decision.getNode().getName() + "' requires parsed FEEL logic.");
        }
        yield Optional.of(RuntimeExpressionLowerer.lowerExpression(
            decision.getLogic().getLiteralExpression().getParsed().getAst(),
            basePath + "/literalExpression", bindings, slots, itemTypes,
            localSlots, nextLocalSlot));
      }
      case BOXED_EXPRESSION -> {
        if (!decision.getLogic().getBoxedExpression().hasParsed()) {
          throw new RuntimeIrLoweringException(
              "Decision '" + decision.getNode().getName() + "' requires parsed boxed logic.");
        }
        RuntimeType expected = RuntimeTypeLowerer.lower(
            decision.getVariable().getType(), itemTypes);
        yield Optional.of(lowerBoxedExpression(
            decision.getLogic().getBoxedExpression().getParsed(), basePath + "/boxedExpression",
            bindings, slots, itemTypes, localSlots, nextLocalSlot, expected));
      }
      case INVOCATION -> Optional.of(lowerDmnInvocation(
          decision.getLogic().getInvocation(), basePath + "/invocation", bindings, slots,
          itemTypes, localSlots, nextLocalSlot,
          RuntimeTypeLowerer.lower(decision.getVariable().getType(), itemTypes)));
      case DECISION_TABLE, TYPE_NOT_SET -> Optional.empty();
    };
  }

  private static RuntimeExpression lowerDmnInvocation(
      Invocation invocation,
      String path,
      List<io.finmsg.dmn.semantic.analysis.DmnSymbolBinding> bindings,
      Map<String, Integer> slots,
      Map<String, ItemDefinition> itemTypes,
      Map<String, LocalSlotAddress> localSlots,
      int[] nextLocalSlot,
      RuntimeType type) {
    if (!invocation.hasExpression() || !invocation.getExpression().hasParsed()) {
      throw new RuntimeIrLoweringException("DMN invocation requires a parsed target at " + path);
    }
    RuntimeExpression target = RuntimeExpressionLowerer.lowerExpression(
        invocation.getExpression().getParsed().getAst(),
        path + "/expression", bindings, slots, itemTypes, localSlots, nextLocalSlot);
    List<RuntimeNamedArgument> arguments = new ArrayList<>();
    for (int index = 0; index < invocation.getBindingsCount(); index++) {
      Binding binding = invocation.getBindings(index);
      if (!binding.hasExpression() || !binding.getExpression().hasParsed()) {
        throw new RuntimeIrLoweringException(
            "DMN invocation binding requires parsed FEEL at " + path + "/binding[" + index + "]");
      }
      arguments.add(new RuntimeNamedArgument(binding.getParameter(),
          RuntimeExpressionLowerer.lowerExpression(
          binding.getExpression().getParsed().getAst(), path + "/binding[" + index + "]",
          bindings, slots, itemTypes, localSlots, nextLocalSlot)));
    }
    return new RuntimeInvocationExpression(
        Optional.empty(), Optional.of(target), arguments, List.of(), type);
  }

  private static RuntimeExpression lowerParsedExpression(
      ExpressionParsed parsed,
      String path,
      List<io.finmsg.dmn.semantic.analysis.DmnSymbolBinding> bindings,
      Map<String, Integer> slots,
      Map<String, ItemDefinition> itemTypes,
      Map<String, LocalSlotAddress> localSlots,
      int[] nextLocalSlot) {
    return switch (parsed.getTypeCase()) {
      case FEEL -> RuntimeExpressionLowerer.lowerExpression(
          parsed.getFeel().getAst(), path, bindings, slots, itemTypes,
          localSlots, nextLocalSlot);
      case BOXED -> lowerBoxedExpression(parsed.getBoxed(), path, bindings, slots, itemTypes,
          localSlots, nextLocalSlot, null);
      case TYPE_NOT_SET -> throw new RuntimeIrLoweringException(
          "Empty parsed boxed expression at " + path + ".");
    };
  }

  private static RuntimeExpression lowerBoxedExpression(
      BoxedExpressionParsed boxed,
      String path,
      List<io.finmsg.dmn.semantic.analysis.DmnSymbolBinding> bindings,
      Map<String, Integer> slots,
      Map<String, ItemDefinition> itemTypes,
      Map<String, LocalSlotAddress> localSlots,
      int[] nextLocalSlot,
      RuntimeType expectedType) {
    return switch (boxed.getTypeCase()) {
      case CONTEXT -> {
        List<RuntimeContextEntry> entries = new ArrayList<>();
        Map<String, LocalSlotAddress> contextSlots = RuntimeLexicalFrame.childScope(localSlots);
        List<RuntimeField> fields = new ArrayList<>();
        for (int index = 0; index < boxed.getContext().getEntriesCount(); index++) {
          ContextEntryParsed entry = boxed.getContext().getEntries(index);
          String entryPath = path + "/context/entry[" + index + "]";
          if (!entry.hasVariable() || !entry.hasExpression()) {
            throw new RuntimeIrLoweringException("Incomplete boxed context entry at " + entryPath);
          }
          RuntimeExpression expression = lowerParsedExpression(entry.getExpression(),
              entryPath + "/expression", bindings, slots, itemTypes,
              contextSlots, nextLocalSlot);
          String name = entry.getVariable().getNode().getName();
          if (name.isBlank()) {
            entries.add(new RuntimeContextEntry("", -1, expression));
          } else {
            int localSlot = nextLocalSlot[0]++;
            contextSlots.put(path + "/context/entry[" + index + "]",
                new LocalSlotAddress(0, localSlot));
            entries.add(new RuntimeContextEntry(name, localSlot, expression));
            fields.add(new RuntimeField(fields.size(), name, expression.type()));
          }
        }
        yield new RuntimeContextExpression(entries,
            expectedType == null ? RuntimeType.contextFields(fields) : expectedType);
      }
      case LIST -> {
        List<RuntimeExpression> elements = new ArrayList<>();
        for (int index = 0; index < boxed.getList().getElementsCount(); index++) {
          elements.add(lowerParsedExpression(boxed.getList().getElements(index),
              path + "/list/element[" + index + "]", bindings, slots, itemTypes,
              localSlots, nextLocalSlot));
        }
        RuntimeType type = expectedType == null
            ? RuntimeType.element(RuntimeTypeKind.LIST, RuntimeType.scalar(RuntimeTypeKind.ANY))
            : expectedType;
        yield new RuntimeListExpression(elements, type);
      }
      case RELATION -> {
        List<RuntimeRelationColumn> columns = boxed.getRelation().getColumnsList().stream()
            .map(column -> new RuntimeRelationColumn(
                column.getVariable().getNode().getName(),
                RuntimeTypeLowerer.lower(column.getVariable().getType(), itemTypes)))
            .toList();
        List<List<RuntimeExpression>> rows = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < boxed.getRelation().getRowsCount(); rowIndex++) {
          RelationRowParsed row = boxed.getRelation().getRows(rowIndex);
          List<RuntimeExpression> cells = new ArrayList<>();
          for (int columnIndex = 0; columnIndex < row.getExpressionsCount(); columnIndex++) {
            cells.add(lowerParsedExpression(row.getExpressions(columnIndex),
                path + "/relation/row[" + rowIndex + "]/cell[" + columnIndex + "]",
                bindings, slots, itemTypes, localSlots, nextLocalSlot));
          }
          rows.add(cells);
        }
        RuntimeType relationType = expectedType == null
            ? RuntimeType.element(RuntimeTypeKind.LIST,
                RuntimeType.contextFields(RuntimeTypeLowerer.relationFields(columns)))
            : expectedType;
        yield new RuntimeRelationExpression(columns, rows, relationType);
      }
      case FUNCTION_DEFINITION -> {
        FunctionDefinitionParsed function = boxed.getFunctionDefinition();
        List<RuntimeFunctionParameter> parameters = new ArrayList<>();
        Map<String, LocalSlotAddress> parameterSlots =
            RuntimeLexicalFrame.capturedScope(localSlots);
        int[] functionNextLocalSlot = {0};
        for (int index = 0; index < function.getParametersCount(); index++) {
          InformationItem parameter = function.getParameters(index);
          int localSlot = functionNextLocalSlot[0]++;
          parameterSlots.put(path + "/parameter[" + index + "]",
              new LocalSlotAddress(0, localSlot));
          parameters.add(new RuntimeFunctionParameter(parameter.getNode().getName(), localSlot,
              RuntimeTypeLowerer.lower(parameter.getType(), itemTypes)));
        }
        if (!function.hasBody()) {
          throw new RuntimeIrLoweringException("Boxed function requires a body at " + path + ".");
        }
        RuntimeExpression body = lowerParsedExpression(function.getBody(), path + "/body",
            bindings, slots, itemTypes, parameterSlots, functionNextLocalSlot);
        RuntimeType functionType = expectedType == null
            ? RuntimeType.function(parameters.stream().map(RuntimeFunctionParameter::type).toList(),
                body.type()) : expectedType;
        yield new RuntimeFunctionDefinition(parameters, Optional.of(body), false,
            functionNextLocalSlot[0], functionType);
      }
      case TYPE_NOT_SET -> throw new RuntimeIrLoweringException(
          "Empty boxed expression at " + path + ".");
    };
  }

  private static Optional<RuntimeDecisionTable> lowerDecisionTable(
      Decision decision,
      List<io.finmsg.dmn.semantic.analysis.DmnSymbolBinding> bindings,
      Map<String, Integer> slots,
      Map<String, ItemDefinition> itemTypes,
      Map<String, LocalSlotAddress> localSlots,
      int[] nextLocalSlot) {
    if (!decision.hasLogic() || !decision.getLogic().hasDecisionTable()) {
      return Optional.empty();
    }
    DecisionTable table = decision.getLogic().getDecisionTable();
    String path = "definitions/decision[" + decision.getNode().getName()
        + "]/logic/decisionTable";
    List<RuntimeDecisionTableInput> inputs = new ArrayList<>();
    for (int index = 0; index < table.getInputsCount(); index++) {
      InputClause input = table.getInputs(index);
      if (!input.hasInputExpression() || !input.getInputExpression().hasParsed()) {
        throw new RuntimeIrLoweringException(
            "Decision-table input requires parsed FEEL at " + path + "/input[" + index + "].");
      }
      String inputPath = path + "/input[" + index + "]";
      Optional<RuntimeUnaryTests> allowed = input.hasInputValues()
          && input.getInputValues().hasParsed()
          ? Optional.of(RuntimeExpressionLowerer.lowerUnaryTests(
              input.getInputValues().getParsed().getAst().getUnaryTests(),
              inputPath + "/inputValues", bindings, slots, itemTypes,
              localSlots, nextLocalSlot)) : Optional.empty();
      inputs.add(new RuntimeDecisionTableInput(
          RuntimeExpressionLowerer.lowerExpression(
              input.getInputExpression().getParsed().getAst(), inputPath,
              bindings, slots, itemTypes, localSlots, nextLocalSlot),
          allowed, RuntimeTypeLowerer.lower(input.getType(), itemTypes)));
    }
    List<RuntimeDecisionTableOutput> outputs = new ArrayList<>();
    for (int index = 0; index < table.getOutputsCount(); index++) {
      OutputClause output = table.getOutputs(index);
      String outputPath = path + "/output[" + index + "]";
      Optional<RuntimeUnaryTests> allowed = output.hasOutputValues()
          && output.getOutputValues().hasParsed()
          ? Optional.of(RuntimeExpressionLowerer.lowerUnaryTests(
              output.getOutputValues().getParsed().getAst().getUnaryTests(),
              outputPath + "/outputValues", bindings, slots, itemTypes,
              localSlots, nextLocalSlot)) : Optional.empty();
      Optional<RuntimeExpression> defaultValue = Optional.empty();
      if (output.hasDefaultOutputEntry() && output.getDefaultOutputEntry().hasParsed()) {
        ExpressionParsed parsed = output.getDefaultOutputEntry().getParsed();
        if (!parsed.hasFeel()) {
          throw new RuntimeIrLoweringException(
              "Boxed default outputs are not yet supported at " + outputPath + ".");
        }
        defaultValue = Optional.of(RuntimeExpressionLowerer.lowerExpression(
            parsed.getFeel().getAst(),
            outputPath + "/defaultOutputEntry", bindings, slots, itemTypes,
            localSlots, nextLocalSlot));
      }
      outputs.add(new RuntimeDecisionTableOutput(
          output.getNode().getName().isBlank()
              ? Optional.empty() : Optional.of(output.getNode().getName()),
          RuntimeTypeLowerer.lower(output.getType(), itemTypes), allowed, defaultValue));
    }
    List<RuntimeDecisionTableRule> rules = new ArrayList<>();
    for (int ruleIndex = 0; ruleIndex < table.getRulesCount(); ruleIndex++) {
      DecisionRule rule = table.getRules(ruleIndex);
      String rulePath = path + "/rule[" + ruleIndex + "]";
      List<RuntimeUnaryTests> inputEntries = new ArrayList<>();
      for (int index = 0; index < rule.getInputEntriesCount(); index++) {
        UnaryTest entry = rule.getInputEntries(index);
        if (!entry.hasParsed()) {
          throw new RuntimeIrLoweringException(
              "Decision-table input entry requires parsed FEEL at " + rulePath + ".");
        }
        inputEntries.add(RuntimeExpressionLowerer.lowerUnaryTests(entry.getParsed().getTests(),
            rulePath + "/inputEntry[" + index + "]", bindings, slots, itemTypes,
            localSlots, nextLocalSlot));
      }
      List<RuntimeExpression> outputEntries = new ArrayList<>();
      for (int index = 0; index < rule.getOutputEntriesCount(); index++) {
        Feel entry = rule.getOutputEntries(index);
        if (!entry.hasParsed()) {
          throw new RuntimeIrLoweringException(
              "Decision-table output entry requires parsed FEEL at " + rulePath + ".");
        }
        outputEntries.add(RuntimeExpressionLowerer.lowerExpression(entry.getParsed().getAst(),
            rulePath + "/outputEntry[" + index + "]", bindings, slots, itemTypes,
            localSlots, nextLocalSlot));
      }
      rules.add(new RuntimeDecisionTableRule(rule.getRuleIndex(), inputEntries, outputEntries,
          rule.getAnnotationEntriesList().stream().map(RuleAnnotation::getText).toList()));
    }
    return Optional.of(new RuntimeDecisionTable(hitPolicy(table.getHitPolicy().getPolicy()),
        aggregation(table.getHitPolicy().getAggregation()), inputs, outputs, rules,
        table.getAnnotationsCount()));
  }

  private static RuntimeHitPolicy hitPolicy(HitPolicy policy) {
    return switch (policy) {
      case HIT_POLICY_UNSPECIFIED, HIT_POLICY_UNIQUE -> RuntimeHitPolicy.UNIQUE;
      case HIT_POLICY_FIRST -> RuntimeHitPolicy.FIRST;
      case HIT_POLICY_PRIORITY -> RuntimeHitPolicy.PRIORITY;
      case HIT_POLICY_ANY -> RuntimeHitPolicy.ANY;
      case HIT_POLICY_COLLECT -> RuntimeHitPolicy.COLLECT;
      case HIT_POLICY_RULE_ORDER -> RuntimeHitPolicy.RULE_ORDER;
      case HIT_POLICY_OUTPUT_ORDER -> RuntimeHitPolicy.OUTPUT_ORDER;
      case UNRECOGNIZED -> throw new RuntimeIrLoweringException(
          "Unsupported decision-table hit policy " + policy + ".");
    };
  }

  private static Optional<RuntimeAggregation> aggregation(Aggregation aggregation) {
    return switch (aggregation) {
      case AGGREGATION_UNSPECIFIED -> Optional.empty();
      case AGGREGATION_SUM -> Optional.of(RuntimeAggregation.SUM);
      case AGGREGATION_MIN -> Optional.of(RuntimeAggregation.MIN);
      case AGGREGATION_MAX -> Optional.of(RuntimeAggregation.MAX);
      case AGGREGATION_COUNT -> Optional.of(RuntimeAggregation.COUNT);
      case UNRECOGNIZED -> throw new RuntimeIrLoweringException(
          "Unsupported decision-table aggregation " + aggregation + ".");
    };
  }

  private static List<Integer> decisionDependencies(
      Decision decision, Map<String, Integer> ids) {
    Set<Integer> result = new LinkedHashSet<>();
    for (InformationRequirement requirement : decision.getInformationRequirementsList()) {
      switch (requirement.getRequiredCase()) {
        case INPUT -> addReference(result, requirement.getInput().getHref(), ids);
        case DECISION -> addReference(result, requirement.getDecision().getHref(), ids);
        case REQUIRED_NOT_SET -> { }
      }
    }
    decision.getKnowledgeRequirementsList().forEach(requirement ->
        addReference(result, requirement.getRequiredKnowledge().getHref(), ids));
    return List.copyOf(result);
  }

  private static List<Integer> bkmDependencies(
      BusinessKnowledgeModel bkm, Map<String, Integer> ids) {
    Set<Integer> result = new LinkedHashSet<>();
    bkm.getKnowledgeRequirementsList().forEach(requirement ->
        addReference(result, requirement.getRequiredKnowledge().getHref(), ids));
    return List.copyOf(result);
  }

  private static void collectReferencedSlots(
      RuntimeDecisionTable table, Set<Integer> slots) {
    table.inputs().forEach(input -> {
      collectReferencedSlots(input.expression(), slots);
      input.allowedValues().ifPresent(tests -> collectReferencedSlots(tests, slots));
    });
    table.outputs().forEach(output -> {
      output.allowedValues().ifPresent(tests -> collectReferencedSlots(tests, slots));
      output.defaultValue().ifPresent(value -> collectReferencedSlots(value, slots));
    });
    table.rules().forEach(rule -> {
      rule.inputEntries().forEach(tests -> collectReferencedSlots(tests, slots));
      rule.outputEntries().forEach(value -> collectReferencedSlots(value, slots));
    });
  }

  private static void collectReferencedSlots(RuntimeUnaryTests tests, Set<Integer> slots) {
    for (RuntimeUnaryTest test : tests.tests()) {
      switch (test) {
        case RuntimeComparisonUnaryTest comparison ->
            collectReferencedSlots(comparison.endpoint(), slots);
        case RuntimeRangeUnaryTest range -> collectReferencedSlots(range.range(), slots);
        case RuntimeExpressionUnaryTest expression ->
            collectReferencedSlots(expression.expression(), slots);
      }
    }
  }

  private static void collectReferencedSlots(RuntimeExpression expression, Set<Integer> slots) {
    switch (expression) {
      case RuntimeValueReference reference -> slots.add(reference.sourceSlot());
      case RuntimeDecisionTableReference reference -> slots.add(reference.decisionSlot());
      case RuntimeUnaryExpression unary -> collectReferencedSlots(unary.operand(), slots);
      case RuntimeBinaryExpression binary -> {
        collectReferencedSlots(binary.left(), slots);
        collectReferencedSlots(binary.right(), slots);
      }
      case RuntimeConditionalExpression conditional -> {
        collectReferencedSlots(conditional.condition(), slots);
        collectReferencedSlots(conditional.thenExpression(), slots);
        collectReferencedSlots(conditional.elseExpression(), slots);
      }
      case RuntimeListExpression list ->
          list.elements().forEach(value -> collectReferencedSlots(value, slots));
      case RuntimeFunctionCall call ->
          call.arguments().forEach(value -> collectReferencedSlots(value, slots));
      case RuntimeContextExpression context -> context.entries().forEach(entry ->
          collectReferencedSlots(entry.expression(), slots));
      case RuntimePathExpression path -> collectReferencedSlots(path.source(), slots);
      case RuntimeDescendantExpression descendant ->
          collectReferencedSlots(descendant.source(), slots);
      case RuntimeRangeExpression range -> {
        range.lower().ifPresent(value -> collectReferencedSlots(value, slots));
        range.upper().ifPresent(value -> collectReferencedSlots(value, slots));
      }
      case RuntimeFilterExpression filter -> {
        collectReferencedSlots(filter.source(), slots);
        collectReferencedSlots(filter.filter(), slots);
      }
      case RuntimeBetweenExpression between -> {
        collectReferencedSlots(between.value(), slots);
        collectReferencedSlots(between.lower(), slots);
        collectReferencedSlots(between.upper(), slots);
      }
      case RuntimeInExpression in -> {
        collectReferencedSlots(in.value(), slots);
        collectReferencedSlots(in.tests(), slots);
      }
      case RuntimeInstanceOfExpression instance ->
          collectReferencedSlots(instance.expression(), slots);
      case RuntimeUnaryTestsExpression tests -> collectReferencedSlots(tests.tests(), slots);
      case RuntimeForExpression forExpression -> {
        forExpression.iterations().forEach(iteration -> {
          collectReferencedSlots(iteration.source(), slots);
          iteration.end().ifPresent(value -> collectReferencedSlots(value, slots));
        });
        collectReferencedSlots(forExpression.result(), slots);
      }
      case RuntimeQuantifiedExpression quantified -> {
        quantified.bindings().forEach(binding ->
            collectReferencedSlots(binding.source(), slots));
        collectReferencedSlots(quantified.satisfies(), slots);
      }
      case RuntimeFunctionDefinition function ->
          function.body().ifPresent(value -> collectReferencedSlots(value, slots));
      case RuntimeInvocationExpression invocation -> {
        invocation.target().ifPresent(value -> collectReferencedSlots(value, slots));
        invocation.namedArguments().forEach(argument ->
            collectReferencedSlots(argument.expression(), slots));
        invocation.positionalArguments().forEach(value -> collectReferencedSlots(value, slots));
      }
      case RuntimeRelationExpression relation -> relation.rows().forEach(row ->
          row.forEach(value -> collectReferencedSlots(value, slots)));
      case RuntimeConstant ignoredConstant -> { }
      case RuntimeLocalReference ignoredLocal -> { }
    }
  }

  private static List<Integer> runtimeEvaluationOrder(
      List<RuntimeDecision> decisions, List<RuntimeBkm> bkms) {
    Map<Integer, List<Integer>> dependencies = new LinkedHashMap<>();
    decisions.forEach(decision -> dependencies.put(decision.id(), decision.dependencies()));
    bkms.forEach(bkm -> dependencies.put(bkm.id(), bkm.dependencies()));
    List<Integer> order = new ArrayList<>();
    Set<Integer> complete = new HashSet<>();
    Set<Integer> visiting = new HashSet<>();
    dependencies.keySet().stream().sorted().forEach(id ->
        visitRuntimeNode(id, dependencies, complete, visiting, order));
    return List.copyOf(order);
  }

  private static void visitRuntimeNode(
      int id,
      Map<Integer, List<Integer>> dependencies,
      Set<Integer> complete,
      Set<Integer> visiting,
      List<Integer> order) {
    if (complete.contains(id)) {
      return;
    }
    if (!visiting.add(id)) {
      throw new RuntimeIrLoweringException("Runtime dependency cycle at node " + id + ".");
    }
    for (int dependency : dependencies.getOrDefault(id, List.of())) {
      if (dependencies.containsKey(dependency)) {
        visitRuntimeNode(dependency, dependencies, complete, visiting, order);
      }
    }
    visiting.remove(id);
    complete.add(id);
    order.add(id);
  }

  private static void addReference(Set<Integer> result, String href, Map<String, Integer> ids) {
    int hash = href.lastIndexOf('#');
    String key = hash > 0 ? href : referenceId(href);
    Integer id = ids.get(key);
    if (id != null) {
      result.add(id);
    }
  }

  private static String referenceId(String href) {
    int hash = href.lastIndexOf('#');
    return hash < 0 ? href : href.substring(hash + 1);
  }

}
