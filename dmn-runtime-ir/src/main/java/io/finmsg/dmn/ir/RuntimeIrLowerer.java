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
    Map<String, DrgElement> elementBySourceId = new HashMap<>();
    int nextId = 0;
    for (DrgElement element : model.getDrgElementsList()) {
      Node node = executableNode(element);
      if (node == null) {
        continue;
      }
      if (!node.getId().isBlank()) {
        runtimeIdBySourceId.put(node.getId(), nextId);
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
              decisionDependencies(decision, runtimeIdBySourceId)));
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
