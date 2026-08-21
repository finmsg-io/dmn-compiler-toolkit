package io.finmsg.dmn.ir;

import io.finmsg.dmn.model.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Recursive lowering for parsed boxed DMN expressions. */
final class RuntimeBoxedExpressionLowerer {
	private RuntimeBoxedExpressionLowerer() {
	}

	static RuntimeExpression lowerParsedExpression(ExpressionParsed parsed, String path,
			List<io.finmsg.dmn.semantic.analysis.DmnSymbolBinding> bindings, Map<String, Integer> slots,
			Map<String, ItemDefinition> itemTypes, Map<String, LocalSlotAddress> localSlots, int[] nextLocalSlot) {
		return switch (parsed.getTypeCase()) {
			case FEEL -> RuntimeExpressionLowerer.lowerExpression(parsed.getFeel().getAst(), path, bindings, slots,
					itemTypes, localSlots, nextLocalSlot);
			case BOXED -> lowerBoxedExpression(parsed.getBoxed(), path, bindings, slots, itemTypes, localSlots,
					nextLocalSlot, null);
			case TYPE_NOT_SET -> throw new RuntimeIrLoweringException("Empty parsed boxed expression at " + path + ".");
		};
	}

	static RuntimeExpression lowerBoxedExpression(BoxedExpressionParsed boxed, String path,
			List<io.finmsg.dmn.semantic.analysis.DmnSymbolBinding> bindings, Map<String, Integer> slots,
			Map<String, ItemDefinition> itemTypes, Map<String, LocalSlotAddress> localSlots, int[] nextLocalSlot,
			RuntimeType expectedType) {
		return switch (boxed.getTypeCase()) {
			case CONTEXT -> {
				List<RuntimeContextEntry> entries = new ArrayList<>();
				Map<String, LocalSlotAddress> contextSlots = RuntimeLexicalFrame.childScope(localSlots);
				List<RuntimeField> fields = new ArrayList<>();
				for (int index = 0; index < boxed.getContext().getEntriesCount(); index++) {
					ContextEntryParsed entry = boxed.getContext().getEntries(index);
					String entryPath = path + "/context/entry[" + index + "]";
					if (!entry.hasExpression()) {
						throw new RuntimeIrLoweringException("Incomplete boxed context entry at " + entryPath);
					}
					RuntimeExpression expression = lowerParsedExpression(entry.getExpression(),
							entryPath + "/expression", bindings, slots, itemTypes, contextSlots, nextLocalSlot);
					if (!entry.hasVariable() || entry.getVariable().getNode().getName().isBlank()) {
						entries.add(new RuntimeContextEntry("", -1, expression));
					} else {
						String name = entry.getVariable().getNode().getName();
						int localSlot = nextLocalSlot[0]++;
						contextSlots.put(path + "/context/entry[" + index + "]", new LocalSlotAddress(0, localSlot));
						entries.add(new RuntimeContextEntry(name, localSlot, expression));
						fields.add(new RuntimeField(fields.size(), name, expression.type()));
					}
				}
				yield new RuntimeContextExpression(entries,
						expectedType == null ? RuntimeType.contextFields(fields) : expectedType);
			}
			case LIST -> {
				List<RuntimeExpression> elements = new ArrayList<>();
				for (int index = 0; index < boxed.getList().getElementsCount(); index++) {
					elements.add(lowerParsedExpression(boxed.getList().getElements(index),
							path + "/list/element[" + index + "]", bindings, slots, itemTypes, localSlots,
							nextLocalSlot));
				}
				RuntimeType type = expectedType == null
						? RuntimeType.element(RuntimeTypeKind.LIST, RuntimeType.scalar(RuntimeTypeKind.ANY))
						: expectedType;
				yield new RuntimeListExpression(elements, type);
			}
			case RELATION -> {
				List<RuntimeRelationColumn> columns = boxed.getRelation().getColumnsList().stream()
						.map(column -> new RuntimeRelationColumn(column.getVariable().getNode().getName(),
								RuntimeTypeLowerer.lower(column.getVariable().getType(), itemTypes)))
						.toList();
				List<List<RuntimeExpression>> rows = new ArrayList<>();
				for (int rowIndex = 0; rowIndex < boxed.getRelation().getRowsCount(); rowIndex++) {
					RelationRowParsed row = boxed.getRelation().getRows(rowIndex);
					List<RuntimeExpression> cells = new ArrayList<>();
					for (int columnIndex = 0; columnIndex < row.getExpressionsCount(); columnIndex++) {
						cells.add(lowerParsedExpression(row.getExpressions(columnIndex),
								path + "/relation/row[" + rowIndex + "]/cell[" + columnIndex + "]", bindings, slots,
								itemTypes, localSlots, nextLocalSlot));
					}
					rows.add(cells);
				}
				RuntimeType relationType = expectedType == null
						? RuntimeType.element(RuntimeTypeKind.LIST,
								RuntimeType.contextFields(RuntimeTypeLowerer.relationFields(columns)))
						: expectedType;
				yield new RuntimeRelationExpression(columns, rows, relationType);
			}
			case FUNCTION_DEFINITION -> {
				FunctionDefinitionParsed function = boxed.getFunctionDefinition();
				List<RuntimeFunctionParameter> parameters = new ArrayList<>();
				Map<String, LocalSlotAddress> parameterSlots = RuntimeLexicalFrame.capturedScope(localSlots);
				for (int index = 0; index < function.getParametersCount(); index++) {
					InformationItem parameter = function.getParameters(index);
					int localSlot = nextLocalSlot[0]++;
					parameterSlots.put(path + "/parameter[" + index + "]", new LocalSlotAddress(0, localSlot));
					parameters.add(new RuntimeFunctionParameter(parameter.getNode().getName(), localSlot,
							RuntimeTypeLowerer.lower(parameter.getType(), itemTypes)));
				}
				if (!function.hasBody()) {
					throw new RuntimeIrLoweringException("Boxed function requires a body at " + path + ".");
				}
				RuntimeExpression body = lowerParsedExpression(function.getBody(), path + "/body", bindings, slots,
						itemTypes, parameterSlots, nextLocalSlot);
				RuntimeType functionType = expectedType == null
						? RuntimeType.function(parameters.stream().map(RuntimeFunctionParameter::type).toList(),
								body.type())
						: expectedType;
				yield new RuntimeFunctionDefinition(parameters, Optional.of(body), false, nextLocalSlot[0],
						functionType);
			}
			case TYPE_NOT_SET -> throw new RuntimeIrLoweringException("Empty boxed expression at " + path + ".");
		};
	}
}
