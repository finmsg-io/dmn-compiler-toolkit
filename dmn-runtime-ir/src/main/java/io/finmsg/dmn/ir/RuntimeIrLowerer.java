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

/** Lowers a successfully typed, single-model semantic result into structural Runtime IR. */
public final class RuntimeIrLowerer {

  public RuntimeModel lower(DmnSemanticPipelineResult analysis) {
    Objects.requireNonNull(analysis, "analysis");
    if (!analysis.isSuccess()) {
      throw new RuntimeIrLoweringException(
          "Runtime IR requires successful semantic analysis; found "
              + analysis.diagnostics().size() + " diagnostic(s).");
    }

    Definitions model = analysis.model();
    Map<String, ItemDefinition> itemTypes = new LinkedHashMap<>();
    model.getItemDefinitionsList().forEach(item ->
        itemTypes.put(item.getNode().getName(), item));

    Map<String, Integer> runtimeIdBySourceId = new HashMap<>();
    Map<String, Integer> valueSlotBySourceId = new HashMap<>();
    Map<String, DrgElement> elementBySourceId = new HashMap<>();
    int nextId = 0;
    for (DrgElement element : model.getDrgElementsList()) {
      Node node = executableNode(element);
      if (node == null) {
        continue;
      }
      if (!node.getId().isBlank()) {
        runtimeIdBySourceId.put(node.getId(), nextId);
        valueSlotBySourceId.put(node.getId(), nextId);
        elementBySourceId.put(node.getId(), element);
      }
      if (element.hasDecision()
          && element.getDecision().hasLogic()
          && element.getDecision().getLogic().hasDecisionTable()
          && !element.getDecision().getLogic().getDecisionTable().getNode().getId().isBlank()) {
        valueSlotBySourceId.put(
            element.getDecision().getLogic().getDecisionTable().getNode().getId(), nextId);
      }
      nextId++;
    }

    List<RuntimeInput> inputs = new ArrayList<>();
    List<RuntimeDecision> decisions = new ArrayList<>();
    List<RuntimeBkm> bkms = new ArrayList<>();
    int runtimeId = 0;
    for (DrgElement element : model.getDrgElementsList()) {
      switch (element.getElementCase()) {
        case INPUT_DATA -> {
          InputData input = element.getInputData();
          inputs.add(new RuntimeInput(runtimeId, runtimeId,
              lowerType(input.getVariable().getType(), itemTypes, new HashSet<>())));
          runtimeId++;
        }
        case DECISION -> {
          Decision decision = element.getDecision();
          Map<String, LocalSlotAddress> localSlots = new HashMap<>();
          int[] nextLocalSlot = {0};
          Optional<RuntimeExpression> expression = lowerDecisionExpression(
              decision, analysis.bindings(), valueSlotBySourceId, itemTypes,
              localSlots, nextLocalSlot);
          Optional<RuntimeDecisionTable> decisionTable = lowerDecisionTable(
              decision, analysis.bindings(), valueSlotBySourceId, itemTypes,
              localSlots, nextLocalSlot);
          Set<Integer> dependencies = new LinkedHashSet<>(
              decisionDependencies(decision, runtimeIdBySourceId));
          expression.ifPresent(value -> collectReferencedSlots(value, dependencies));
          decisionTable.ifPresent(value -> collectReferencedSlots(value, dependencies));
          decisions.add(new RuntimeDecision(runtimeId, runtimeId,
              lowerType(decision.getVariable().getType(), itemTypes, new HashSet<>()),
              List.copyOf(dependencies), expression, decisionTable,
              nextLocalSlot[0]));
          runtimeId++;
        }
        case BUSINESS_KNOWLEDGE_MODEL -> {
          BusinessKnowledgeModel bkm = element.getBusinessKnowledgeModel();
          RuntimeFunctionDefinition function = lowerBkmFunction(
              bkm, analysis.bindings(), valueSlotBySourceId, itemTypes);
          Set<Integer> dependencies = new LinkedHashSet<>(
              bkmDependencies(bkm, runtimeIdBySourceId));
          collectReferencedSlots(function, dependencies);
          bkms.add(new RuntimeBkm(runtimeId, runtimeId,
              lowerType(bkm.getVariable().getType(), itemTypes, new HashSet<>()),
              List.copyOf(dependencies),
              functionKind(bkm.getFunction().getKind()),
              Optional.of(function)));
          runtimeId++;
        }
        default -> { }
      }
    }

    // Preserve the semantic model-set guard even though runtime order is recomputed below.
    for (DrgElement element : analysis.compilationOrder()) {
      Node node = executableNode(element);
      Integer id = node == null ? null : runtimeIdBySourceId.get(node.getId());
      if (id == null || elementBySourceId.get(node.getId()) == null) {
        throw new RuntimeIrLoweringException(
            "Model-set compilation order requires model-set Runtime IR lowering.");
      }
    }
    List<Integer> order = runtimeEvaluationOrder(decisions, bkms);
    return new RuntimeModel(inputs, decisions, bkms, order, runtimeId);
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
          lowerType(parameter.getType(), itemTypes, new HashSet<>())));
    }
    RuntimeExpression body = lowerExpression(function.getLogic().getParsed().getAst(),
        path + "/logic", bindings, slots, itemTypes, localSlots, nextLocalSlot);
    RuntimeType type = lowerType(bkm.getVariable().getType(), itemTypes, new HashSet<>());
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
        yield Optional.of(lowerExpression(
            decision.getLogic().getLiteralExpression().getParsed().getAst(),
            basePath + "/literalExpression", bindings, slots, itemTypes,
            localSlots, nextLocalSlot));
      }
      case BOXED_EXPRESSION -> {
        if (!decision.getLogic().getBoxedExpression().hasParsed()) {
          throw new RuntimeIrLoweringException(
              "Decision '" + decision.getNode().getName() + "' requires parsed boxed logic.");
        }
        RuntimeType expected = lowerType(
            decision.getVariable().getType(), itemTypes, new HashSet<>());
        yield Optional.of(lowerBoxedExpression(
            decision.getLogic().getBoxedExpression().getParsed(), basePath + "/boxedExpression",
            bindings, slots, itemTypes, localSlots, nextLocalSlot, expected));
      }
      case INVOCATION -> Optional.of(lowerDmnInvocation(
          decision.getLogic().getInvocation(), basePath + "/invocation", bindings, slots,
          itemTypes, localSlots, nextLocalSlot,
          lowerType(decision.getVariable().getType(), itemTypes, new HashSet<>())));
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
    RuntimeExpression target = lowerExpression(invocation.getExpression().getParsed().getAst(),
        path + "/expression", bindings, slots, itemTypes, localSlots, nextLocalSlot);
    List<RuntimeNamedArgument> arguments = new ArrayList<>();
    for (int index = 0; index < invocation.getBindingsCount(); index++) {
      Binding binding = invocation.getBindings(index);
      if (!binding.hasExpression() || !binding.getExpression().hasParsed()) {
        throw new RuntimeIrLoweringException(
            "DMN invocation binding requires parsed FEEL at " + path + "/binding[" + index + "]");
      }
      arguments.add(new RuntimeNamedArgument(binding.getParameter(), lowerExpression(
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
      case FEEL -> lowerExpression(parsed.getFeel().getAst(), path, bindings, slots, itemTypes,
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
        Map<String, LocalSlotAddress> contextSlots = new HashMap<>(localSlots);
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
                lowerType(column.getVariable().getType(), itemTypes, new HashSet<>())))
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
                RuntimeType.contextFields(relationFields(columns)))
            : expectedType;
        yield new RuntimeRelationExpression(columns, rows, relationType);
      }
      case FUNCTION_DEFINITION -> {
        FunctionDefinitionParsed function = boxed.getFunctionDefinition();
        List<RuntimeFunctionParameter> parameters = new ArrayList<>();
        Map<String, LocalSlotAddress> parameterSlots = capturedSlots(localSlots);
        int[] functionNextLocalSlot = {0};
        for (int index = 0; index < function.getParametersCount(); index++) {
          InformationItem parameter = function.getParameters(index);
          int localSlot = functionNextLocalSlot[0]++;
          parameterSlots.put(path + "/parameter[" + index + "]",
              new LocalSlotAddress(0, localSlot));
          parameters.add(new RuntimeFunctionParameter(parameter.getNode().getName(), localSlot,
              lowerType(parameter.getType(), itemTypes, new HashSet<>())));
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
          ? Optional.of(lowerUnaryTests(
              input.getInputValues().getParsed().getAst().getUnaryTests(),
              inputPath + "/inputValues", bindings, slots, itemTypes,
              localSlots, nextLocalSlot)) : Optional.empty();
      inputs.add(new RuntimeDecisionTableInput(
          lowerExpression(input.getInputExpression().getParsed().getAst(), inputPath,
              bindings, slots, itemTypes, localSlots, nextLocalSlot),
          allowed, lowerType(input.getType(), itemTypes, new HashSet<>())));
    }
    List<RuntimeDecisionTableOutput> outputs = new ArrayList<>();
    for (int index = 0; index < table.getOutputsCount(); index++) {
      OutputClause output = table.getOutputs(index);
      String outputPath = path + "/output[" + index + "]";
      Optional<RuntimeUnaryTests> allowed = output.hasOutputValues()
          && output.getOutputValues().hasParsed()
          ? Optional.of(lowerUnaryTests(
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
        defaultValue = Optional.of(lowerExpression(parsed.getFeel().getAst(),
            outputPath + "/defaultOutputEntry", bindings, slots, itemTypes,
            localSlots, nextLocalSlot));
      }
      outputs.add(new RuntimeDecisionTableOutput(
          output.getNode().getName().isBlank()
              ? Optional.empty() : Optional.of(output.getNode().getName()),
          lowerType(output.getType(), itemTypes, new HashSet<>()), allowed, defaultValue));
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
        inputEntries.add(lowerUnaryTests(entry.getParsed().getTests(),
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
        outputEntries.add(lowerExpression(entry.getParsed().getAst(),
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

  private static RuntimeExpression lowerExpression(
      Expression expression,
      String path,
      List<io.finmsg.dmn.semantic.analysis.DmnSymbolBinding> bindings,
      Map<String, Integer> slots,
      Map<String, ItemDefinition> itemTypes,
      Map<String, LocalSlotAddress> localSlots,
      int[] nextLocalSlot) {
    RuntimeType type = lowerType(expression.getInferredType(), itemTypes, new HashSet<>());
    return switch (expression.getNodeCase()) {
      case LITERAL -> new RuntimeConstant(
          constantKind(expression.getLiteral().getKind()), expression.getLiteral().getValue(), type);
      case NAME -> {
        var binding = bindings.stream()
            .filter(value -> value.referencePath().equals(path))
            .findFirst()
            .orElseThrow(() -> new RuntimeIrLoweringException(
                "Missing semantic binding for decision expression at " + path + "."));
        if (binding.kind() == io.finmsg.dmn.semantic.analysis.DmnSymbolKind.LOCAL_VARIABLE
            || binding.kind() == io.finmsg.dmn.semantic.analysis.DmnSymbolKind.PARAMETER) {
          LocalSlotAddress localSlot = localSlots.get(binding.declarationPath());
          if (localSlot == null) {
            throw new RuntimeIrLoweringException(
                "Local expression binding is not in scope at " + path + ".");
          }
          yield new RuntimeLocalReference(localSlot.lexicalDepth(), localSlot.localSlot(), type);
        }
        Integer slot = slots.get(binding.symbolId());
        if (slot == null) {
          throw new RuntimeIrLoweringException(
              "Expression binding targets a value outside the current runtime model: '"
                  + binding.symbolName() + "'.");
        }
        yield new RuntimeValueReference(slot, type);
      }
      case UNARY -> new RuntimeUnaryExpression(
          unaryOperator(expression.getUnary().getOperator()),
          lowerExpression(
              expression.getUnary().getExpression(), path + "/unary", bindings, slots, itemTypes,
              localSlots, nextLocalSlot),
          type);
      case BINARY -> new RuntimeBinaryExpression(
          binaryOperator(expression.getBinary().getOperator()),
          lowerExpression(
              expression.getBinary().getLeft(), path + "/left", bindings, slots, itemTypes,
              localSlots, nextLocalSlot),
          lowerExpression(
              expression.getBinary().getRight(), path + "/right", bindings, slots, itemTypes,
              localSlots, nextLocalSlot),
          type);
      case IF_EXPRESSION -> new RuntimeConditionalExpression(
          lowerExpression(
              expression.getIfExpression().getCondition(),
              path + "/condition", bindings, slots, itemTypes, localSlots, nextLocalSlot),
          lowerExpression(
              expression.getIfExpression().getThenExpression(),
              path + "/then", bindings, slots, itemTypes, localSlots, nextLocalSlot),
          lowerExpression(
              expression.getIfExpression().getElseExpression(),
              path + "/else", bindings, slots, itemTypes, localSlots, nextLocalSlot),
          type);
      case LIST -> {
        List<RuntimeExpression> elements = new ArrayList<>();
        for (int index = 0; index < expression.getList().getElementsCount(); index++) {
          elements.add(lowerExpression(
              expression.getList().getElements(index), path + "/element[" + index + "]",
              bindings, slots, itemTypes, localSlots, nextLocalSlot));
        }
        yield new RuntimeListExpression(elements, type);
      }
      case FUNCTION_CALL -> {
        List<RuntimeExpression> arguments = new ArrayList<>();
        for (int index = 0; index < expression.getFunctionCall().getArgumentsCount(); index++) {
          arguments.add(lowerExpression(
              expression.getFunctionCall().getArguments(index), path + "/argument[" + index + "]",
              bindings, slots, itemTypes, localSlots, nextLocalSlot));
        }
        yield new RuntimeFunctionCall(
            expression.getFunctionCall().getFunction(), arguments, type);
      }
      case CONTEXT -> {
        List<RuntimeContextEntry> entries = new ArrayList<>();
        Map<String, LocalSlotAddress> contextSlots = new HashMap<>(localSlots);
        for (int index = 0; index < expression.getContext().getEntriesCount(); index++) {
          ContextEntry entry = expression.getContext().getEntries(index);
          String entryPath = path + "/entry[" + index + "]";
          RuntimeExpression value = lowerExpression(
              entry.getExpression(), entryPath, bindings, slots, itemTypes,
              contextSlots, nextLocalSlot);
          int localSlot = nextLocalSlot[0]++;
          contextSlots.put(entryPath, new LocalSlotAddress(0, localSlot));
          entries.add(new RuntimeContextEntry(entry.getName(), localSlot, value));
        }
        yield new RuntimeContextExpression(entries, type);
      }
      case PATH -> {
        RuntimeExpression source = lowerExpression(expression.getPath().getSource(),
            path + "/source", bindings, slots, itemTypes, localSlots, nextLocalSlot);
        yield new RuntimePathExpression(source, expression.getPath().getMember(),
            resolvedFieldIndex(source.type(), expression.getPath().getMember()), type);
      }
      case RANGE -> lowerRange(expression.getRange(), type, path, bindings, slots, itemTypes,
          localSlots, nextLocalSlot);
      case FILTER -> new RuntimeFilterExpression(
          lowerExpression(expression.getFilter().getSource(), path + "/source", bindings, slots,
              itemTypes, localSlots, nextLocalSlot),
          lowerExpression(expression.getFilter().getFilter(), path + "/filter", bindings, slots,
              itemTypes, localSlots, nextLocalSlot),
          type);
      case BETWEEN -> new RuntimeBetweenExpression(
          lowerExpression(expression.getBetween().getValue(), path + "/value", bindings, slots,
              itemTypes, localSlots, nextLocalSlot),
          lowerExpression(expression.getBetween().getLower(), path + "/lower", bindings, slots,
              itemTypes, localSlots, nextLocalSlot),
          lowerExpression(expression.getBetween().getUpper(), path + "/upper", bindings, slots,
              itemTypes, localSlots, nextLocalSlot),
          type);
      case IN -> new RuntimeInExpression(
          lowerExpression(expression.getIn().getValue(), path + "/value", bindings, slots,
              itemTypes, localSlots, nextLocalSlot),
          lowerUnaryTests(expression.getIn().getTests(), path + "/tests", bindings, slots,
              itemTypes, localSlots, nextLocalSlot),
          type);
      case INSTANCE_OF -> new RuntimeInstanceOfExpression(
          lowerExpression(expression.getInstanceOf().getExpression(), path + "/expression",
              bindings, slots, itemTypes, localSlots, nextLocalSlot),
          lowerFeelType(expression.getInstanceOf().getType(), itemTypes), type);
      case UNARY_TESTS -> new RuntimeUnaryTestsExpression(
          lowerUnaryTests(expression.getUnaryTests(), path, bindings, slots, itemTypes,
              localSlots, nextLocalSlot), type);
      case FOR_EXPRESSION -> lowerFor(expression.getForExpression(), type, path, bindings, slots,
          itemTypes, localSlots, nextLocalSlot);
      case QUANTIFIED -> lowerQuantified(expression.getQuantified(), type, path, bindings, slots,
          itemTypes, localSlots, nextLocalSlot);
      case FUNCTION_DEFINITION -> lowerFunctionDefinition(
          expression.getFunctionDefinition(), type, path, bindings, slots, itemTypes,
          localSlots, nextLocalSlot);
      case INVOCATION -> lowerInvocation(expression.getInvocation(), type, path, bindings, slots,
          itemTypes, localSlots, nextLocalSlot);
      case DESCENDANT -> {
        RuntimeExpression source = lowerExpression(expression.getDescendant().getSource(),
            path + "/source", bindings, slots, itemTypes, localSlots, nextLocalSlot);
        yield new RuntimeDescendantExpression(source, expression.getDescendant().getMember(),
            resolvedFieldIndex(source.type(), expression.getDescendant().getMember()), type);
      }
      case DECISION_TABLE -> {
        Integer decisionSlot = slots.get(expression.getDecisionTable().getDecisionTableId());
        if (decisionSlot == null) {
          throw new RuntimeIrLoweringException(
              "Decision-table reference is outside the current runtime model at " + path + ".");
        }
        yield new RuntimeDecisionTableReference(decisionSlot, type);
      }
      default -> throw new RuntimeIrLoweringException(
          "Unsupported Runtime IR expression " + expression.getNodeCase()
              + " at " + path + ".");
    };
  }

  private static RuntimeInvocationExpression lowerInvocation(
      InvocationExpression value,
      RuntimeType type,
      String path,
      List<io.finmsg.dmn.semantic.analysis.DmnSymbolBinding> bindings,
      Map<String, Integer> slots,
      Map<String, ItemDefinition> itemTypes,
      Map<String, LocalSlotAddress> localSlots,
      int[] nextLocalSlot) {
    boolean boundTarget = bindings.stream()
        .anyMatch(binding -> binding.referencePath().equals(path + "/target"));
    Optional<String> function = value.getTarget().hasName() && !boundTarget
        ? Optional.of(value.getTarget().getName().getName()) : Optional.empty();
    Optional<RuntimeExpression> target = function.isPresent() ? Optional.empty()
        : Optional.of(lowerExpression(value.getTarget(), path + "/target", bindings, slots,
            itemTypes, localSlots, nextLocalSlot));
    List<RuntimeNamedArgument> namedArguments = new ArrayList<>();
    for (int index = 0; index < value.getArgumentsCount(); index++) {
      NamedArgument argument = value.getArguments(index);
      namedArguments.add(new RuntimeNamedArgument(argument.getName(), lowerExpression(
          argument.getExpression(), path + "/argument[" + index + "]", bindings, slots,
          itemTypes, localSlots, nextLocalSlot)));
    }
    List<RuntimeExpression> positionalArguments = new ArrayList<>();
    for (int index = 0; index < value.getPositionalArgumentsCount(); index++) {
      positionalArguments.add(lowerExpression(value.getPositionalArguments(index),
          path + "/argument[" + index + "]", bindings, slots, itemTypes,
          localSlots, nextLocalSlot));
    }
    return new RuntimeInvocationExpression(
        function, target, namedArguments, positionalArguments, type);
  }

  private static RuntimeForExpression lowerFor(
      ForExpression value,
      RuntimeType type,
      String path,
      List<io.finmsg.dmn.semantic.analysis.DmnSymbolBinding> bindings,
      Map<String, Integer> slots,
      Map<String, ItemDefinition> itemTypes,
      Map<String, LocalSlotAddress> localSlots,
      int[] nextLocalSlot) {
    List<RuntimeIteration> iterations = new ArrayList<>();
    Map<String, LocalSlotAddress> iterationSlots = new HashMap<>(localSlots);
    if (value.getIterationsCount() == 0) {
      if (value.getVariable().isBlank()) {
        throw new RuntimeIrLoweringException("For expression has no iteration at " + path + ".");
      }
      RuntimeExpression source = lowerExpression(value.getIn(), path + "/in", bindings, slots,
          itemTypes, iterationSlots, nextLocalSlot);
      int localSlot = nextLocalSlot[0]++;
      iterationSlots.put(path, new LocalSlotAddress(0, localSlot));
      iterations.add(new RuntimeIteration(localSlot, source, Optional.empty()));
    } else {
      for (int index = 0; index < value.getIterationsCount(); index++) {
        IterationContext iteration = value.getIterations(index);
        String iterationPath = path + "/iteration[" + index + "]";
        RuntimeExpression source = lowerExpression(iteration.getStart(), iterationPath + "/in",
            bindings, slots, itemTypes, iterationSlots, nextLocalSlot);
        Optional<RuntimeExpression> end = iteration.hasEnd()
            ? Optional.of(lowerExpression(iteration.getEnd(), iterationPath + "/end", bindings,
                slots, itemTypes, iterationSlots, nextLocalSlot)) : Optional.empty();
        int localSlot = nextLocalSlot[0]++;
        iterationSlots.put(iterationPath, new LocalSlotAddress(0, localSlot));
        iterations.add(new RuntimeIteration(localSlot, source, end));
      }
    }
    RuntimeExpression result = lowerExpression(value.getReturnExpression(), path + "/return",
        bindings, slots, itemTypes, iterationSlots, nextLocalSlot);
    return new RuntimeForExpression(iterations, result, type);
  }

  private static RuntimeQuantifiedExpression lowerQuantified(
      QuantifiedExpression value,
      RuntimeType type,
      String path,
      List<io.finmsg.dmn.semantic.analysis.DmnSymbolBinding> bindings,
      Map<String, Integer> slots,
      Map<String, ItemDefinition> itemTypes,
      Map<String, LocalSlotAddress> localSlots,
      int[] nextLocalSlot) {
    List<RuntimeQuantifiedBinding> loweredBindings = new ArrayList<>();
    Map<String, LocalSlotAddress> quantifiedSlots = new HashMap<>(localSlots);
    if (value.getBindingsCount() == 0) {
      if (value.getVariable().isBlank()) {
        throw new RuntimeIrLoweringException(
            "Quantified expression has no binding at " + path + ".");
      }
      RuntimeExpression source = lowerExpression(value.getIn(), path + "/in", bindings, slots,
          itemTypes, quantifiedSlots, nextLocalSlot);
      int localSlot = nextLocalSlot[0]++;
      quantifiedSlots.put(path, new LocalSlotAddress(0, localSlot));
      loweredBindings.add(new RuntimeQuantifiedBinding(localSlot, source));
    } else {
      for (int index = 0; index < value.getBindingsCount(); index++) {
        IterationBinding binding = value.getBindings(index);
        String bindingPath = path + "/binding[" + index + "]";
        RuntimeExpression source = lowerExpression(binding.getIn(), bindingPath + "/in", bindings,
            slots, itemTypes, quantifiedSlots, nextLocalSlot);
        int localSlot = nextLocalSlot[0]++;
        quantifiedSlots.put(bindingPath, new LocalSlotAddress(0, localSlot));
        loweredBindings.add(new RuntimeQuantifiedBinding(localSlot, source));
      }
    }
    RuntimeExpression satisfies = lowerExpression(value.getSatisfies(), path + "/satisfies",
        bindings, slots, itemTypes, quantifiedSlots, nextLocalSlot);
    return new RuntimeQuantifiedExpression(
        quantifier(value.getQuantifier()), loweredBindings, satisfies, type);
  }

  private static RuntimeFunctionDefinition lowerFunctionDefinition(
      FunctionDefinitionExpression value,
      RuntimeType type,
      String path,
      List<io.finmsg.dmn.semantic.analysis.DmnSymbolBinding> bindings,
      Map<String, Integer> slots,
      Map<String, ItemDefinition> itemTypes,
      Map<String, LocalSlotAddress> localSlots,
      int[] nextLocalSlot) {
    List<RuntimeFunctionParameter> parameters = new ArrayList<>();
    Map<String, LocalSlotAddress> parameterSlots = capturedSlots(localSlots);
    int[] functionNextLocalSlot = {0};
    for (int index = 0; index < value.getParametersCount(); index++) {
      FormalParameter parameter = value.getParameters(index);
      String parameterPath = path + "/parameter[" + index + "]";
      int localSlot = functionNextLocalSlot[0]++;
      parameterSlots.put(parameterPath, new LocalSlotAddress(0, localSlot));
      parameters.add(new RuntimeFunctionParameter(
          parameter.getName(), localSlot, lowerFeelType(parameter.getType(), itemTypes)));
    }
    Optional<RuntimeExpression> body = value.getBody().getNodeCase() == Expression.NodeCase.NODE_NOT_SET
        ? Optional.empty()
        : Optional.of(lowerExpression(value.getBody(), path + "/body", bindings, slots,
            itemTypes, parameterSlots, functionNextLocalSlot));
    return new RuntimeFunctionDefinition(
        parameters, body, value.getExternal(), functionNextLocalSlot[0], type);
  }

  private static RuntimeQuantifier quantifier(Quantifier quantifier) {
    return switch (quantifier) {
      case QUANTIFIER_SOME -> RuntimeQuantifier.SOME;
      case QUANTIFIER_EVERY -> RuntimeQuantifier.EVERY;
      case QUANTIFIER_UNSPECIFIED, UNRECOGNIZED -> throw new RuntimeIrLoweringException(
          "Unsupported FEEL quantifier " + quantifier + ".");
    };
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

  private static RuntimeRangeExpression lowerRange(
      RangeExpression range,
      RuntimeType type,
      String path,
      List<io.finmsg.dmn.semantic.analysis.DmnSymbolBinding> bindings,
      Map<String, Integer> slots,
      Map<String, ItemDefinition> itemTypes,
      Map<String, LocalSlotAddress> localSlots,
      int[] nextLocalSlot) {
    Optional<RuntimeExpression> lower = range.hasLower()
        ? Optional.of(lowerExpression(range.getLower(), path + "/lower", bindings, slots,
            itemTypes, localSlots, nextLocalSlot)) : Optional.empty();
    Optional<RuntimeExpression> upper = range.hasUpper()
        ? Optional.of(lowerExpression(range.getUpper(), path + "/upper", bindings, slots,
            itemTypes, localSlots, nextLocalSlot)) : Optional.empty();
    return new RuntimeRangeExpression(lower, upper,
        rangeBoundary(range.getLowerBoundary()), rangeBoundary(range.getUpperBoundary()), type);
  }

  private static RuntimeUnaryTests lowerUnaryTests(
      UnaryTestsExpression tests,
      String path,
      List<io.finmsg.dmn.semantic.analysis.DmnSymbolBinding> bindings,
      Map<String, Integer> slots,
      Map<String, ItemDefinition> itemTypes,
      Map<String, LocalSlotAddress> localSlots,
      int[] nextLocalSlot) {
    List<RuntimeUnaryTest> lowered = new ArrayList<>();
    for (int index = 0; index < tests.getTestsCount(); index++) {
      PositiveUnaryTest test = tests.getTests(index);
      String testPath = path + "/test[" + index + "]";
      lowered.add(switch (test.getTypeCase()) {
        case COMPARISON -> new RuntimeComparisonUnaryTest(
            unaryTestOperator(test.getComparison().getOperator()),
            lowerExpression(test.getComparison().getEndpoint(), testPath, bindings, slots,
                itemTypes, localSlots, nextLocalSlot));
        case RANGE -> {
          RuntimeType elementType = test.getRange().hasLower()
              ? lowerType(test.getRange().getLower().getInferredType(), itemTypes, new HashSet<>())
              : test.getRange().hasUpper()
                  ? lowerType(test.getRange().getUpper().getInferredType(), itemTypes,
                      new HashSet<>())
                  : RuntimeType.scalar(RuntimeTypeKind.ANY);
          yield new RuntimeRangeUnaryTest(lowerRange(test.getRange(),
              RuntimeType.element(RuntimeTypeKind.RANGE, elementType), testPath, bindings, slots,
              itemTypes, localSlots, nextLocalSlot));
        }
        case EXPRESSION -> new RuntimeExpressionUnaryTest(lowerExpression(
            test.getExpression(), testPath, bindings, slots, itemTypes, localSlots, nextLocalSlot));
        case TYPE_NOT_SET -> throw new RuntimeIrLoweringException(
            "Unsupported empty unary test at " + testPath + ".");
      });
    }
    return new RuntimeUnaryTests(tests.getNegated(), tests.getWildcard(), lowered);
  }

  private static RuntimeRangeBoundary rangeBoundary(RangeBoundary boundary) {
    return switch (boundary) {
      case RANGE_BOUNDARY_OPEN -> RuntimeRangeBoundary.OPEN;
      case RANGE_BOUNDARY_CLOSED -> RuntimeRangeBoundary.CLOSED;
      case RANGE_BOUNDARY_UNSPECIFIED, UNRECOGNIZED -> throw new RuntimeIrLoweringException(
          "Unsupported FEEL range boundary " + boundary + ".");
    };
  }

  private static RuntimeUnaryTestOperator unaryTestOperator(UnaryTestOperator operator) {
    return switch (operator) {
      case UNARY_TEST_OPERATOR_EQUAL -> RuntimeUnaryTestOperator.EQUAL;
      case UNARY_TEST_OPERATOR_NOT_EQUAL -> RuntimeUnaryTestOperator.NOT_EQUAL;
      case UNARY_TEST_OPERATOR_LESS -> RuntimeUnaryTestOperator.LESS;
      case UNARY_TEST_OPERATOR_LESS_EQUAL -> RuntimeUnaryTestOperator.LESS_EQUAL;
      case UNARY_TEST_OPERATOR_GREATER -> RuntimeUnaryTestOperator.GREATER;
      case UNARY_TEST_OPERATOR_GREATER_EQUAL -> RuntimeUnaryTestOperator.GREATER_EQUAL;
      case UNARY_TEST_OPERATOR_UNSPECIFIED, UNRECOGNIZED ->
          throw new RuntimeIrLoweringException("Unsupported FEEL unary-test operator "
              + operator + ".");
    };
  }

  private static RuntimeUnaryOperator unaryOperator(UnaryOperator operator) {
    return switch (operator) {
      case UNARY_OPERATOR_PLUS -> RuntimeUnaryOperator.POSITIVE;
      case UNARY_OPERATOR_MINUS -> RuntimeUnaryOperator.NEGATE;
      case UNARY_OPERATOR_NOT -> RuntimeUnaryOperator.NOT;
      case UNARY_OPERATOR_UNSPECIFIED, UNRECOGNIZED -> throw new RuntimeIrLoweringException(
          "Unsupported FEEL unary operator " + operator + ".");
    };
  }

  private static RuntimeBinaryOperator binaryOperator(BinaryOperator operator) {
    return switch (operator) {
      case BINARY_OPERATOR_ADD -> RuntimeBinaryOperator.ADD;
      case BINARY_OPERATOR_SUBTRACT -> RuntimeBinaryOperator.SUBTRACT;
      case BINARY_OPERATOR_MULTIPLY -> RuntimeBinaryOperator.MULTIPLY;
      case BINARY_OPERATOR_DIVIDE -> RuntimeBinaryOperator.DIVIDE;
      case BINARY_OPERATOR_POWER -> RuntimeBinaryOperator.POWER;
      case BINARY_OPERATOR_EQUAL -> RuntimeBinaryOperator.EQUAL;
      case BINARY_OPERATOR_NOT_EQUAL -> RuntimeBinaryOperator.NOT_EQUAL;
      case BINARY_OPERATOR_LESS -> RuntimeBinaryOperator.LESS;
      case BINARY_OPERATOR_LESS_EQUAL -> RuntimeBinaryOperator.LESS_EQUAL;
      case BINARY_OPERATOR_GREATER -> RuntimeBinaryOperator.GREATER;
      case BINARY_OPERATOR_GREATER_EQUAL -> RuntimeBinaryOperator.GREATER_EQUAL;
      case BINARY_OPERATOR_AND -> RuntimeBinaryOperator.AND;
      case BINARY_OPERATOR_OR -> RuntimeBinaryOperator.OR;
      case BINARY_OPERATOR_UNSPECIFIED, UNRECOGNIZED -> throw new RuntimeIrLoweringException(
          "Unsupported FEEL binary operator " + operator + ".");
    };
  }

  private static RuntimeConstantKind constantKind(LiteralKind kind) {
    return switch (kind) {
      case LITERAL_KIND_NULL -> RuntimeConstantKind.NULL;
      case LITERAL_KIND_BOOLEAN -> RuntimeConstantKind.BOOLEAN;
      case LITERAL_KIND_NUMBER -> RuntimeConstantKind.NUMBER;
      case LITERAL_KIND_STRING -> RuntimeConstantKind.STRING;
      case LITERAL_KIND_DATE -> RuntimeConstantKind.DATE;
      case LITERAL_KIND_TIME -> RuntimeConstantKind.TIME;
      case LITERAL_KIND_DATE_TIME -> RuntimeConstantKind.DATE_TIME;
      case LITERAL_KIND_DURATION -> RuntimeConstantKind.DURATION;
      case LITERAL_KIND_UNSPECIFIED, UNRECOGNIZED -> throw new RuntimeIrLoweringException(
          "Unsupported FEEL literal kind " + kind + ".");
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
    Integer id = ids.get(referenceId(href));
    if (id != null) {
      result.add(id);
    }
  }

  private static String referenceId(String href) {
    int hash = href.lastIndexOf('#');
    return hash < 0 ? href : href.substring(hash + 1);
  }

  private static Node executableNode(DrgElement element) {
    return switch (element.getElementCase()) {
      case INPUT_DATA -> element.getInputData().getNode();
      case DECISION -> element.getDecision().getNode();
      case BUSINESS_KNOWLEDGE_MODEL -> element.getBusinessKnowledgeModel().getNode();
      default -> null;
    };
  }

  private static RuntimeType lowerType(
      TypeReference type, Map<String, ItemDefinition> items, Set<String> resolving) {
    return switch (type.getKindCase()) {
      case BUILTIN -> RuntimeType.scalar(builtinKind(type.getBuiltin()));
      case LIST -> RuntimeType.element(RuntimeTypeKind.LIST,
          lowerType(type.getList().getElementType(), items, resolving));
      case RANGE -> RuntimeType.element(RuntimeTypeKind.RANGE,
          lowerType(type.getRange().getElementType(), items, resolving));
      case CONTEXT -> lowerContextType(type.getContext(), items, resolving);
      case FUNCTION -> RuntimeType.function(type.getFunction().getParameterTypeList().stream()
              .map(parameter -> lowerType(parameter, items, new HashSet<>(resolving))).toList(),
          lowerType(type.getFunction().getReturnType(), items, new HashSet<>(resolving)));
      case NAMED -> lowerNamed(type.getNamed(), items, resolving);
      case KIND_NOT_SET -> RuntimeType.scalar(RuntimeTypeKind.ANY);
    };
  }

  private static RuntimeType lowerContextType(
      ContextTypeReference context,
      Map<String, ItemDefinition> items,
      Set<String> resolving) {
    List<RuntimeField> fields = new ArrayList<>();
    for (int index = 0; index < context.getEntriesCount(); index++) {
      ContextEntryTypeReference entry = context.getEntries(index);
      fields.add(new RuntimeField(index, entry.getName(),
          lowerType(entry.getType(), items, new HashSet<>(resolving))));
    }
    return RuntimeType.contextFields(fields);
  }

  private static List<RuntimeField> relationFields(List<RuntimeRelationColumn> columns) {
    List<RuntimeField> fields = new ArrayList<>();
    for (int index = 0; index < columns.size(); index++) {
      RuntimeRelationColumn column = columns.get(index);
      fields.add(new RuntimeField(index, column.name(), column.type()));
    }
    return List.copyOf(fields);
  }

  private static int resolvedFieldIndex(RuntimeType source, String member) {
    RuntimeType candidate = source.kind() == RuntimeTypeKind.LIST ? source.elementType() : source;
    if (candidate == null || candidate.kind() != RuntimeTypeKind.CONTEXT) {
      return -1;
    }
    return candidate.fieldLayout().stream()
        .filter(field -> field.name().equals(member))
        .map(RuntimeField::index)
        .findFirst()
        .orElse(-1);
  }

  private static RuntimeType lowerFeelType(
      FeelType type, Map<String, ItemDefinition> items) {
    return switch (type.getTypeCase()) {
      case QUALIFIED_NAME -> switch (type.getQualifiedName()) {
        case "any" -> RuntimeType.scalar(RuntimeTypeKind.ANY);
        case "null" -> RuntimeType.scalar(RuntimeTypeKind.NULL);
        case "boolean" -> RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);
        case "number" -> RuntimeType.scalar(RuntimeTypeKind.NUMBER);
        case "string" -> RuntimeType.scalar(RuntimeTypeKind.STRING);
        case "date" -> RuntimeType.scalar(RuntimeTypeKind.DATE);
        case "time" -> RuntimeType.scalar(RuntimeTypeKind.TIME);
        case "date and time" -> RuntimeType.scalar(RuntimeTypeKind.DATE_TIME);
        case "duration" -> RuntimeType.scalar(RuntimeTypeKind.DURATION);
        case "years and months duration" ->
            RuntimeType.scalar(RuntimeTypeKind.YEARS_MONTHS_DURATION);
        case "days and time duration" -> RuntimeType.scalar(RuntimeTypeKind.DAYS_TIME_DURATION);
        case "range" -> RuntimeType.element(
            RuntimeTypeKind.RANGE, RuntimeType.scalar(RuntimeTypeKind.ANY));
        default -> lowerNamed(NamedTypeReference.newBuilder()
            .setName(type.getQualifiedName()).build(), items, new HashSet<>());
      };
      case RANGE -> RuntimeType.element(RuntimeTypeKind.RANGE,
          lowerFeelType(type.getRange().getElementType(), items));
      case LIST -> RuntimeType.element(RuntimeTypeKind.LIST,
          lowerFeelType(type.getList().getElementType(), items));
      case CONTEXT -> {
        List<RuntimeField> fields = new ArrayList<>();
        for (int index = 0; index < type.getContext().getEntriesCount(); index++) {
          ContextTypeEntry entry = type.getContext().getEntries(index);
          fields.add(new RuntimeField(index, entry.getName(), lowerFeelType(entry.getType(), items)));
        }
        yield RuntimeType.contextFields(fields);
      }
      case FUNCTION -> RuntimeType.function(type.getFunction().getParameterTypesList().stream()
              .map(parameter -> lowerFeelType(parameter, items)).toList(),
          lowerFeelType(type.getFunction().getReturnType(), items));
      case TYPE_NOT_SET -> RuntimeType.scalar(RuntimeTypeKind.ANY);
    };
  }

  private static RuntimeType lowerNamed(
      NamedTypeReference named, Map<String, ItemDefinition> items, Set<String> resolving) {
    if (!named.getNamespace().isBlank()) {
      throw new RuntimeIrLoweringException(
          "Imported named types require model-set Runtime IR lowering.");
    }
    ItemDefinition item = items.get(named.getName());
    if (item == null || !resolving.add(named.getName())) {
      throw new RuntimeIrLoweringException("Unresolved runtime type '" + named.getName() + "'.");
    }
    RuntimeType result;
    if (item.getComponentsCount() > 0) {
      List<RuntimeField> fields = new ArrayList<>();
      for (int index = 0; index < item.getComponentsCount(); index++) {
        ItemComponent component = item.getComponents(index);
        RuntimeType field = lowerType(component.getType(), items, new HashSet<>(resolving));
        if (component.getIsCollection()) {
          field = RuntimeType.element(RuntimeTypeKind.LIST, field);
        }
        fields.add(new RuntimeField(index, component.getNode().getName(), field));
      }
      result = RuntimeType.contextFields(fields);
    } else {
      result = lowerType(item.getType(), items, resolving);
    }
    return item.getIsCollection() ? RuntimeType.element(RuntimeTypeKind.LIST, result) : result;
  }

  private static RuntimeTypeKind builtinKind(BuiltinType type) {
    return switch (type) {
      case BUILTIN_TYPE_NULL -> RuntimeTypeKind.NULL;
      case BUILTIN_TYPE_BOOLEAN -> RuntimeTypeKind.BOOLEAN;
      case BUILTIN_TYPE_NUMBER -> RuntimeTypeKind.NUMBER;
      case BUILTIN_TYPE_STRING -> RuntimeTypeKind.STRING;
      case BUILTIN_TYPE_DATE -> RuntimeTypeKind.DATE;
      case BUILTIN_TYPE_TIME -> RuntimeTypeKind.TIME;
      case BUILTIN_TYPE_DATE_AND_TIME -> RuntimeTypeKind.DATE_TIME;
      case BUILTIN_TYPE_DURATION -> RuntimeTypeKind.DURATION;
      case BUILTIN_TYPE_YEARS_AND_MONTHS_DURATION -> RuntimeTypeKind.YEARS_MONTHS_DURATION;
      case BUILTIN_TYPE_DAYS_AND_TIME_DURATION -> RuntimeTypeKind.DAYS_TIME_DURATION;
      case BUILTIN_TYPE_RANGE -> RuntimeTypeKind.RANGE;
      case BUILTIN_TYPE_ANY, BUILTIN_TYPE_UNSPECIFIED, UNRECOGNIZED -> RuntimeTypeKind.ANY;
    };
  }

  private static Map<String, LocalSlotAddress> capturedSlots(
      Map<String, LocalSlotAddress> localSlots) {
    Map<String, LocalSlotAddress> captured = new HashMap<>();
    localSlots.forEach((path, address) -> captured.put(path,
        new LocalSlotAddress(address.lexicalDepth() + 1, address.localSlot())));
    return captured;
  }

  private record LocalSlotAddress(int lexicalDepth, int localSlot) { }
}
