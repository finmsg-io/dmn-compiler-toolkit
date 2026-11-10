import io.finmsg.dmn.compiler.DmnSource;
import io.finmsg.dmn.compiler.DmnSourceId;
import io.finmsg.dmn.compiler.dmnir.DmnIrRuntimeAdapter;
import io.finmsg.dmn.compiler.dmnir.DmnIrRuntimeAdapter.ElementId;
import io.finmsg.dmn.compiler.dmnir.DmnIrScalarProducer;
import io.finmsg.dmn.dmnir.codec.DmnIrArtifactReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

class ExportMultiplicationFixture {
    private static final String NS = "urn:rust-proof:multiplication";

    public static void main(String[] args) throws Exception {
        String xml = """
                <definitions xmlns="https://www.omg.org/spec/DMN/20240513/MODEL/"
                  namespace="urn:rust-proof:multiplication" id="multiply" name="Multiplication">
                  <inputData id="a" name="A"><variable name="A" typeRef="number"/></inputData>
                  <inputData id="b" name="B"><variable name="B" typeRef="number"/></inputData>
                """
                + decision("product", "Product", "A * B", "Input:a", "Input:b")
                + decision("twice", "Twice", "Product + A * B", "Decision:product", "Input:a", "Input:b")
                + decision("cancelled", "Cancelled", "Product - A * B", "Decision:product", "Input:a", "Input:b")
                + decision("selected", "Selected", "if Product &gt;= 0 then Twice else Product",
                        "Decision:product", "Decision:twice")
                + "</definitions>";
        var artifact = produce(xml);
        var adapter = new DmnIrRuntimeAdapter().adapt(artifact);
        for (String[] vector : new String[][] {
                {"999999999", "999999999"}, {"999999999", "-1000000001"},
                {"-1000000001", "-1000000001"}, {"12345678901234567890.00", "0.010"},
                {"1E-4096", "1E-4096"}, {"-1E+4096", "1E+4096"},
                {"0.000", "-4E+2"}, {"9".repeat(4096), "9".repeat(4096)},
                {null, "1.2"}, {"1.2", null}
        }) {
            var a = vector[0] == null ? null : new BigDecimal(vector[0]);
            var b = vector[1] == null ? null : new BigDecimal(vector[1]);
            Map<ElementId, Object> inputs = new HashMap<>();
            inputs.put(key("a"), a);
            inputs.put(key("b"), b);
            var result = adapter.evaluate(inputs);
            BigDecimal product = a == null || b == null ? null : a.multiply(b);
            BigDecimal twice = product == null ? null : product.add(product);
            expect(result, "product", product);
            expect(result, "twice", twice);
            expect(result, "cancelled", product == null ? null : product.subtract(product));
            expect(result, "selected", product != null && product.signum() >= 0 ? twice : product);
        }
        Files.write(Path.of(args[0]), artifact.artifact().toByteArray());
        var unsupported = produce(xml.replace("A * B", "A / B"));
        Files.write(Path.of(args[1]), unsupported.artifact().toByteArray());
    }

    private static DmnIrArtifactReader.VerifiedArtifact produce(String xml) {
        var artifact = new DmnIrScalarProducer().produce(new DmnSource(DmnSourceId.of("memory:/multiplication.dmn"),
                xml.getBytes(StandardCharsets.UTF_8)));
        if (artifact.artifact().getSchemaVersion().getMinor() != 1) {
            throw new AssertionError("Expected schema 0.1");
        }
        return artifact;
    }

    private static String decision(String id, String name, String expression, String... dependencies) {
        var xml = new StringBuilder("<decision id=\"%s\" name=\"%s\"><variable name=\"%s\" typeRef=\"number\"/>"
                .formatted(id, name, name));
        for (String dependency : dependencies) {
            String[] target = dependency.split(":", 2);
            xml.append("<informationRequirement><required%s href=\"#%s\"/></informationRequirement>"
                    .formatted(target[0], target[1]));
        }
        return xml.append("<literalExpression><text>").append(expression)
                .append("</text></literalExpression></decision>").toString();
    }

    private static ElementId key(String id) {
        return new ElementId(NS, id);
    }

    private static void expect(Map<ElementId, Object> result, String id, Object expected) {
        if (!result.containsKey(key(id)) || !Objects.equals(result.get(key(id)), expected)) {
            throw new AssertionError(id + ": exact result differs");
        }
    }
}
