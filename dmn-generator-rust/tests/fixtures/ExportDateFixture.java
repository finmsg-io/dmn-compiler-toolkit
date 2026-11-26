import io.finmsg.dmn.compiler.*;
import io.finmsg.dmn.compiler.dmnir.*;
import io.finmsg.dmn.compiler.dmnir.DmnIrRuntimeAdapter.ElementId;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
class ExportDateFixture {
    static final String NS = "urn:rust-proof:dates";
    public static void main(String[] args) throws Exception {
        StringBuilder xml = new StringBuilder("""
            <definitions xmlns="https://www.omg.org/spec/DMN/20240513/MODEL/" id="dates" name="Dates" namespace="urn:rust-proof:dates">
            <itemDefinition id="profile" name="tProfile"><itemComponent name="When"><typeRef>date</typeRef></itemComponent></itemDefinition>
            <inputData id="input" name="Input"><variable name="Input" typeRef="date"/></inputData>
            <inputData id="record" name="Profile"><variable name="Profile" typeRef="tProfile"/></inputData>
            """);
        List<String> literals = List.of("-999999999-01-01", "-0400-02-29", "-0001-12-31", "0001-01-01", "1900-03-01", "2000-02-29", "2024-02-29", "9999-12-31", "10000-01-01", "999999999-12-31");
        for (int i = 0; i < literals.size(); i++) decision(xml, "literal" + i, "date", "date(&quot;" + literals.get(i) + "&quot;)", false);
        String[] operators = {"=", "!=", "&lt;", "&lt;=", "&gt;", "&gt;="};
        for (int i = 0; i < operators.length; i++) decision(xml, "compare" + i, "boolean", "Input " + operators[i] + " date(&quot;2024-02-29&quot;)", true);
        decision(xml, "mixed", "boolean", "Input = &quot;2024-02-29&quot;", true);
        decision(xml, "isnull", "boolean", "Input = null", true);
        xml.append("""
            <decision id="local" name="Local"><variable name="Local" typeRef="date"/>
            <informationRequirement><requiredInput href="#input"/></informationRequirement>
            <informationRequirement><requiredInput href="#record"/></informationRequirement>
            <context><contextEntry><variable name="When" typeRef="date"/><literalExpression><text>Profile.When</text></literalExpression></contextEntry>
            <contextEntry><literalExpression><text>if Input &lt; date(&quot;2024-02-29&quot;) then When else Input</text></literalExpression></contextEntry></context></decision>
            </definitions>
            """);
        var source = new DmnSource(DmnSourceId.of("memory:/dates.dmn"), xml.toString().getBytes(StandardCharsets.UTF_8));
        var verified = new DmnIrScalarProducer().produce(source);
        if (verified.artifact().getSchemaVersion().getMinor() != 4) throw new AssertionError("Expected 0.4");
        var model = new DmnIrRuntimeAdapter().adapt(verified);
        List<LocalDate> dates = new ArrayList<>();
        for (var text : literals) {
            var parts = text.substring(text.startsWith("-") ? 1 : 0).split("-");
            dates.add(LocalDate.of(Integer.parseInt(parts[0]) * (text.startsWith("-") ? -1 : 1), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
        }
        dates.add(LocalDate.of(2024, 2, 28)); dates.add(LocalDate.of(2024, 3, 1)); dates.add(null);
        StringBuilder vectors = new StringBuilder();
        for (LocalDate date : dates) for (LocalDate field : Arrays.asList(LocalDate.of(-400, 2, 29), null)) {
            Map<String, Object> record = new HashMap<>(); record.put("When", field);
            Map<ElementId, Object> inputs = new HashMap<>();
            inputs.put(new ElementId(NS, "input"), date); inputs.put(new ElementId(NS, "record"), record);
            var values = model.evaluate(inputs);
            for (var entry : values.entrySet()) vectors.append(encode(date)).append('\t').append(encode(field)).append('\t')
                    .append(entry.getKey().elementId()).append('\t').append(encode(entry.getValue())).append('\n');
        }
        Files.write(Path.of(args[0]), verified.artifact().toByteArray());
        Files.writeString(Path.of(args[1]), vectors);
    }
    static String encode(Object value) {
        if (value == null) return "N";
        if (value instanceof LocalDate d) return "D" + d.getYear() + "," + d.getMonthValue() + "," + d.getDayOfMonth();
        if (value instanceof Boolean b) return b ? "T" : "F";
        throw new AssertionError(value);
    }
    static void decision(StringBuilder xml, String id, String type, String text, boolean input) {
        xml.append("<decision id=\"").append(id).append("\" name=\"").append(id).append("\"><variable name=\"").append(id).append("\" typeRef=\"").append(type).append("\"/>");
        if (input) xml.append("<informationRequirement><requiredInput href=\"#input\"/></informationRequirement>");
        xml.append("<literalExpression><text>").append(text).append("</text></literalExpression></decision>");
    }
}
