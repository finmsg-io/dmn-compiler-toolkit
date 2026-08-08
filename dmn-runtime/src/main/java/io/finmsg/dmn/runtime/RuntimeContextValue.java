package io.finmsg.dmn.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable FEEL context supporting both stable indexed and named access. */
public record RuntimeContextValue(List<Object> fields, Map<String, Object> namedFields) {

  public RuntimeContextValue {
    fields = List.copyOf(fields);
    namedFields = Map.copyOf(new LinkedHashMap<>(namedFields));
  }

  public Object field(int index) { return fields.get(index); }
  public Object field(String name) { return namedFields.get(Objects.requireNonNull(name)); }

  /** Returns only the named fields, matching the established toString contract. */
  @Override public String toString() { return namedFields.toString(); }
}
