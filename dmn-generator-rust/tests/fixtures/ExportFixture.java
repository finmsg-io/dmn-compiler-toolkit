import io.finmsg.dmn.compiler.DmnSource;
import io.finmsg.dmn.compiler.DmnSourceId;
import io.finmsg.dmn.compiler.InMemoryDmnModelResolver;
import io.finmsg.dmn.compiler.dmnir.DmnIrRuntimeAdapter;
import io.finmsg.dmn.compiler.dmnir.DmnIrRuntimeAdapter.ElementId;
import io.finmsg.dmn.compiler.dmnir.DmnIrScalarProducer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

class ExportFixture {
    private static final String ROOT = "urn:rust-proof:root";
    private static final String LIB = "urn:rust-proof:lib";
    private static final String EXACT = "12345678901234567890.000012500";
    private static final String UNICODE = "Grüezi 🌍\n\"FEEL\"";

    public static void main(String[] args) throws Exception {
        var lib = source("lib.dmn", """
                <definitions xmlns="https://www.omg.org/spec/DMN/20240513/MODEL/"
                  namespace="urn:rust-proof:lib" id="lib" name="Library">
                  <decision id="rate" name="Rate"><variable name="Rate" typeRef="number"/>
                    <literalExpression><text>%s</text></literalExpression></decision>
                </definitions>
                """.formatted(EXACT));
        var root = source("root.dmn", """
                <definitions xmlns="https://www.omg.org/spec/DMN/20240513/MODEL/"
                  namespace="urn:rust-proof:root" id="root" name="Root">
                  <import name="Lib" namespace="urn:rust-proof:lib" locationURI="lib.dmn" importType="DMN"/>
                  <inputData id="text" name="Text"><variable name="Text" typeRef="string"/></inputData>
                  <inputData id="amount" name="Amount"><variable name="Amount" typeRef="number"/></inputData>
                  <decision id="rate" name="Imported Rate"><variable name="Imported Rate" typeRef="number"/>
                    <informationRequirement><requiredDecision href="urn:rust-proof:lib#rate"/></informationRequirement>
                    <literalExpression><text>Lib.Rate</text></literalExpression></decision>
                  <decision id="echo" name="Echo"><variable name="Echo" typeRef="string"/>
                    <informationRequirement><requiredInput href="#text"/></informationRequirement>
                    <literalExpression><text>Text</text></literalExpression></decision>
                  <decision id="nullable" name="Nullable"><variable name="Nullable" typeRef="number"/>
                    <informationRequirement><requiredInput href="#amount"/></informationRequirement>
                    <literalExpression><text>Amount</text></literalExpression></decision>
                  <decision id="flag" name="Flag"><variable name="Flag" typeRef="boolean"/>
                    <literalExpression><text>false</text></literalExpression></decision>
                </definitions>
                """);
        var verified = new DmnIrScalarProducer().produce(root, new InMemoryDmnModelResolver(List.of(lib)));
        if (verified.artifact().getSchemaVersion().getMinor() != 0) {
            throw new AssertionError("Expected schema 0.0");
        }
        var inputs = new HashMap<ElementId, Object>();
        inputs.put(new ElementId(ROOT, "text"), UNICODE);
        inputs.put(new ElementId(ROOT, "amount"), null);
        var result = new DmnIrRuntimeAdapter().adapt(verified).evaluate(inputs);
        expect(result.get(new ElementId(ROOT, "rate")), new BigDecimal(EXACT));
        expect(result.get(new ElementId(LIB, "rate")), new BigDecimal(EXACT));
        expect(result.get(new ElementId(ROOT, "echo")), UNICODE);
        if (!result.containsKey(new ElementId(ROOT, "nullable"))) {
            throw new AssertionError("Missing nullable result");
        }
        expect(result.get(new ElementId(ROOT, "nullable")), null);
        expect(result.get(new ElementId(ROOT, "flag")), false);
        Files.write(Path.of(args[0]), verified.artifact().toByteArray());
    }

    private static void expect(Object actual, Object expected) {
        if (!Objects.equals(actual, expected)) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }

    private static DmnSource source(String name, String xml) {
        return new DmnSource(DmnSourceId.of("memory:/" + name), xml.getBytes(StandardCharsets.UTF_8));
    }
}
