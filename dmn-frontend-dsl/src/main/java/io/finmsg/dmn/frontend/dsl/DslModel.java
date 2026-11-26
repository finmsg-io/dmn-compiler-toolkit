package io.finmsg.dmn.frontend.dsl;

import java.util.List;
import java.util.Optional;

/**
 * Immutable AST representation of a {@code .decision} DSL document.
 */
public record DslModel(String name, String namespace, Optional<String> id, List<DslImport> imports, List<DslType> types,
		List<DslInput> inputs, List<DslBkm> bkms, List<DslDecision> decisions, List<DslTest> tests) {

	public DslModel {
		imports = List.copyOf(imports);
		types = List.copyOf(types);
		inputs = List.copyOf(inputs);
		bkms = List.copyOf(bkms);
		decisions = List.copyOf(decisions);
		tests = List.copyOf(tests);
	}

	public record DslImport(String alias, String locationUri, String namespace, int line) {
	}

	public enum TypeKind {
		RECORD, COLLECTION, ALIAS
	}

	public record DslType(String name, Optional<String> id, TypeKind kind, Optional<String> elementOrBaseType,
			Optional<String> constraint, List<DslRecordField> fields, int line) {
		public DslType {
			fields = List.copyOf(fields);
		}
	}

	public record DslRecordField(String name, String typeRef, Optional<String> constraint, int line) {
	}

	public record DslInput(String name, Optional<String> id, String typeRef, int line) {
	}

	public record DslParam(String name, String typeRef) {
	}

	public record DslBkm(String name, Optional<String> id, List<DslParam> parameters, String returnType,
			List<DslRef> uses, String bodyFeel, int line) {
		public DslBkm {
			parameters = List.copyOf(parameters);
			uses = List.copyOf(uses);
		}
	}

	public record DslRef(Optional<String> importAlias, String name, Optional<String> explicitElementId) {
	}

	public record DslDecision(String name, Optional<String> id, String typeRef, List<DslRef> requires,
			List<DslRef> uses, Optional<String> literalFeel, Optional<DslTable> table, int line) {
		public DslDecision {
			requires = List.copyOf(requires);
			uses = List.copyOf(uses);
		}
	}

	public record DslTable(String hitPolicy, List<DslTableInput> inputs, List<DslTableOutput> outputs,
			List<DslTableRule> rules, int line) {
		public DslTable {
			inputs = List.copyOf(inputs);
			outputs = List.copyOf(outputs);
			rules = List.copyOf(rules);
		}
	}

	public record DslTableInput(String expressionFeel, Optional<String> typeRef, Optional<String> label) {
	}

	public record DslTableOutput(Optional<String> name, Optional<String> typeRef, Optional<String> defaultFeel) {
	}

	public record DslTableRule(List<String> inputEntries, List<String> outputEntries, int line) {
		public DslTableRule {
			inputEntries = List.copyOf(inputEntries);
			outputEntries = List.copyOf(outputEntries);
		}
	}

	public record DslTestEntry(String name, String feelExpression, int line) {
	}

	public record DslTest(String name, List<DslTestEntry> givens, List<DslTestEntry> expects, int line) {
		public DslTest {
			givens = List.copyOf(givens);
			expects = List.copyOf(expects);
		}
	}
}
