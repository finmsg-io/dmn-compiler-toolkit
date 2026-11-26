import io.finmsg.dmn.compiler.*;
import io.finmsg.dmn.compiler.dmnir.*;
import io.finmsg.dmn.compiler.dmnir.DmnIrRuntimeAdapter.ElementId;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
class ExportBuiltinFixture {
    static final String NS="urn:rust-proof:builtins";
    public static void main(String[] args) throws Exception {
        String start="""
            <definitions xmlns="https://www.omg.org/spec/DMN/20240513/MODEL/" id="builtins" name="Builtins" namespace="urn:rust-proof:builtins">
            <itemDefinition id="numbers" name="tNumbers" isCollection="true"><typeRef>number</typeRef></itemDefinition>
            <inputData id="number" name="Number"><variable name="Number" typeRef="number"/></inputData>
            <inputData id="text" name="Text"><variable name="Text" typeRef="string"/></inputData>
            <inputData id="needle" name="Needle"><variable name="Needle" typeRef="string"/></inputData>
            """;
        StringBuilder xml=new StringBuilder(start);
        decision(xml,"absolute","number","<literalExpression><text>abs(Number)</text></literalExpression>");
        decision(xml,"length","number","<literalExpression><text>string length(Text)</text></literalExpression>");
        decision(xml,"contains","boolean","<literalExpression><text>contains(Text, Needle)</text></literalExpression>");
        decision(xml,"context","number","<context><contextEntry><variable name=\"Size\" typeRef=\"number\"/><literalExpression><text>string length(Text)</text></literalExpression></contextEntry><contextEntry><literalExpression><text>abs(Number) + Size</text></literalExpression></contextEntry></context>");
        decision(xml,"filter","tNumbers","<literalExpression><text>[Number, null, -1.00][abs(item) &gt;= 0]</text></literalExpression>");
        decision(xml,"table","number","""
            <decisionTable hitPolicy="FIRST"><input><inputExpression typeRef="number"><text>abs(Number)</text></inputExpression></input><output typeRef="number"/>
            <rule><inputEntry><text>&gt; 0</text></inputEntry><outputEntry><text>abs(Number)</text></outputEntry></rule>
            <rule><inputEntry><text>null</text></inputEntry><outputEntry><text>string length(Text)</text></outputEntry></rule>
            <rule><inputEntry><text>-</text></inputEntry><outputEntry><text>if contains(Text, Needle) then string length(Text) else 0</text></outputEntry></rule></decisionTable>
            """);
        xml.append("</definitions>");
        var verified=new DmnIrScalarProducer().produce(new DmnSource(DmnSourceId.of("memory:/builtins.dmn"),xml.toString().getBytes(StandardCharsets.UTF_8)));
        if(verified.artifact().getSchemaVersion().getMinor()!=9)throw new AssertionError("Expected 0.9");
        var model=new DmnIrRuntimeAdapter().adapt(verified);
        BigDecimal[] numbers={null,new BigDecimal("-12.500"),new BigDecimal("0.000"),new BigDecimal("-1E+12"),new BigDecimal("-1E-4096"),new BigDecimal("-"+"9".repeat(4096))};
        String[] texts={null,"","A\ud83c\udf0de\u0301","\u00df\ud834\udd1e","ababa","\u4f60\u597d"};
        String[] needles={null,"","\ud83c\udf0d","a","e\u0301","\u754c"};
        StringBuilder vectors=new StringBuilder();
        for(int n=0;n<numbers.length;n++)for(int t=0;t<texts.length;t++)for(int k=0;k<needles.length;k++) {
            Map<ElementId,Object> inputs=new HashMap<>();inputs.put(new ElementId(NS,"number"),numbers[n]);inputs.put(new ElementId(NS,"text"),texts[t]);inputs.put(new ElementId(NS,"needle"),needles[k]);
            var values=model.evaluate(inputs);var sorted=new TreeMap<String,String>();values.forEach((id,value)->sorted.put(id.elementId(),encode(value)));
            vectors.append(n).append('\t').append(t).append('\t').append(k).append('\t').append(String.join(";",sorted.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).toList())).append('\n');
        }
        Files.write(Path.of(args[0]),verified.artifact().toByteArray());Files.writeString(Path.of(args[1]),vectors);
    }
    static void decision(StringBuilder xml,String id,String type,String body){
        xml.append("<decision id=\"").append(id).append("\" name=\"").append(id).append("\"><variable name=\"").append(id).append("\" typeRef=\"").append(type).append("\"/>");
        for(String input:List.of("number","text","needle"))xml.append("<informationRequirement><requiredInput href=\"#").append(input).append("\"/></informationRequirement>");
        xml.append(body).append("</decision>");
    }
    static String encode(Object value){if(value==null)return "N";if(value instanceof Boolean b)return b?"T":"F";
        if(value instanceof BigDecimal d)return "D"+d.unscaledValue()+","+d.scale();
        if(value instanceof List<?> list)return "["+String.join("|",list.stream().map(ExportBuiltinFixture::encode).toList())+"]";throw new AssertionError(value);}
}
