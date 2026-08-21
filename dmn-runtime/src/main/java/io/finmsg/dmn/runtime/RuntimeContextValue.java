package io.finmsg.dmn.runtime;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable FEEL context supporting both stable indexed and named access as a
 * Map.
 */
public record RuntimeContextValue(List<Object> fields, Map<String, Object> namedFields) implements Map<String, Object> {

	public RuntimeContextValue {
		fields = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(fields));
		namedFields = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(namedFields));
	}

	public Object field(int index) {
		return index >= 0 && index < fields.size() ? fields.get(index) : null;
	}
	public Object field(String name) {
		return namedFields.get(Objects.requireNonNull(name));
	}

	@Override
	public int size() {
		return namedFields.size();
	}
	@Override
	public boolean isEmpty() {
		return namedFields.isEmpty();
	}
	@Override
	public boolean containsKey(Object key) {
		return namedFields.containsKey(key);
	}
	@Override
	public boolean containsValue(Object value) {
		return namedFields.containsValue(value);
	}
	@Override
	public Object get(Object key) {
		return namedFields.get(key);
	}
	@Override
	public Object put(String key, Object value) {
		throw new UnsupportedOperationException();
	}
	@Override
	public Object remove(Object key) {
		throw new UnsupportedOperationException();
	}
	@Override
	public void putAll(Map<? extends String, ?> m) {
		throw new UnsupportedOperationException();
	}
	@Override
	public void clear() {
		throw new UnsupportedOperationException();
	}
	@Override
	public Set<String> keySet() {
		return namedFields.keySet();
	}
	@Override
	public Collection<Object> values() {
		return namedFields.values();
	}
	@Override
	public Set<Entry<String, Object>> entrySet() {
		return namedFields.entrySet();
	}

	/**
	 * Returns only the named fields, matching the established toString contract.
	 */
	@Override
	public String toString() {
		return namedFields.toString();
	}
}
