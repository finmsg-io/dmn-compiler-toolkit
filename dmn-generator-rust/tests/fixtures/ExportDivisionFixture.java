import io.finmsg.dmn.compiler.DmnSource;
import io.finmsg.dmn.compiler.DmnSourceId;
import io.finmsg.dmn.compiler.dmnir.DmnIrRuntimeAdapter;
import io.finmsg.dmn.compiler.dmnir.DmnIrRuntimeAdapter.ElementId;
import io.finmsg.dmn.compiler.dmnir.DmnIrScalarProducer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

class ExportDivisionFixture {
    private static final String NS = "urn:rust-proof:division";

    public static void main(String[] args) throws Exception {
        String xml = """
                <definitions xmlns="https://www.omg.org/spec/DMN/20240513/MODEL/"
                  namespace="urn:rust-proof:division" id="division" name="Division">
                  <inputData id="a" name="A"><variable name="A" typeRef="number"/></inputData>
                  <inputData id="b" name="B"><variable name="B" typeRef="number"/></inputData>
                  <decision id="quotient" name="Quotient"><variable name="Quotient" typeRef="number"/>
                    <informationRequirement><requiredInput href="#a"/></informationRequirement>
                    <informationRequirement><requiredInput href="#b"/></informationRequirement>
                    <literalExpression><text>A / B</text></literalExpression></decision>
                  <decision id="adjusted" name="Adjusted"><variable name="Adjusted" typeRef="number"/>
                    <informationRequirement><requiredDecision href="#quotient"/></informationRequirement>
                    <literalExpression><text>Quotient + 1.00</text></literalExpression></decision>
                  <decision id="mixed" name="Mixed"><variable name="Mixed" typeRef="number"/>
                    <informationRequirement><requiredInput href="#a"/></informationRequirement>
                    <informationRequirement><requiredInput href="#b"/></informationRequirement>
                    <literalExpression><text>A * 2 / B</text></literalExpression></decision>
                </definitions>
                """;
        var producer = new DmnIrScalarProducer();
        var artifact = producer.produce(source(xml));
        if (artifact.artifact().getSchemaVersion().getMinor() != 1) {
            throw new AssertionError("Expected schema 0.1");
        }
        var adapter = new DmnIrRuntimeAdapter().adapt(artifact);
        var vectors = new StringBuilder();
        for (String[] pair : new String[][] {
                {"1.00", "2"}, {"1", "2.00"}, {"0.00", "2.000"}, {"1", "32"},
                {"1", "3"}, {"2", "-3"}, {"1", "22"}, {"-1", "-22"}, {"99", "9900"},
                {"1" + "0".repeat(33) + "5", "1"},
                {"1" + "0".repeat(32) + "15", "1"},
                {"1" + "0".repeat(33) + "51", "1"},
                {"9".repeat(34) + "5", "1"},
                {"-" + "9".repeat(34) + "5", "1"},
                {"1E+4096", "1E-4096"}, {"1E-4096", "3E+4096"},
                {"1E-4096", "9".repeat(4096) + "E+4096"},
                {"9".repeat(4096), "8".repeat(4096)},
                {"1", "0.00"}, {"0", "0"}, {null, "2"}, {"2", null}
        }) {
            add(vectors, adapter, decimal(pair[0]), decimal(pair[1]));
        }
        var random = new Random(1209);
        for (int i = 0; i < 96; i++) {
            add(vectors, adapter, randomDecimal(random), randomDecimal(random));
        }
        Files.write(Path.of(args[0]), artifact.artifact().toByteArray());
        Files.writeString(Path.of(args[1]), vectors);
        var unsupported = producer.produce(source(xml.replace("A / B", "A ** B")));
        Files.write(Path.of(args[2]), unsupported.artifact().toByteArray());
    }

    private static void add(StringBuilder vectors, DmnIrRuntimeAdapter.AdaptedModel adapter,
            BigDecimal a, BigDecimal b) {
        Map<ElementId, Object> inputs = new HashMap<>();
        inputs.put(key("a"), a);
        inputs.put(key("b"), b);
        var values = adapter.evaluate(inputs);
        boolean canDivide = a != null && b != null && b.signum() != 0;
        BigDecimal quotient = canDivide ? a.divide(b, MathContext.DECIMAL128) : null;
        BigDecimal adjusted = canDivide ? quotient.add(new BigDecimal("1.00")) : null;
        BigDecimal mixed = canDivide ? a.multiply(BigDecimal.TWO).divide(b, MathContext.DECIMAL128) : null;
        expect(values, "quotient", quotient);
        expect(values, "adjusted", adjusted);
        expect(values, "mixed", mixed);
        vectors.append(String.join("\t", value(a), value(b), value(quotient), value(adjusted), value(mixed)))
                .append('\n');
    }

    private static String value(BigDecimal value) {
        return value == null ? "null" : value.unscaledValue() + "@" + value.scale();
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private static BigDecimal randomDecimal(Random random) {
        var coefficient = new BigInteger(1 + random.nextInt(250), random);
        if (random.nextBoolean()) {
            coefficient = coefficient.negate();
        }
        return new BigDecimal(coefficient, random.nextInt(-60, 61));
    }

    private static ElementId key(String id) {
        return new ElementId(NS, id);
    }

    private static DmnSource source(String xml) {
        return new DmnSource(DmnSourceId.of("memory:/division.dmn"), xml.getBytes(StandardCharsets.UTF_8));
    }

    private static void expect(Map<ElementId, Object> values, String id, BigDecimal expected) {
        if (!values.containsKey(key(id)) || !Objects.equals(values.get(key(id)), expected)) {
            throw new AssertionError(id + ": expected " + expected + ", got " + values.get(key(id)));
        }
    }
}
