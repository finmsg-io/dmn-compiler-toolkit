import io.finmsg.dmn.compiler.*;
import io.finmsg.dmn.compiler.dmnir.*;
import io.finmsg.dmn.compiler.dmnir.DmnIrRuntimeAdapter.ElementId;
import io.finmsg.dmn.runtime.DmnEvaluationException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
class ExportCollectionFixture {
private static final String CATALOG = """
			<definitions xmlns="https://www.omg.org/spec/DMN/20240513/MODEL/" id="catalog" name="Catalog" namespace="urn:collections:catalog">
			<itemDefinition id="dates" name="tDates" isCollection="true"><typeRef>date</typeRef></itemDefinition>
			<itemDefinition id="fees" name="tFees" isCollection="true"><typeRef>number</typeRef></itemDefinition>
			<itemDefinition id="permit" name="tPermit">
			<itemComponent name="Expiry"><typeRef>date</typeRef><typeConstraint><text>&gt;= date("2024-01-01")</text></typeConstraint></itemComponent>
			<itemComponent name="Previous Dates"><typeRef>tDates</typeRef></itemComponent>
			<itemComponent name="Previous Fees"><typeRef>tFees</typeRef></itemComponent>
			</itemDefinition></definitions>
			""";
private static final String POLICY = """
			<definitions xmlns="https://www.omg.org/spec/DMN/20240513/MODEL/" xmlns:catalog="urn:collections:catalog"
				id="policy" name="Policy" namespace="urn:collections:policy">
			<import name="catalog" namespace="urn:collections:catalog" locationURI="catalog.dmn" importType="DMN"/>
			<itemDefinition id="applicant-type" name="tApplicant"><itemComponent name="Permit"><typeRef>catalog:tPermit</typeRef></itemComponent></itemDefinition>
			<inputData id="applicant" name="Applicant"><variable name="Applicant" typeRef="tApplicant"/></inputData>
			<inputData id="include" name="Include"><variable name="Include" typeRef="boolean"/></inputData>
			<inputData id="external" name="External"><variable name="External" typeRef="catalog:tFees"/></inputData>
			<decision id="fee" name="Fee"><variable name="Fee" typeRef="number"/>
			<informationRequirement><requiredInput href="#applicant"/></informationRequirement>
			<decisionTable hitPolicy="FIRST"><input><inputExpression typeRef="date"><text>Applicant.Permit.Expiry</text></inputExpression></input>
			<output typeRef="number"><defaultOutputEntry><text>0</text></defaultOutputEntry></output>
			<rule><inputEntry><text>&gt;= date("2024-02-01")</text></inputEntry><outputEntry><text>30.125</text></outputEntry></rule>
			</decisionTable></decision>
			<decision id="history" name="History"><variable name="History" typeRef="catalog:tFees"/>
			<informationRequirement><requiredInput href="#applicant"/></informationRequirement>
			<informationRequirement><requiredInput href="#include"/></informationRequirement>
			<informationRequirement><requiredDecision href="#fee"/></informationRequirement>
			<context><contextEntry><variable name="Prior" typeRef="catalog:tFees"/><literalExpression><text>Applicant.Permit.Previous Fees</text></literalExpression></contextEntry>
			<contextEntry><literalExpression><text>if Include then [Fee, Fee, null, 0.125] else Prior</text></literalExpression></contextEntry></context></decision>
			<decision id="dates" name="Dates"><variable name="Dates" typeRef="catalog:tDates"/>
			<informationRequirement><requiredInput href="#applicant"/></informationRequirement>
			<literalExpression><text>Applicant.Permit.Previous Dates</text></literalExpression></decision>
			<decision id="empty" name="Empty Or Null"><variable name="Empty Or Null" typeRef="catalog:tFees"/>
			<informationRequirement><requiredInput href="#include"/></informationRequirement>
			<literalExpression><text>if Include then [] else null</text></literalExpression></decision>
			<decision id="echo" name="Echo"><variable name="Echo" typeRef="catalog:tFees"/>
			<informationRequirement><requiredInput href="#external"/></informationRequirement>
			<literalExpression><text>External</text></literalExpression></decision></definitions>
			""";
    static final String NS = "urn:collections:policy";
    public static void main(String[] args) throws Exception {
        var catalog = CATALOG.replace("</definitions>", "<itemDefinition id=\"words\" name=\"tWords\" isCollection=\"true\"><typeRef>string</typeRef></itemDefinition><itemDefinition id=\"flags\" name=\"tFlags\" isCollection=\"true\"><typeRef>boolean</typeRef></itemDefinition></definitions>");
        var policy = POLICY.replace("</definitions>", "<decision id=\"words\" name=\"Words\"><variable name=\"Words\" typeRef=\"catalog:tWords\"/><literalExpression><text>[&quot;Gruezi&quot;, null, &quot;Gruezi&quot;]</text></literalExpression></decision><decision id=\"flags\" name=\"Flags\"><variable name=\"Flags\" typeRef=\"catalog:tFlags\"/><literalExpression><text>[true, false, null]</text></literalExpression></decision></definitions>");
        var verified = new DmnIrScalarProducer().produce(source("policy.dmn",policy),new InMemoryDmnModelResolver(List.of(source("catalog.dmn",catalog))));
        if(verified.artifact().getSchemaVersion().getMinor()!=7)throw new AssertionError("Expected 0.7");
        var model=new DmnIrRuntimeAdapter().adapt(verified);
        StringBuilder vectors=new StringBuilder();
        for(int scenario=0;scenario<8;scenario++)for(Boolean include:Arrays.asList(false,true,null)) {
            Map<String,Object> permit=new HashMap<>();
            permit.put("Expiry",LocalDate.of(scenario==5?2023:2024,2,scenario==5?28:29));
            permit.put("Previous Dates",scenario==1?List.of():scenario==2?null:Arrays.asList(LocalDate.of(-400,2,29),null,LocalDate.of(10000,1,1)));
            permit.put("Previous Fees",scenario==1?List.of():scenario==2?null:Arrays.asList(new BigDecimal("10.00"),null,new BigDecimal("-2.5")));
            Map<String,Object> applicant=new HashMap<>();applicant.put("Permit",scenario==3?null:permit);
            Map<ElementId,Object> inputs=new HashMap<>();
            inputs.put(key("applicant"),scenario==4?null:applicant);inputs.put(key("include"),include);
            inputs.put(key("external"),scenario==6?null:scenario==7?List.of():Arrays.asList(new BigDecimal("1.250"),null,new BigDecimal("1.250")));
            String expected;
            try{var values=model.evaluate(inputs);var sorted=new TreeMap<String,String>();values.forEach((k,v)->sorted.put(k.elementId(),encode(v)));expected=String.join(";",sorted.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).toList());}
            catch(DmnEvaluationException e){expected="ERROR";}
            vectors.append(scenario).append('\t').append(include==null?"N":include?"T":"F").append('\t').append(expected).append('\n');
        }
        Files.write(Path.of(args[0]),verified.artifact().toByteArray());Files.writeString(Path.of(args[1]),vectors);
    }
    static DmnSource source(String name,String xml){return new DmnSource(DmnSourceId.of("memory:/"+name),xml.getBytes(StandardCharsets.UTF_8));}
    static ElementId key(String id){return new ElementId(NS,id);}
    static String encode(Object value){
        if(value==null)return "N";if(value instanceof Boolean b)return b?"T":"F";
        if(value instanceof BigDecimal d)return "D"+d.unscaledValue()+","+d.scale();
        if(value instanceof LocalDate d)return "A"+d.getYear()+","+d.getMonthValue()+","+d.getDayOfMonth();
        if(value instanceof String s)return "S"+s;
        if(value instanceof List<?> list)return "["+String.join("|",list.stream().map(ExportCollectionFixture::encode).toList())+"]";
        throw new AssertionError(value.getClass());
    }
}
