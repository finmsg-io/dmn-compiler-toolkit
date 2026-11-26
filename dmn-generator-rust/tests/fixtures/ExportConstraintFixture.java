import io.finmsg.dmn.compiler.dmnir.*;
import io.finmsg.dmn.compiler.dmnir.DmnIrRuntimeAdapter.ElementId;
import io.finmsg.dmn.dmnir.codec.DmnIrArtifactReader;
import io.finmsg.dmn.dmnir.proto.v0.*;
import io.finmsg.dmn.runtime.DmnEvaluationException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
class ExportConstraintFixture {
    static final String NS = "urn:rust-proof:tables";
    static DmnIrExpression literal(int type, DmnIrLiteral value) { return DmnIrExpression.newBuilder().setTypeValue(type).setLiteral(value).build(); }
    static DmnIrExpression number(String value) { var n = new BigDecimal(value); return literal(3,DmnIrLiteral.newBuilder().setNumberValue(DmnIrDecimal.newBuilder().setUnscaledValue(n.unscaledValue().toString()).setScale(n.scale())).build()); }
    static DmnIrUnaryTest comparison(int op, DmnIrExpression value) { return DmnIrUnaryTest.newBuilder().setComparison(DmnIrTestComparison.newBuilder().setOperatorValue(op).setEndpoint(value)).build(); }
    static DmnIrUnaryTests group(DmnIrUnaryTest... tests) { return DmnIrUnaryTests.newBuilder().addAllTests(List.of(tests)).build(); }
    static DmnIrUnaryTest range(DmnIrExpression a, DmnIrExpression b) { return DmnIrUnaryTest.newBuilder().setRange(DmnIrTestRange.newBuilder().setLower(a).setUpper(b).setLowerInclusive(true).setUpperInclusive(true)).build(); }
    static DmnIrExpression date(int year, int month, int day) { return literal(5,DmnIrLiteral.newBuilder().setDateValue(DmnIrDate.newBuilder().setYear(year).setMonth(month).setDay(day)).build()); }
    static DmnIrRecordField field(String name, int type, DmnIrUnaryTests tests) { return DmnIrRecordField.newBuilder().setName(name).setType(DmnIrTypeReference.newBuilder().setScalarTypeValue(type)).setConstraint(tests).build(); }
    public static void main(String[] args) throws Exception {
        var reader = new DmnIrArtifactReader();
        var artifact = reader.read(Files.readAllBytes(Path.of(args[0]))).artifact().toBuilder();
        artifact.getSchemaVersionBuilder().setMinor(6); artifact.setSemanticsVersion("finmsg-dmnir-field-constraints/0"); artifact.addRequiredCapabilities("dmnir.field-constraints");
        var model = artifact.getModelsBuilder(0);
        model.getRecordDefinitionsBuilder(0).getFieldsBuilder(0).setConstraint(group(range(number("1"),number("2"))));
        model.addRecordDefinitions(DmnIrRecordDefinition.newBuilder().setIdentity(DmnIrElementIdentity.newBuilder().setId("details-type").setName("Details"))
                .addFields(field("Score",3,group(range(number("0"),number("10"))))));
        model.addRecordDefinitions(DmnIrRecordDefinition.newBuilder().setIdentity(DmnIrElementIdentity.newBuilder().setId("profile-type").setName("ProfileType"))
                .addFields(DmnIrRecordField.newBuilder().setName("Details").setType(DmnIrTypeReference.newBuilder().setRecordType(DmnIrReference.newBuilder().setModelNamespace(NS).setElementId("details-type"))))
                .addFields(field("Code",4,group(comparison(6,literal(4,DmnIrLiteral.newBuilder().setStringValue("blocked").build()))).toBuilder().setNegated(true).build()))
                .addFields(field("Enabled",2,group(comparison(6,literal(2,DmnIrLiteral.newBuilder().setBooleanValue(true).build())))))
                .addFields(field("When",5,group(range(date(2024,1,1),date(2025,1,1)),comparison(6,literal(1,DmnIrLiteral.newBuilder().setNullValueValue(0).build()))))));
        model.addInputs(DmnIrInput.newBuilder().setIdentity(DmnIrElementIdentity.newBuilder().setId("profile-input").setName("ProfileInput"))
                .setVariable(DmnIrVariable.newBuilder().setName("ProfileInput").setRecordType(DmnIrReference.newBuilder().setModelNamespace(NS).setElementId("profile-type"))));
        var checked = reader.read(artifact.build().toByteArray());
        var evaluator = new DmnIrRuntimeAdapter().adapt(checked);
        StringBuilder vectors = new StringBuilder();
        for (String text : List.of("null","-1","0","1","5","10","20")) for (int scenario=0;scenario<9;scenario++) {
            Map<String,Object> details = new HashMap<>(); details.put("Score",scenario==1 ? new BigDecimal("11") : scenario==7 ? null : new BigDecimal("5"));
            Map<String,Object> profile = new HashMap<>(); profile.put("Details",scenario==5 ? null : details);
            profile.put("Code",scenario==2 ? "blocked" : "ok"); profile.put("Enabled",scenario!=3);
            profile.put("When",scenario==4 ? LocalDate.of(2023,12,31) : scenario==8 ? null : LocalDate.of(2024,2,29));
            Map<ElementId,Object> inputs = new HashMap<>(); inputs.put(new ElementId(NS,"input"),text.equals("null")?null:new BigDecimal(text));
            inputs.put(new ElementId(NS,"profile-input"), scenario==6 ? null : profile);
            String expected;
            try { var values = evaluator.evaluate(inputs); var sorted=new TreeMap<String,String>(); values.forEach((k,v)->sorted.put(k.elementId(),encode(v))); expected=String.join(";",sorted.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).toList()); }
            catch (DmnEvaluationException e) { expected="ERROR"; }
            vectors.append(text).append('\t').append(scenario).append('\t').append(expected).append('\n');
        }
        Files.write(Path.of(args[1]),checked.artifact().toByteArray()); Files.writeString(Path.of(args[2]),vectors);
    }
    static String encode(Object value) {
        if(value==null)return "N"; if(value instanceof BigDecimal d)return "D"+d.unscaledValue()+","+d.scale();
        if(value instanceof Boolean b)return b?"T":"F"; if(value instanceof String s)return "S"+s;
        if(value instanceof io.finmsg.dmn.runtime.RuntimeContextValue c)return encode(c.namedFields());
        if(value instanceof Map<?,?> m){ var sorted=new TreeMap<String,String>();m.forEach((k,v)->sorted.put(k.toString(),encode(v)));return "R"+String.join("|",sorted.entrySet().stream().map(e->e.getKey()+":"+e.getValue()).toList()); }throw new AssertionError(value);
    }
}
