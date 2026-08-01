package io.finmsg.dmn.semantic.analysis;

import io.finmsg.dmn.model.ItemDefinition;
import io.finmsg.dmn.model.TypeReference;
import java.util.Map;

/** Name and item-definition types visible while analyzing one FEEL expression. */
public record FeelTypeEnvironment(
    Map<String, TypeReference> symbols,
    Map<String, ItemDefinition> itemDefinitions,
    Map<String, java.util.List<TypeReference>> decisionTables) {

  public FeelTypeEnvironment {
    symbols = Map.copyOf(symbols);
    itemDefinitions = Map.copyOf(itemDefinitions);
    decisionTables = decisionTables.entrySet().stream().collect(
        java.util.stream.Collectors.toUnmodifiableMap(
            Map.Entry::getKey, entry -> java.util.List.copyOf(entry.getValue())));
  }

  public FeelTypeEnvironment(
      Map<String, TypeReference> symbols,
      Map<String, ItemDefinition> itemDefinitions) {
    this(symbols, itemDefinitions, Map.of());
  }

  public static FeelTypeEnvironment empty() {
    return new FeelTypeEnvironment(Map.of(), Map.of(), Map.of());
  }
}
