package io.finmsg.dmn.ir;

import java.util.List;
import java.util.Objects;

public record RuntimeRelationExpression(List<RuntimeRelationColumn> columns, List<List<RuntimeExpression>> rows,
		RuntimeType type) implements RuntimeExpression {
	public RuntimeRelationExpression {
		columns = List.copyOf(columns);
		rows = rows.stream().map(List::copyOf).toList();
		for (List<RuntimeExpression> row : rows) {
			if (row.size() != columns.size()) {
				throw new IllegalArgumentException("Every relation row must match the column count.");
			}
		}
		Objects.requireNonNull(type, "type");
	}
}
