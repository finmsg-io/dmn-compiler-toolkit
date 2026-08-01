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
          decisions.add(new RuntimeDecision(runtimeId, runtimeId,
              lowerType(decision.getVariable().getType(), itemTypes, new HashSet<>()),
              decisionDependencies(decision, runtimeIdBySourceId),
              lowerDecisionExpression(
                  decision, analysis.bindings(), valueSlotBySourceId, itemTypes)));
          runtimeId++;
        }
        case BUSINESS_KNOWLEDGE_MODEL -> {
          BusinessKnowledgeModel bkm = element.getBusinessKnowledgeModel();
          bkms.add(new RuntimeBkm(runtimeId, runtimeId,
              lowerType(bkm.getVariable().getType(), itemTypes, new HashSet<>()),
              bkmDependencies(bkm, runtimeIdBySourceId)));
          runtimeId++;
        }
        default -> { }
      }
    }

    List<Integer> order = new ArrayList<>();
    for (DrgElement element : analysis.compilationOrder()) {
      Node node = executableNode(element);
      Integer id = node == null ? null : runtimeIdBySourceId.get(node.getId());
      if (id == null || elementBySourceId.get(node.getId()) == null) {
        throw new RuntimeIrLoweringException(
            "Model-set compilation order requires model-set Runtime IR lowering.");
      }
      order.add(id);
    }
    return new RuntimeModel(inputs, decisions, bkms, order, runtimeId);
  }

  private static Optional<RuntimeExpression> lowerDecisionExpression(
      Decision decision,
      List<io.finmsg.dmn.semantic.analysis.DmnSymbolBinding> bindings,
      Map<String, Integer> slots,
      Map<String, ItemDefinition> itemTypes) {
    if (!decision.hasLogic()) {
      return Optional.empty();
    }
    if (!decision.getLogic().hasLiteralExpression()
        || !decision.getLogic().getLiteralExpression().hasParsed()) {
      throw new RuntimeIrLoweringException(
          "Decision '" + decision.getNode().getName()
              + "' requires supported parsed literal logic for expression lowering.");
    }
    Expression expression = decision.getLogic().getLiteralExpression().getParsed().getAst();
    String path = "definitions/decision[" + decision.getNode().getName()
        + "]/logic/literalExpression";
    return Optional.of(lowerExpression(
        expression, path, bindings, slots, itemTypes, new HashMap<>(), new int[] {0}));
  }

  private static RuntimeExpression lowerExpression(
      Expression expression,
      String path,
      List<io.finmsg.dmn.semantic.analysis.DmnSymbolBinding> bindings,
      Map<String, Integer> slots,
      Map<String, ItemDefinition> itemTypes,
      Map<String, Integer> localSlots,
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
          Integer localSlot = localSlots.get(binding.declarationPath());
          if (localSlot == null) {
            throw new RuntimeIrLoweringException(
                "Local expression binding is not in scope at " + path + ".");
          }
          yield new RuntimeLocalReference(localSlot, type);
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
        Map<String, Integer> contextSlots = new HashMap<>(localSlots);
        for (int index = 0; index < expression.getContext().getEntriesCount(); index++) {
          ContextEntry entry = expression.getContext().getEntries(index);
          String entryPath = path + "/entry[" + index + "]";
          RuntimeExpression value = lowerExpression(
              entry.getExpression(), entryPath, bindings, slots, itemTypes,
              contextSlots, nextLocalSlot);
          int localSlot = nextLocalSlot[0]++;
          contextSlots.put(entryPath, localSlot);
          entries.add(new RuntimeContextEntry(entry.getName(), localSlot, value));
        }
        yield new RuntimeContextExpression(entries, type);
      }
      case PATH -> new RuntimePathExpression(
          lowerExpression(expression.getPath().getSource(), path + "/source", bindings, slots,
              itemTypes, localSlots, nextLocalSlot),
          expression.getPath().getMember(), type);
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
      case DESCENDANT -> new RuntimeDescendantExpression(
          lowerExpression(expression.getDescendant().getSource(), path + "/source", bindings, slots,
              itemTypes, localSlots, nextLocalSlot),
          expression.getDescendant().getMember(), type);
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
      Map<String, Integer> localSlots,
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
      Map<String, Integer> localSlots,
      int[] nextLocalSlot) {
    List<RuntimeIteration> iterations = new ArrayList<>();
    Map<String, Integer> iterationSlots = new HashMap<>(localSlots);
    if (value.getIterationsCount() == 0) {
      if (value.getVariable().isBlank()) {
        throw new RuntimeIrLoweringException("For expression has no iteration at " + path + ".");
      }
      RuntimeExpression source = lowerExpression(value.getIn(), path + "/in", bindings, slots,
          itemTypes, iterationSlots, nextLocalSlot);
      int localSlot = nextLocalSlot[0]++;
      iterationSlots.put(path, localSlot);
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
        iterationSlots.put(iterationPath, localSlot);
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
      Map<String, Integer> localSlots,
      int[] nextLocalSlot) {
    List<RuntimeQuantifiedBinding> loweredBindings = new ArrayList<>();
    Map<String, Integer> quantifiedSlots = new HashMap<>(localSlots);
    if (value.getBindingsCount() == 0) {
      if (value.getVariable().isBlank()) {
        throw new RuntimeIrLoweringException(
            "Quantified expression has no binding at " + path + ".");
      }
      RuntimeExpression source = lowerExpression(value.getIn(), path + "/in", bindings, slots,
          itemTypes, quantifiedSlots, nextLocalSlot);
      int localSlot = nextLocalSlot[0]++;
      quantifiedSlots.put(path, localSlot);
      loweredBindings.add(new RuntimeQuantifiedBinding(localSlot, source));
    } else {
      for (int index = 0; index < value.getBindingsCount(); index++) {
        IterationBinding binding = value.getBindings(index);
        String bindingPath = path + "/binding[" + index + "]";
        RuntimeExpression source = lowerExpression(binding.getIn(), bindingPath + "/in", bindings,
            slots, itemTypes, quantifiedSlots, nextLocalSlot);
        int localSlot = nextLocalSlot[0]++;
        quantifiedSlots.put(bindingPath, localSlot);
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
      Map<String, Integer> localSlots,
      int[] nextLocalSlot) {
    List<RuntimeFunctionParameter> parameters = new ArrayList<>();
    Map<String, Integer> parameterSlots = new HashMap<>(localSlots);
    for (int index = 0; index < value.getParametersCount(); index++) {
      FormalParameter parameter = value.getParameters(index);
      String parameterPath = path + "/parameter[" + index + "]";
      int localSlot = nextLocalSlot[0]++;
      parameterSlots.put(parameterPath, localSlot);
      parameters.add(new RuntimeFunctionParameter(
          parameter.getName(), localSlot, lowerFeelType(parameter.getType(), itemTypes)));
    }
    Optional<RuntimeExpression> body = value.getBody().getNodeCase() == Expression.NodeCase.NODE_NOT_SET
        ? Optional.empty()
        : Optional.of(lowerExpression(value.getBody(), path + "/body", bindings, slots,
            itemTypes, parameterSlots, nextLocalSlot));
    return new RuntimeFunctionDefinition(parameters, body, value.getExternal(), type);
  }

  private static RuntimeQuantifier quantifier(Quantifier quantifier) {
    return switch (quantifier) {
      case QUANTIFIER_SOME -> RuntimeQuantifier.SOME;
      case QUANTIFIER_EVERY -> RuntimeQuantifier.EVERY;
      case QUANTIFIER_UNSPECIFIED, UNRECOGNIZED -> throw new RuntimeIrLoweringException(
          "Unsupported FEEL quantifier " + quantifier + ".");
    };
  }

  private static RuntimeRangeExpression lowerRange(
      RangeExpression range,
      RuntimeType type,
      String path,
      List<io.finmsg.dmn.semantic.analysis.DmnSymbolBinding> bindings,
      Map<String, Integer> slots,
      Map<String, ItemDefinition> itemTypes,
      Map<String, Integer> localSlots,
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
      Map<String, Integer> localSlots,
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
      case CONTEXT -> RuntimeType.context(type.getContext().getEntriesList().stream()
          .map(entry -> lowerType(entry.getType(), items, new HashSet<>(resolving))).toList());
      case FUNCTION -> RuntimeType.function(type.getFunction().getParameterTypeList().stream()
              .map(parameter -> lowerType(parameter, items, new HashSet<>(resolving))).toList(),
          lowerType(type.getFunction().getReturnType(), items, new HashSet<>(resolving)));
      case NAMED -> lowerNamed(type.getNamed(), items, resolving);
      case KIND_NOT_SET -> RuntimeType.scalar(RuntimeTypeKind.ANY);
    };
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
      case CONTEXT -> RuntimeType.context(type.getContext().getEntriesList().stream()
          .map(entry -> lowerFeelType(entry.getType(), items)).toList());
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
      result = RuntimeType.context(item.getComponentsList().stream()
          .map(component -> {
            RuntimeType field = lowerType(component.getType(), items, new HashSet<>(resolving));
            return component.getIsCollection()
                ? RuntimeType.element(RuntimeTypeKind.LIST, field) : field;
          }).toList());
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
}
