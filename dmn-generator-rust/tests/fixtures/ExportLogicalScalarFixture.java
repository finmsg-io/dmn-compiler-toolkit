import io.finmsg.dmn.compiler.DmnSource;
import io.finmsg.dmn.compiler.DmnSourceId;
import io.finmsg.dmn.compiler.dmnir.DmnIrRuntimeAdapter;
import io.finmsg.dmn.compiler.dmnir.DmnIrScalarDmnExporter;
import io.finmsg.dmn.compiler.dmnir.DmnIrScalarProducer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/** Regenerates the scalar logical truth table and checks Java and DMN round-trip execution. */
class ExportLogicalScalarFixture {
    static final String NS = "urn:rust-proof:logical-scalars";

    public static void main(String[] args) throws Exception {
        String[] operands = {"false", "true", "null", "123", "\"true\""};
        StringBuilder decisions = new StringBuilder();
        for (int op = 0, index = 0; op < 2; op++) {
            for (String left : operands) {
                for (String right : operands) {
                    String id = "c" + index++;
                    decisions.append("<decision id=\"").append(id).append("\" name=\"").append(id)
                        .append("\"><variable name=\"").append(id).append("\" typeRef=\"boolean\"/>")
                        .append("<literalExpression><text>").append(left)
                        .append(op == 0 ? " and " : " or ").append(right)
                        .append("</text></literalExpression></decision>");
                }
            }
        }
        var source = source("<definitions xmlns=\"https://www.omg.org/spec/DMN/20240513/MODEL/\""
            + " id=\"logical\" name=\"Logical\" namespace=\"" + NS + "\">" + decisions + "</definitions>");
        var producer = new DmnIrScalarProducer();
        var verified = producer.produce(source);
        check(new DmnIrRuntimeAdapter().adapt(verified).evaluate(Map.of()));
        var bundle = new DmnIrScalarDmnExporter().export(verified);
        var roundTrip = producer.produce(source(bundle.documents().get(bundle.rootDocument())));
        check(new DmnIrRuntimeAdapter().adapt(roundTrip).evaluate(Map.of()));
        Files.write(Path.of(args[0]), verified.artifact().toByteArray());
        System.out.println("50 logical results verified in Java and after DMN export/recompilation");
    }

    static void check(Map<DmnIrRuntimeAdapter.ElementId, Object> actual) {
        for (int op = 0, index = 0; op < 2; op++) {
            for (int left = 0; left < 5; left++) {
                for (int right = 0; right < 5; right++) {
                    Object expected;
                    if (op == 0) {
                        if (left == 0 || right == 0) expected = Boolean.FALSE;
                        else if (left == 1 && right == 1) expected = Boolean.TRUE;
                        else expected = null;
                    } else {
                        if (left == 1 || right == 1) expected = Boolean.TRUE;
                        else if (left == 0 && right == 0) expected = Boolean.FALSE;
                        else expected = null;
                    }
                    var id = new DmnIrRuntimeAdapter.ElementId(NS, "c" + index++);
                    if (!actual.containsKey(id) || !Objects.equals(expected, actual.get(id))) {
                        throw new AssertionError(id + ": expected " + expected + ", got " + actual.get(id));
                    }
                }
            }
        }
    }

    static DmnSource source(String xml) {
        return new DmnSource(DmnSourceId.of("memory:/logical.dmn"), xml.getBytes(StandardCharsets.UTF_8));
    }
}
