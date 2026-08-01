package io.finmsg.dmn.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable FEEL context supporting both stable indexed and named access. */
public final class RuntimeContextValue {
  private final List<Object> fields;
  private final Map<String, Object> namedFields;

  public RuntimeContextValue(List<Object> fields, Map<String, Object> namedFields) {
    this.fields = List.copyOf(fields);
    this.namedFields = Map.copyOf(new LinkedHashMap<>(namedFields));
  }

  public List<Object> fields() { return fields; }
  public Map<String, Object> namedFields() { return namedFields; }
  public Object field(int index) { return fields.get(index); }
  public Object field(String name) { return namedFields.get(Objects.requireNonNull(name)); }

  @Override public boolean equals(Object other) {
    return other instanceof RuntimeContextValue context && fields.equals(context.fields)
        && namedFields.equals(context.namedFields);
  }
  @Override public int hashCode() { return Objects.hash(fields, namedFields); }
  @Override public String toString() { return namedFields.toString(); }
}
