package io.finmsg.dmn.compiler.dmnir;

import com.google.protobuf.Message;
import io.finmsg.dmn.dmnir.proto.v0.DmnIrDate;
import io.finmsg.dmn.model.*;
import java.time.LocalDate;
import java.util.*;

/** Consumer-local date conversion; portable values never carry a timezone. */
final class DmnIrDates {
	private DmnIrDates() {
	}
	static LocalDate value(DmnIrDate date) {
		return LocalDate.of(date.getYear(), date.getMonth(), date.getDay());
	}
	static DmnIrDate wire(LocalDate date) {
		if (date.getYear() == 0)
			throw new IllegalArgumentException("Year zero is outside the DMN date profile.");
		return DmnIrDate.newBuilder().setYear(date.getYear()).setMonth(date.getMonthValue())
				.setDay(date.getDayOfMonth()).build();
	}
	static String feel(DmnIrDate date) {
		int year = date.getYear();
		return (year < 0 ? "-" : "")
				+ String.format(Locale.ROOT, "%04d-%02d-%02d", Math.abs(year), date.getMonth(), date.getDay());
	}
	static LocalDate fromFeel(String text) {
		if (text == null || !text.matches("-?(?:[0-9]{4}|[1-9][0-9]{4,8})-[0-9]{2}-[0-9]{2}"))
			return null;
		try {
			int endYear = text.indexOf('-', text.startsWith("-") ? 1 : 0);
			int year = Integer.parseInt(text.substring(0, endYear));
			return year == 0
					? null
					: LocalDate.of(year, Integer.parseInt(text.substring(endYear + 1, endYear + 3)),
							Integer.parseInt(text.substring(endYear + 4)));
		} catch (java.time.DateTimeException | NumberFormatException invalid) {
			return null;
		}
	}
	static boolean containsDate(Message source) {
		var pending = new ArrayDeque<Message>();
		pending.add(source);
		while (!pending.isEmpty()) {
			var node = pending.remove();
			if (node instanceof TypeReference type && type.getBuiltin() == BuiltinType.BUILTIN_TYPE_DATE
					|| node instanceof LiteralExpression literal && literal.getKind() == LiteralKind.LITERAL_KIND_DATE)
				return true;
			for (var field : node.getAllFields().values()) {
				if (field instanceof Message child)
					pending.add(child);
				else if (field instanceof List<?> children)
					for (var child : children)
						if (child instanceof Message message)
							pending.add(message);
			}
		}
		return false;
	}
}
