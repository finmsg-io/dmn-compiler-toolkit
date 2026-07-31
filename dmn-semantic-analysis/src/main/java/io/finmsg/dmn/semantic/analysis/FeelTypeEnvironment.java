package io.finmsg.dmn.semantic.analysis;

import io.finmsg.dmn.model.ItemDefinition;
import io.finmsg.dmn.model.TypeReference;
import java.util.Map;

/** Name and item-definition types visible while analyzing one FEEL expression. */
public record FeelTypeEnvironment(
    Map<String, TypeReference> symbols,
    Map<String, ItemDefinition> itemDefinitions) {

  public FeelTypeEnvironment {
    symbols = Map.copyOf(symbols);
    itemDefinitions = Map.copyOf(itemDefinitions);
  }

  public static FeelTypeEnvironment empty() {
    return new FeelTypeEnvironment(Map.of(), Map.of());
  }
}
