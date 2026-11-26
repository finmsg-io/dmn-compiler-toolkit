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

class ExportArithmeticFixture {
    private static final String NS = "urn:rust-proof:arithmetic";

    public static void main(String[] args) throws Exception {
        String xml = """
                <definitions xmlns="https://www.omg.org/spec/DMN/20240513/MODEL/"
                  namespace="urn:rust-proof:arithmetic" id="arithmetic" name="Arithmetic">
                  <inputData id="a" name="A"><variable name="A" typeRef="number"/></inputData>
                  <inputData id="b" name="B"><variable name="B" typeRef="number"/></inputData>
                """
                + decision("sum", "Sum", "A + B", "Input:a", "Input:b")
                + decision("difference", "Difference", "A - B", "Input:a", "Input:b")
                + decision("net", "Net", "Sum - Difference", "Decision:sum", "Decision:difference")
                + decision("selected", "Selected", "if Net &gt;= 0 then Sum else Difference",
                        "Decision:net", "Decision:sum", "Decision:difference")
                + "</definitions>";
        var artifact = produce(xml);
        if (artifact.artifact().getSchemaVersion().getMinor() != 1) {
            throw new AssertionError("Expected schema 0.1");
        }
        var adapter = new DmnIrRuntimeAdapter().adapt(artifact);
        for (String[] vector : new String[][] {
                {"999.99", "0.01"}, {"-1.20", "0.3"}, {"1", "-1000"}, {"1.2", "-1.20"},
                {"1E+4096", "1E-4096"}, {"1E+3", "2E+2"}, {"0.000", "0E+2"},
                {null, "1.2"}, {"1.2", null}
        }) {
            var a = vector[0] == null ? null : new BigDecimal(vector[0]);
            var b = vector[1] == null ? null : new BigDecimal(vector[1]);
            Map<ElementId, Object> inputs = new HashMap<>();
            inputs.put(key("a"), a);
            inputs.put(key("b"), b);
            var result = adapter.evaluate(inputs);
            BigDecimal sum = a == null || b == null ? null : a.add(b);
            BigDecimal difference = a == null || b == null ? null : a.subtract(b);
            BigDecimal net = sum == null ? null : sum.subtract(difference);
            expect(result, "sum", sum);
            expect(result, "difference", difference);
            expect(result, "net", net);
            expect(result, "selected", net != null && net.signum() >= 0 ? sum : difference);
        }
        Files.write(Path.of(args[0]), artifact.artifact().toByteArray());
        if (args.length > 1) {
            var unsupported = produce(xml.replace("A + B", "A * B"));
            Files.write(Path.of(args[1]), unsupported.artifact().toByteArray());
        }
    }

    private static DmnIrArtifactReader.VerifiedArtifact produce(String xml) {
        return new DmnIrScalarProducer().produce(new DmnSource(DmnSourceId.of("memory:/arithmetic.dmn"),
                xml.getBytes(StandardCharsets.UTF_8)));
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
