import io.finmsg.dmn.compiler.*;
import io.finmsg.dmn.compiler.dmnir.*;
import io.finmsg.dmn.compiler.dmnir.DmnIrRuntimeAdapter.ElementId;
import io.finmsg.dmn.dmnir.codec.DmnIrArtifactReader;
import io.finmsg.dmn.runtime.DmnEvaluationException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
class ExportTableFixture {
    static final String NS = "urn:rust-proof:tables";
    public static void main(String[] args) throws Exception {
        String xml = """
            <definitions xmlns="https://www.omg.org/spec/DMN/20240513/MODEL/" id="tables" name="Tables" namespace="urn:rust-proof:tables">
            <itemDefinition id="result-type" name="tResult"><itemComponent name="Fee"><typeRef>number</typeRef></itemComponent><itemComponent name="Reason"><typeRef>string</typeRef></itemComponent></itemDefinition>
            <inputData id="input" name="Input"><variable name="Input" typeRef="number"/></inputData>
            <decision id="first" name="First"><variable name="First" typeRef="number"/><informationRequirement><requiredInput href="#input"/></informationRequirement>
            <decisionTable id="first-table" hitPolicy="FIRST"><input><inputExpression typeRef="number"><text>Input</text></inputExpression></input><output typeRef="number"><defaultOutputEntry><text>99.00</text></defaultOutputEntry></output>
            <rule><inputEntry><text>[1 .. 10], 20</text></inputEntry><outputEntry><text>1.00</text></outputEntry></rule>
            <rule><inputEntry><text>&gt;= 5</text></inputEntry><outputEntry><text>2.0</text></outputEntry></rule></decisionTable></decision>
            <decision id="any" name="Any"><variable name="Any" typeRef="tResult"/><informationRequirement><requiredInput href="#input"/></informationRequirement>
            <decisionTable hitPolicy="ANY"><input><inputExpression typeRef="number"><text>Input</text></inputExpression></input><output name="Fee" typeRef="number"/><output name="Reason" typeRef="string"/>
            <rule><inputEntry><text>not(null, &lt; 0)</text></inputEntry><outputEntry><text>1.00</text></outputEntry><outputEntry><text>"same"</text></outputEntry></rule>
            <rule><inputEntry><text>&gt; 0</text></inputEntry><outputEntry><text>1.0</text></outputEntry><outputEntry><text>"same"</text></outputEntry></rule></decisionTable></decision>
            <decision id="unique" name="Unique"><variable name="Unique" typeRef="string"/><informationRequirement><requiredInput href="#input"/></informationRequirement>
            <decisionTable hitPolicy="UNIQUE"><input><inputExpression typeRef="number"><text>Input</text></inputExpression></input><output typeRef="string"/>
            <rule><inputEntry><text>null</text></inputEntry><outputEntry><text>"null"</text></outputEntry></rule>
            <rule><inputEntry><text>&lt; 0</text></inputEntry><outputEntry><text>"negative"</text></outputEntry></rule>
            <rule><inputEntry><text>[0 .. 10)</text></inputEntry><outputEntry><text>"small"</text></outputEntry></rule></decisionTable></decision>
            <decision id="wild" name="Wild"><variable name="Wild" typeRef="boolean"/>
            <decisionTable hitPolicy="FIRST"><input><inputExpression typeRef="boolean"><text>true</text></inputExpression></input><input><inputExpression typeRef="string"><text>"m"</text></inputExpression></input><input><inputExpression typeRef="date"><text>date("2024-02-29")</text></inputExpression></input><output typeRef="boolean"/>
            <rule><inputEntry><text>true</text></inputEntry><inputEntry><text>("a" .. "z"]</text></inputEntry><inputEntry><text>[date("2024-02-01") .. date("2024-03-01"))</text></inputEntry><outputEntry><text>true</text></outputEntry></rule>
            <rule><inputEntry><text>-</text></inputEntry><inputEntry><text>-</text></inputEntry><inputEntry><text>-</text></inputEntry><outputEntry><text>false</text></outputEntry></rule></decisionTable></decision>
            </definitions>
            """;
        var verified = new DmnIrScalarProducer().produce(new DmnSource(DmnSourceId.of("memory:/tables.dmn"), xml.getBytes(StandardCharsets.UTF_8)));
        if (verified.artifact().getSchemaVersion().getMinor() != 5) throw new AssertionError("Expected 0.5");
        for (String variant : List.of("tables", "tables-unique-conflict", "tables-any-conflict")) {
            var artifact = verified.artifact().toBuilder();
            if (!variant.equals("tables")) {
                var decision = artifact.getModelsBuilder(0).getDecisionsBuilderList().stream().filter(d -> d.getIdentity().getId().equals("first")).findFirst().orElseThrow();
                decision.getDecisionTableBuilder().setHitPolicyValue(variant.contains("unique") ? 1 : 3);
            }
            var checked = new DmnIrArtifactReader().read(artifact.build().toByteArray());
            var model = new DmnIrRuntimeAdapter().adapt(checked);
            StringBuilder vectors = new StringBuilder();
            for (String text : List.of("null", "-1", "0", "0.00", "1", "4.999", "5", "9.999", "10", "10.001", "20", "21")) {
                Map<ElementId,Object> inputs = new HashMap<>(); inputs.put(new ElementId(NS,"input"), text.equals("null") ? null : new BigDecimal(text));
                String expected;
                try { var values = model.evaluate(inputs); var sorted = new TreeMap<String,String>();
                    values.forEach((key,value) -> sorted.put(key.elementId(), encode(value)));
                    expected = String.join(";", sorted.entrySet().stream().map(e -> e.getKey()+"="+e.getValue()).toList());
                } catch (DmnEvaluationException e) { expected = "ERROR"; }
                vectors.append(text).append('\t').append(expected).append('\n');
            }
            Files.write(Path.of(args[0], variant+"-0.5.dmnir"), checked.artifact().toByteArray());
            Files.writeString(Path.of(args[0], variant+"_vectors.tsv"), vectors);
        }
    }
    static String encode(Object value) {
        if (value == null) return "N";
        if (value instanceof BigDecimal d) return "D"+d.unscaledValue()+","+d.scale();
        if (value instanceof Boolean b) return b ? "T" : "F";
        if (value instanceof String s) return "S"+s;
        if (value instanceof io.finmsg.dmn.runtime.RuntimeContextValue c) return encode(c.namedFields());
        if (value instanceof Map<?,?> m) { var sorted = new TreeMap<String,String>(); m.forEach((k,v) -> sorted.put(k.toString(),encode(v))); return "R"+String.join("|", sorted.entrySet().stream().map(e -> e.getKey()+":"+e.getValue()).toList()); }
        throw new AssertionError(value.getClass());
    }
}
