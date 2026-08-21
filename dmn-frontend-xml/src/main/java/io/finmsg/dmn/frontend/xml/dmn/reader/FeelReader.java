package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.Feel;
import io.finmsg.dmn.model.FeelText;

public final class FeelReader {

	public Feel read(XmlCursor cursor) {
		return Feel.newBuilder().setText(readText(cursor)).build();
	}

	public FeelText readText(XmlCursor cursor) {

		String tag = cursor.documentLocalName();
		if ("some".equals(tag)) {
			String iterVar = cursor.attribute("iteratorVariable").orElse("item");
			String inText = "";
			String satisfiesText = "";
			if (cursor.firstChild()) {
				do {
					switch (cursor.documentLocalName()) {
						case "in" -> inText = readText(cursor).getText();
						case "satisfies" -> satisfiesText = readText(cursor).getText();
					}
				} while (cursor.nextSibling());
				cursor.parent();
			}
			return FeelText.newBuilder().setText("some " + iterVar + " in " + inText + " satisfies " + satisfiesText)
					.build();
		}
		if ("every".equals(tag)) {
			String iterVar = cursor.attribute("iteratorVariable").orElse("item");
			String inText = "";
			String satisfiesText = "";
			if (cursor.firstChild()) {
				do {
					switch (cursor.documentLocalName()) {
						case "in" -> inText = readText(cursor).getText();
						case "satisfies" -> satisfiesText = readText(cursor).getText();
					}
				} while (cursor.nextSibling());
				cursor.parent();
			}
			return FeelText.newBuilder().setText("every " + iterVar + " in " + inText + " satisfies " + satisfiesText)
					.build();
		}
		if ("conditional".equals(tag)) {
			String ifText = "";
			String thenText = "";
			String elseText = "";
			if (cursor.firstChild()) {
				do {
					switch (cursor.documentLocalName()) {
						case "if" -> ifText = readText(cursor).getText();
						case "then" -> thenText = readText(cursor).getText();
						case "else" -> elseText = readText(cursor).getText();
					}
				} while (cursor.nextSibling());
				cursor.parent();
			}
			return FeelText.newBuilder().setText("if " + ifText + " then " + thenText + " else " + elseText).build();
		}
		if ("for".equals(tag)) {
			String iterVar = cursor.attribute("iteratorVariable").orElse("item");
			String inText = "";
			String returnText = "";
			if (cursor.firstChild()) {
				do {
					switch (cursor.documentLocalName()) {
						case "in" -> inText = readText(cursor).getText();
						case "return" -> returnText = readText(cursor).getText();
					}
				} while (cursor.nextSibling());
				cursor.parent();
			}
			return FeelText.newBuilder().setText("for " + iterVar + " in " + inText + " return " + returnText).build();
		}
		if ("decisionTable".equals(tag)) {
			String hitPolicy = cursor.attribute("hitPolicy").orElse("UNIQUE");
			java.util.List<String> inputExprs = new java.util.ArrayList<>();
			java.util.List<java.util.List<String>> ruleInputs = new java.util.ArrayList<>();
			java.util.List<java.util.List<String>> ruleOutputs = new java.util.ArrayList<>();

			if (cursor.firstChild()) {
				do {
					switch (cursor.documentLocalName()) {
						case "input" -> {
							String inExpr = "";
							if (cursor.firstChild()) {
								do {
									if ("inputExpression".equals(cursor.documentLocalName())) {
										inExpr = readText(cursor).getText();
									}
								} while (cursor.nextSibling());
								cursor.parent();
							}
							inputExprs.add(inExpr);
						}
						case "output" -> {
						}
						case "rule" -> {
							java.util.List<String> inEntries = new java.util.ArrayList<>();
							java.util.List<String> outEntries = new java.util.ArrayList<>();
							if (cursor.firstChild()) {
								do {
									if ("inputEntry".equals(cursor.documentLocalName())) {
										inEntries.add(readText(cursor).getText());
									} else if ("outputEntry".equals(cursor.documentLocalName())) {
										outEntries.add(readText(cursor).getText());
									}
								} while (cursor.nextSibling());
								cursor.parent();
							}
							ruleInputs.add(inEntries);
							ruleOutputs.add(outEntries);
						}
					}
				} while (cursor.nextSibling());
				cursor.parent();
			}

			StringBuilder sb = new StringBuilder();
			boolean isCollect = "COLLECT".equalsIgnoreCase(hitPolicy) || "RULE ORDER".equalsIgnoreCase(hitPolicy);
			String agg = cursor.attribute("aggregation").orElse("");
			String prefix = "";
			String suffix = "";
			if (isCollect) {
				if ("SUM".equalsIgnoreCase(agg) || "+".equals(agg)) {
					prefix = "sum(flatten([";
					suffix = "]))";
				} else if ("COUNT".equalsIgnoreCase(agg) || "#".equals(agg)) {
					prefix = "count(flatten([";
					suffix = "]))";
				} else if ("MIN".equalsIgnoreCase(agg) || "<".equals(agg)) {
					prefix = "min(flatten([";
					suffix = "]))";
				} else if ("MAX".equalsIgnoreCase(agg) || ">".equals(agg)) {
					prefix = "max(flatten([";
					suffix = "]))";
				} else {
					prefix = "flatten([";
					suffix = "])";
				}
				sb.append(prefix);
			}
			for (int r = 0; r < ruleInputs.size(); r++) {
				java.util.List<String> inEntries = ruleInputs.get(r);
				java.util.List<String> outEntries = ruleOutputs.get(r);
				java.util.List<String> conds = new java.util.ArrayList<>();
				for (int c = 0; c < inEntries.size() && c < inputExprs.size(); c++) {
					String entry = inEntries.get(c).trim();
					String inExpr = inputExprs.get(c).trim();
					if (entry.isEmpty() || "-".equals(entry)) {
						continue;
					}
					conds.add("(" + inExpr + " in (" + entry + "))");
				}
				String ruleCond = conds.isEmpty() ? "true" : String.join(" and ", conds);
				String outExpr = outEntries.isEmpty() ? "null" : (outEntries.size() == 1 ? outEntries.get(0) : "{" + String.join(", ", outEntries) + "}");
				if (isCollect) {
					if (r > 0) sb.append(", ");
					sb.append("(if ").append(ruleCond).append(" then [").append(outExpr).append("] else [])");
				} else {
					sb.append("if ").append(ruleCond).append(" then ").append(outExpr).append(" else ");
				}
			}
			if (isCollect) {
				sb.append(suffix);
			} else {
				sb.append("null");
			}
			return FeelText.newBuilder().setText(sb.toString()).build();
		}
		if ("filter".equals(tag)) {
			String inText = "";
			String matchText = "";
			if (cursor.firstChild()) {
				do {
					switch (cursor.documentLocalName()) {
						case "in" -> inText = readText(cursor).getText();
						case "match" -> matchText = readText(cursor).getText();
					}
				} while (cursor.nextSibling());
				cursor.parent();
			}
			return FeelText.newBuilder().setText("(" + inText + ")[" + matchText + "]").build();
		}
		if ("context".equals(tag)) {
			java.util.List<String> entries = new java.util.ArrayList<>();
			String resultExpr = null;
			if (cursor.firstChild()) {
				do {
					if ("contextEntry".equals(cursor.documentLocalName())) {
						String key = null;
						String expr = null;
						if (cursor.firstChild()) {
							do {
								if ("variable".equals(cursor.documentLocalName())) {
									key = cursor.attribute("name").orElse(null);
								} else if ("literalExpression".equals(cursor.documentLocalName())
										|| "decisionTable".equals(cursor.documentLocalName())
										|| "expression".equals(cursor.documentLocalName())
										|| "context".equals(cursor.documentLocalName())
										|| "relation".equals(cursor.documentLocalName())
										|| "list".equals(cursor.documentLocalName())
										|| "invocation".equals(cursor.documentLocalName())
										|| "some".equals(cursor.documentLocalName())
										|| "every".equals(cursor.documentLocalName())
										|| "filter".equals(cursor.documentLocalName())) {
									expr = readText(cursor).getText();
								}
							} while (cursor.nextSibling());
							cursor.parent();
						}
						if (key != null && expr != null) {
							String escapedKey = key.contains(" ") || key.contains("-") || key.contains(".")
									? "\"" + key.replace("\"", "\\\"") + "\""
									: key;
							entries.add(escapedKey + ": " + expr);
						} else if (expr != null) {
							resultExpr = expr;
						}
					}
				} while (cursor.nextSibling());
				cursor.parent();
			}
			if (resultExpr != null) {
				if (entries.isEmpty()) {
					return FeelText.newBuilder().setText(resultExpr).build();
				}
				return FeelText.newBuilder()
						.setText("({" + String.join(", ", entries) + ", __result__: " + resultExpr + "}.__result__)")
						.build();
			}
			return FeelText.newBuilder().setText("{" + String.join(", ", entries) + "}").build();
		}
		if ("invocation".equals(tag)) {
			String fnName = "";
			java.util.List<String> args = new java.util.ArrayList<>();
			if (cursor.firstChild()) {
				do {
					if ("literalExpression".equals(cursor.documentLocalName())
							|| "expression".equals(cursor.documentLocalName())) {
						fnName = readText(cursor).getText();
					} else if ("binding".equals(cursor.documentLocalName())) {
						String paramName = null;
						String expr = null;
						if (cursor.firstChild()) {
							do {
								if ("parameter".equals(cursor.documentLocalName())) {
									paramName = cursor.attribute("name").orElse(null);
								} else {
									expr = readText(cursor).getText();
								}
							} while (cursor.nextSibling());
							cursor.parent();
						}
						if (paramName != null && expr != null) {
							args.add(paramName + ": " + expr);
						} else if (expr != null) {
							args.add(expr);
						}
					}
				} while (cursor.nextSibling());
				cursor.parent();
			}
			return FeelText.newBuilder().setText(fnName + "(" + String.join(", ", args) + ")").build();
		}
		if ("list".equals(tag)) {
			java.util.List<String> elements = new java.util.ArrayList<>();
			if (cursor.firstChild()) {
				do {
					if ("literalExpression".equals(cursor.documentLocalName())
							|| "expression".equals(cursor.documentLocalName())
							|| "context".equals(cursor.documentLocalName())
							|| "relation".equals(cursor.documentLocalName())
							|| "list".equals(cursor.documentLocalName())
							|| "invocation".equals(cursor.documentLocalName())
							|| "some".equals(cursor.documentLocalName())
							|| "every".equals(cursor.documentLocalName())
							|| "filter".equals(cursor.documentLocalName())) {
						elements.add(readText(cursor).getText());
					}
				} while (cursor.nextSibling());
				cursor.parent();
			}
			return FeelText.newBuilder().setText("[" + String.join(", ", elements) + "]").build();
		}

		FeelText.Builder builder = FeelText.newBuilder();

		if (cursor.firstChild()) {
			do {
				switch (cursor.documentLocalName()) {
					case "text" -> {
						if (cursor.hasText()) {
							builder.setText(cursor.text().trim());
						}
					}
					case "in", "satisfies", "match", "expression", "literalExpression", "context", "functionDefinition",
							"some", "every", "filter", "extensionElements", "documentation" -> {
						FeelText inner = readText(cursor);
						if (!inner.getText().isBlank() && builder.getText().isBlank()) {
							builder.setText(inner.getText());
						}
					}
					default -> UnsupportedContent.rejectDmnChild(cursor, "FEEL expression");
				}
			} while (cursor.nextSibling());
			cursor.parent();
		}

		return builder.build();
	}
}
