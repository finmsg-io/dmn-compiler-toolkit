import io.finmsg.dmn.compiler.DmnSource;
import io.finmsg.dmn.compiler.DmnSourceId;
import io.finmsg.dmn.compiler.dmnir.DmnIrRuntimeAdapter;
import io.finmsg.dmn.compiler.dmnir.DmnIrRuntimeAdapter.ElementId;
import io.finmsg.dmn.compiler.dmnir.DmnIrScalarProducer;
import io.finmsg.dmn.dmnir.codec.DmnIrArtifactReader;
import io.finmsg.dmn.dmnir.proto.v0.DmnIrUnaryOperator;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

class ExportExpressionFixture {
    private static final String NS = "urn:rust-proof:expressions";

    public static void main(String[] args) throws Exception {
        String xml = """
                <definitions xmlns="https://www.omg.org/spec/DMN/20240513/MODEL/"
                  namespace="urn:rust-proof:expressions" id="expressions" name="Expressions">
                  <inputData id="a" name="A"><variable name="A" typeRef="number"/></inputData>
                  <inputData id="b" name="B"><variable name="B" typeRef="number"/></inputData>
                  <inputData id="flag" name="Flag"><variable name="Flag" typeRef="boolean"/></inputData>
                  <inputData id="other" name="Other"><variable name="Other" typeRef="boolean"/></inputData>
                  <inputData id="left" name="LeftText"><variable name="LeftText" typeRef="string"/></inputData>
                  <inputData id="right" name="RightText"><variable name="RightText" typeRef="string"/></inputData>
                """ + decision("selected", "Selected", "number", "if Flag then A else B", "flag", "a", "b")
                + decision("negated", "Negated", "number", "-A", "a")
                + decision("positive", "Positive", "number", "-A", "a")
                + decision("equal", "Equal", "boolean", "A = B", "a", "b")
                + decision("different", "Different", "boolean", "A != B", "a", "b")
                + decision("less", "Less", "boolean", "A &lt; B", "a", "b")
                + decision("less-equal", "Less Equal", "boolean", "A &lt;= B", "a", "b")
                + decision("greater", "Greater", "boolean", "A &gt; B", "a", "b")
                + decision("greater-equal", "Greater Equal", "boolean", "A &gt;= B", "a", "b")
                + decision("text-less", "Text Less", "boolean", "LeftText &lt; RightText", "left", "right")
                + decision("mixed", "Mixed", "boolean", "A = Flag", "a", "flag")
                + decision("both", "Both", "boolean", "Flag and Other", "flag", "other")
                + decision("either", "Either", "boolean", "Flag or Other", "flag", "other")
                + decision("opposite", "Opposite", "boolean", "not(Flag)", "flag")
                + "</definitions>";
        var source = new DmnSource(DmnSourceId.of("memory:/expressions.dmn"),
                xml.getBytes(StandardCharsets.UTF_8));
        var produced = new DmnIrScalarProducer().produce(source).artifact().toBuilder();
        // Unary plus is a wire operation. The source parser cannot spell it.
        var positive = produced.getModelsBuilder(0).getDecisionsBuilderList().stream()
                .filter(decision -> decision.getIdentity().getId().equals("positive")).findFirst().orElseThrow();
        positive.getExpressionBuilder().getUnaryBuilder().setOperator(DmnIrUnaryOperator.UNARY_OPERATOR_POSITIVE);
        var artifact = new DmnIrArtifactReader().read(produced.build().toByteArray());
        if (artifact.artifact().getSchemaVersion().getMinor() != 1) {
            throw new AssertionError("Expected schema 0.1");
        }
        var adapter = new DmnIrRuntimeAdapter().adapt(artifact);
        Boolean[] flags = {false, true, null};
        Boolean[][] and = {{false, false, false}, {false, true, null}, {false, null, null}};
        Boolean[][] or = {{false, true, null}, {true, true, true}, {null, true, null}};
        var a = new BigDecimal("12345678901234567890.100");
        var b = new BigDecimal("12345678901234567890.10");
        Map<ElementId, Object> inputs = new HashMap<>();
        inputs.put(key("a"), a);
        inputs.put(key("b"), b);
        inputs.put(key("left"), "😀");
        inputs.put(key("right"), "\uE000");
        for (int left = 0; left < flags.length; left++) {
            for (int right = 0; right < flags.length; right++) {
                var flag = flags[left];
                inputs.put(key("flag"), flag);
                inputs.put(key("other"), flags[right]);
                var result = adapter.evaluate(inputs);
                expect(result, "both", and[left][right]);
                expect(result, "either", or[left][right]);
                expect(result, "opposite", flag == null ? null : !flag);
                expect(result, "selected", Boolean.TRUE.equals(flag) ? a : b);
                expect(result, "mixed", flag == null ? false : null);
                expect(result, "equal", true);
                expect(result, "different", false);
                expect(result, "less", false);
                expect(result, "less-equal", true);
                expect(result, "greater", false);
                expect(result, "greater-equal", true);
                expect(result, "negated", a.negate());
                expect(result, "positive", a);
                expect(result, "text-less", true);
            }
        }
        inputs.put(key("a"), null);
        inputs.put(key("b"), null);
        var nulls = adapter.evaluate(inputs);
        expect(nulls, "equal", true);
        expect(nulls, "less", null);
        expect(nulls, "mixed", true);
        expect(nulls, "negated", null);
        expect(nulls, "positive", null);
        expect(nulls, "selected", null);
        inputs.put(key("a"), new BigDecimal("-1000"));
        inputs.put(key("b"), new BigDecimal("-999.9"));
        var negatives = adapter.evaluate(inputs);
        expect(negatives, "less", true);
        expect(negatives, "different", true);
        expect(negatives, "greater-equal", false);
        inputs.put(key("a"), new BigDecimal("1E+4096"));
        inputs.put(key("b"), new BigDecimal("0E-4096"));
        expect(adapter.evaluate(inputs), "greater", true);
        inputs.put(key("a"), new BigDecimal("0E+4096"));
        var zero = adapter.evaluate(inputs);
        expect(zero, "equal", true);
        expect(zero, "negated", new BigDecimal("0E+4096"));
        Files.write(Path.of(args[0]), artifact.artifact().toByteArray());
    }

    private static String decision(String id, String name, String type, String expression, String... dependencies) {
        var xml = new StringBuilder("<decision id=\"%s\" name=\"%s\"><variable name=\"%s\" typeRef=\"%s\"/>"
                .formatted(id, name, name, type));
        for (String dependency : dependencies) {
            xml.append("<informationRequirement><requiredInput href=\"#%s\"/></informationRequirement>"
                    .formatted(dependency));
        }
        return xml.append("<literalExpression><text>").append(expression)
                .append("</text></literalExpression></decision>").toString();
    }

    private static ElementId key(String id) {
        return new ElementId(NS, id);
    }

    private static void expect(Map<ElementId, Object> result, String id, Object expected) {
        if (!result.containsKey(key(id)) || !Objects.equals(result.get(key(id)), expected)) {
            throw new AssertionError(id + ": expected " + expected + ", got " + result.get(key(id)));
        }
    }
}
