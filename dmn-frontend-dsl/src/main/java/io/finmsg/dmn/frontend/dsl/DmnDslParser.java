package io.finmsg.dmn.frontend.dsl;

import io.finmsg.dmn.frontend.dsl.DslModel.*;
import io.finmsg.dmn.frontend.dsl.grammar.DecisionDslBaseVisitor;
import io.finmsg.dmn.frontend.dsl.grammar.DecisionDslLexer;
import io.finmsg.dmn.frontend.dsl.grammar.DecisionDslParser;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.Interval;

/**
 * ANTLR4-backed bounded parser for the {@code .decision} authoring DSL.
 */
final class DmnDslParser {

	private DmnDslParser() {
	}

	static DslModel parse(String source, DmnDslReadOptions options) {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(options, "options");
		byte[] utf8 = source.getBytes(StandardCharsets.UTF_8);
		if (utf8.length > options.maxBytes()) {
			throw new DmnDslParseException(new DmnDslDiagnostic("DSL-LIMIT-BYTES", 1, 1,
					"DSL source exceeds maxBytes=" + options.maxBytes() + " (actual=" + utf8.length + ")"));
		}

		List<DmnDslDiagnostic> diagnostics = new ArrayList<>();
		CharStream charStream = CharStreams.fromString(source);
		DecisionDslLexer lexer = new DecisionDslLexer(charStream);
		lexer.removeErrorListeners();
		lexer.addErrorListener(new DiagnosticErrorListener("DSL-LEXER", diagnostics));

		CommonTokenStream tokens = new CommonTokenStream(lexer);
		DecisionDslParser parser = new DecisionDslParser(tokens);
		parser.removeErrorListeners();
		parser.addErrorListener(new DiagnosticErrorListener("DSL-SYNTAX", diagnostics));

		DecisionDslParser.DecisionFileContext tree = parser.decisionFile();
		if (!diagnostics.isEmpty()) {
			throw new DmnDslParseException(diagnostics);
		}

		AstBuilder builder = new AstBuilder(charStream, options, diagnostics);
		DslModel model = builder.build(tree);
		if (!diagnostics.isEmpty()) {
			throw new DmnDslParseException(diagnostics);
		}
		return model;
	}

	private static final class DiagnosticErrorListener extends BaseErrorListener {
		private final String code;
		private final List<DmnDslDiagnostic> diagnostics;

		private DiagnosticErrorListener(String code, List<DmnDslDiagnostic> diagnostics) {
			this.code = code;
			this.diagnostics = diagnostics;
		}

		@Override
		public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine,
				String msg, RecognitionException e) {
			diagnostics.add(new DmnDslDiagnostic(code, Math.max(1, line), Math.max(1, charPositionInLine + 1), msg));
		}
	}

	private static final class AstBuilder extends DecisionDslBaseVisitor<Void> {
		private final CharStream input;
		private final DmnDslReadOptions options;
		private final List<DmnDslDiagnostic> diagnostics;

		private String modelName;
		private String namespace;
		private Optional<String> modelId = Optional.empty();
		private final List<DslImport> imports = new ArrayList<>();
		private final List<DslType> types = new ArrayList<>();
		private final List<DslInput> inputs = new ArrayList<>();
		private final List<DslBkm> bkms = new ArrayList<>();
		private final List<DslDecision> decisions = new ArrayList<>();
		private final List<DslTest> tests = new ArrayList<>();
		private int declarations = 0;

		private AstBuilder(CharStream input, DmnDslReadOptions options, List<DmnDslDiagnostic> diagnostics) {
			this.input = input;
			this.options = options;
			this.diagnostics = diagnostics;
		}

		DslModel build(DecisionDslParser.DecisionFileContext ctx) {
			for (DecisionDslParser.StatementContext stmt : ctx.statement()) {
				visit(stmt);
			}
			if (modelName == null || modelName.isBlank()) {
				error("DSL-MISSING-MODEL", 1, 1, "Missing required 'model <name>' declaration.");
			}
			if (namespace == null || namespace.isBlank()) {
				error("DSL-MISSING-NAMESPACE", 1, 1, "Missing required 'namespace \"<uri>\"' declaration.");
			}
			if (!diagnostics.isEmpty()) {
				return null;
			}
			return new DslModel(modelName, namespace, modelId, imports, types, inputs, bkms, decisions, tests);
		}

		@Override
		public Void visitModelDecl(DecisionDslParser.ModelDeclContext ctx) {
			if (modelName != null) {
				error("DSL-DUPLICATE-HEADER", line(ctx), col(ctx), "Duplicate 'model' declaration.");
			}
			modelName = unquote(ctx.nameExpr());
			return null;
		}

		@Override
		public Void visitNamespaceDecl(DecisionDslParser.NamespaceDeclContext ctx) {
			if (namespace != null) {
				error("DSL-DUPLICATE-HEADER", line(ctx), col(ctx), "Duplicate 'namespace' declaration.");
			}
			namespace = unquoteString(ctx.STRING().getText());
			return null;
		}

		@Override
		public Void visitIdDecl(DecisionDslParser.IdDeclContext ctx) {
			if (modelId.isPresent()) {
				error("DSL-DUPLICATE-HEADER", line(ctx), col(ctx), "Duplicate 'id' declaration.");
			}
			modelId = Optional.of(unquoteString(ctx.STRING().getText()));
			return null;
		}

		@Override
		public Void visitImportDecl(DecisionDslParser.ImportDeclContext ctx) {
			checkDeclLimit(line(ctx));
			String alias = unquote(ctx.nameExpr());
			String location = unquoteString(ctx.STRING(0).getText());
			String ns = unquoteString(ctx.STRING(1).getText());
			imports.add(new DslImport(alias, location, ns, line(ctx)));
			return null;
		}

		@Override
		public Void visitTypeDecl(DecisionDslParser.TypeDeclContext ctx) {
			checkDeclLimit(line(ctx));
			String name = unquote(ctx.nameExpr());
			Optional<String> id = ctx.STRING() == null
					? Optional.empty()
					: Optional.of(unquoteString(ctx.STRING().getText()));
			if (ctx.recordTypeBody() != null) {
				List<DslRecordField> fields = new ArrayList<>();
				for (var fieldCtx : ctx.recordTypeBody().recordField()) {
					String fieldName = unquote(fieldCtx.nameExpr());
					String fieldType = exactText(fieldCtx.typeRef());
					Optional<String> constraint = fieldCtx.constraintExpr() == null
							? Optional.empty()
							: Optional.of(exactText(fieldCtx.constraintExpr()));
					fields.add(new DslRecordField(fieldName, fieldType, constraint, line(fieldCtx)));
				}
				if (fields.isEmpty()) {
					error("DSL-EMPTY-RECORD", line(ctx), col(ctx),
							"Record type '" + name + "' must declare at least one field.");
					return null;
				}
				types.add(
						new DslType(name, id, TypeKind.RECORD, Optional.empty(), Optional.empty(), fields, line(ctx)));
			} else if (ctx.collectionTypeBody() != null) {
				String elemType = exactText(ctx.collectionTypeBody().typeRef());
				types.add(new DslType(name, id, TypeKind.COLLECTION, Optional.of(elemType), Optional.empty(), List.of(),
						line(ctx)));
			} else if (ctx.aliasTypeBody() != null) {
				String baseType = exactText(ctx.aliasTypeBody().typeRef());
				Optional<String> constraint = ctx.aliasTypeBody().constraintExpr() == null
						? Optional.empty()
						: Optional.of(exactText(ctx.aliasTypeBody().constraintExpr()));
				types.add(
						new DslType(name, id, TypeKind.ALIAS, Optional.of(baseType), constraint, List.of(), line(ctx)));
			}
			return null;
		}

		@Override
		public Void visitInputDecl(DecisionDslParser.InputDeclContext ctx) {
			checkDeclLimit(line(ctx));
			String name = unquote(ctx.nameExpr());
			Optional<String> id = ctx.STRING() == null
					? Optional.empty()
					: Optional.of(unquoteString(ctx.STRING().getText()));
			String typeRef = exactText(ctx.typeRef());
			inputs.add(new DslInput(name, id, typeRef, line(ctx)));
			return null;
		}

		@Override
		public Void visitBkmDecl(DecisionDslParser.BkmDeclContext ctx) {
			checkDeclLimit(line(ctx));
			String name = unquote(ctx.nameExpr());
			Optional<String> id = ctx.STRING() == null
					? Optional.empty()
					: Optional.of(unquoteString(ctx.STRING().getText()));
			List<DslParam> params = new ArrayList<>();
			if (ctx.paramList() != null) {
				for (var p : ctx.paramList().param()) {
					params.add(new DslParam(unquote(p.nameExpr()), exactText(p.typeRef())));
				}
			}
			String returnType = exactText(ctx.typeRef());
			List<DslRef> uses = ctx.usesClause() == null ? List.of() : extractRefs(ctx.usesClause().refList());
			String body = normalizeMultilineFeel(ctx.feelBody());
			bkms.add(new DslBkm(name, id, params, returnType, uses, body, line(ctx)));
			return null;
		}

		@Override
		public Void visitDecisionDecl(DecisionDslParser.DecisionDeclContext ctx) {
			checkDeclLimit(line(ctx));
			String name = unquote(ctx.nameExpr());
			Optional<String> id = ctx.STRING() == null
					? Optional.empty()
					: Optional.of(unquoteString(ctx.STRING().getText()));
			String typeRef = exactText(ctx.typeRef());
			List<DslRef> requires = new ArrayList<>();
			List<DslRef> uses = new ArrayList<>();
			for (var clause : ctx.decisionClause()) {
				if (clause.requiresClause() != null) {
					requires.addAll(extractRefs(clause.requiresClause().refList()));
				} else if (clause.usesClause() != null) {
					uses.addAll(extractRefs(clause.usesClause().refList()));
				}
			}
			if (ctx.tableBlock() != null) {
				DslTable table = buildTable(ctx.tableBlock());
				if (table != null) {
					decisions.add(new DslDecision(name, id, typeRef, requires, uses, Optional.empty(),
							Optional.of(table), line(ctx)));
				}
			} else {
				String body = normalizeMultilineFeel(ctx.feelBody());
				decisions.add(new DslDecision(name, id, typeRef, requires, uses, Optional.of(body), Optional.empty(),
						line(ctx)));
			}
			return null;
		}

		@Override
		public Void visitTestDecl(DecisionDslParser.TestDeclContext ctx) {
			checkDeclLimit(line(ctx));
			String name = unquoteString(ctx.STRING().getText());
			List<DslTestEntry> givens = new ArrayList<>();
			List<DslTestEntry> expects = new ArrayList<>();
			for (var entryCtx : ctx.testEntry()) {
				String entryName = unquote(entryCtx.nameExpr());
				String expr = exactText(entryCtx.testExpr());
				DslTestEntry entry = new DslTestEntry(entryName, expr, line(entryCtx));
				if (entryCtx.kind.getType() == DecisionDslParser.GIVEN) {
					givens.add(entry);
				} else {
					expects.add(entry);
				}
			}
			if (expects.isEmpty()) {
				error("DSL-TEST-EMPTY", line(ctx), col(ctx),
						"Test '" + name + "' must declare at least one 'expect' assertion.");
				return null;
			}
			tests.add(new DslTest(name, givens, expects, line(ctx)));
			return null;
		}

		private DslTable buildTable(DecisionDslParser.TableBlockContext ctx) {
			String hitPolicy = exactText(ctx.hitPolicy());
			var header = ctx.tableHeader();
			List<DslTableInput> inCols = new ArrayList<>();
			for (var inCtx : header.tableInputCol()) {
				String expr = exactText(inCtx.expr);
				Optional<String> type = inCtx.typeRef() == null
						? Optional.empty()
						: Optional.of(exactText(inCtx.typeRef()));
				inCols.add(new DslTableInput(expr, type, Optional.empty()));
			}
			List<DslTableOutput> outCols = new ArrayList<>();
			for (var outCtx : header.tableOutputCol()) {
				Optional<String> outName = outCtx.name == null ? Optional.empty() : Optional.of(unquote(outCtx.name));
				if (outName.isPresent() && outName.get().equals("_")) {
					outName = Optional.empty();
				}
				Optional<String> outType = Optional.of(exactText(outCtx.type));
				Optional<String> defExpr = outCtx.defExpr == null
						? Optional.empty()
						: Optional.of(exactText(outCtx.defExpr));
				outCols.add(new DslTableOutput(outName, outType, defExpr));
			}

			List<DslTableRule> rules = new ArrayList<>();
			for (var ruleCtx : ctx.tableRule()) {
				int arrowTokenIndex = ruleCtx.ARROW().getSymbol().getTokenIndex();
				List<String> inEntries = new ArrayList<>();
				List<String> outEntries = new ArrayList<>();
				for (var cell : ruleCtx.ruleCell()) {
					if (cell.getStart().getTokenIndex() < arrowTokenIndex) {
						inEntries.add(exactText(cell));
					} else {
						outEntries.add(exactText(cell));
					}
				}
				if (inEntries.size() != inCols.size() || outEntries.size() != outCols.size()) {
					error("DSL-TABLE-ARITY", line(ruleCtx), col(ruleCtx),
							"Rule arity mismatch: expected " + inCols.size() + " inputs and " + outCols.size()
									+ " outputs, got " + inEntries.size() + " and " + outEntries.size());
					continue;
				}
				rules.add(new DslTableRule(inEntries, outEntries, line(ruleCtx)));
			}
			return new DslTable(hitPolicy, inCols, outCols, rules, line(ctx));
		}

		private List<DslRef> extractRefs(DecisionDslParser.RefListContext ctx) {
			List<DslRef> refs = new ArrayList<>();
			for (var refCtx : ctx.elementRef()) {
				Optional<String> alias = refCtx.alias == null ? Optional.empty() : Optional.of(refCtx.alias.getText());
				String target = unquote(refCtx.target);
				Optional<String> explicitId = refCtx.explicitId == null
						? Optional.empty()
						: Optional.of(refCtx.explicitId.getText());
				refs.add(new DslRef(alias, target, explicitId));
			}
			return refs;
		}

		private void checkDeclLimit(int line) {
			if (++declarations > options.maxDeclarations()) {
				throw new DmnDslParseException(new DmnDslDiagnostic("DSL-LIMIT-DECLS", line, 1,
						"DSL model exceeds maxDeclarations=" + options.maxDeclarations()));
			}
		}

		private String normalizeMultilineFeel(DecisionDslParser.FeelBodyContext ctx) {
			StringBuilder sb = new StringBuilder();
			for (var lineCtx : ctx.feelLine()) {
				if (!sb.isEmpty()) {
					sb.append(' ');
				}
				sb.append(exactText(lineCtx));
			}
			return sb.toString().trim();
		}

		private String exactText(ParserRuleContext ctx) {
			if (ctx == null || ctx.getStart() == null || ctx.getStop() == null) {
				return "";
			}
			int start = ctx.getStart().getStartIndex();
			int stop = ctx.getStop().getStopIndex();
			if (start < 0 || stop < start) {
				return "";
			}
			return input.getText(Interval.of(start, stop)).trim();
		}

		private static String unquote(DecisionDslParser.NameExprContext ctx) {
			if (ctx.STRING() != null) {
				return unquoteString(ctx.STRING().getText());
			}
			return ctx.getText();
		}

		private static String unquoteString(String literal) {
			StringBuilder out = new StringBuilder();
			for (int i = 1; i < literal.length() - 1; i++) {
				char c = literal.charAt(i);
				if (c == '\\' && i + 1 < literal.length() - 1) {
					char next = literal.charAt(++i);
					switch (next) {
						case '"' -> out.append('"');
						case '\\' -> out.append('\\');
						case 'n' -> out.append('\n');
						case 'r' -> out.append('\r');
						case 't' -> out.append('\t');
						default -> out.append(next);
					}
				} else {
					out.append(c);
				}
			}
			return out.toString();
		}

		private static int line(ParserRuleContext ctx) {
			return ctx.getStart() == null ? 1 : Math.max(1, ctx.getStart().getLine());
		}

		private static int col(ParserRuleContext ctx) {
			return ctx.getStart() == null ? 1 : Math.max(1, ctx.getStart().getCharPositionInLine() + 1);
		}

		private void error(String code, int line, int col, String message) {
			diagnostics.add(new DmnDslDiagnostic(code, line, col, message));
		}
	}
}
