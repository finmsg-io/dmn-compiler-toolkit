import io.finmsg.dmn.compiler.dmnir.*;
import io.finmsg.dmn.compiler.dmnir.DmnIrRuntimeAdapter.ElementId;
import io.finmsg.dmn.runtime.DmnEvaluationException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import io.finmsg.dmn.dmnir.proto.v0.*;
class ExportClosureFixture {
    static final String LIB="urn:collections:catalog";
static final String NS = "urn:collections:policy";
    public static void main(String[] args) throws Exception {
        var reader=new io.finmsg.dmn.dmnir.codec.DmnIrArtifactReader();
        var base=reader.read(Files.readAllBytes(Path.of(args[2])));
        var artifact=base.artifact().toBuilder();artifact.getSchemaVersionBuilder().setMinor(11);
        artifact.setSemanticsVersion("finmsg-dmnir-inline-feel/0");artifact.addRequiredCapabilities("dmnir.inline-feel");
        for(var m:artifact.getModelsBuilderList())for(var d:m.getDecisionsBuilderList()) {
            if(!d.hasExpression())continue;
            var original=d.getExpression();var type=typeOf(original);
            var captured=DmnIrLocalBinding.newBuilder().setBindingId("closure-captured").setVariable(variable("Captured",type)).setValue(original);
            var definition=DmnIrClosure.newBuilder().setResultType(type).addCapturedBindingIds("closure-captured")
                .addParameters(parameter("closure-parameter",scalar(3))).setBody(local("closure-captured",type));
            var function=DmnIrLocalBinding.newBuilder().setBindingId("closure-function").setClosure(DmnIrClosureBinding.newBuilder().setName("Function").setDefinition(definition));
            var call=typed(type).setClosureInvocation(DmnIrClosureInvocation.newBuilder().setLocalBindingId("closure-function").addArguments(number("2"))).build();
            var shadow=DmnIrLocalBinding.newBuilder().setBindingId("closure-shadow").setVariable(variable("Captured",type)).setValue(nullValue());
            var inner=typed(type).setContext(DmnIrContextExpression.newBuilder().addBindings(shadow).setResult(call)).build();
            var called=DmnIrLocalBinding.newBuilder().setBindingId("closure-called").setVariable(variable("Called",type)).setValue(inner);
            var inline=typed(type).setClosureInvocation(DmnIrClosureInvocation.newBuilder().setInline(DmnIrClosure.newBuilder().setResultType(type)
                .addCapturedBindingIds("closure-called").setBody(local("closure-called",type)))).build();
            d.setExpression(typed(type).setContext(DmnIrContextExpression.newBuilder().addBindings(captured).addBindings(function).addBindings(called).setResult(inline)));
        }
        var verified=reader.read(artifact.build().toByteArray());
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
    static DmnIrTypeReference typeOf(DmnIrExpression e){if(e.hasRecordType())return DmnIrTypeReference.newBuilder().setRecordType(e.getRecordType()).build();if(e.hasCollectionType())return DmnIrTypeReference.newBuilder().setCollectionType(e.getCollectionType()).build();return scalar(e.getTypeValue());}
    static DmnIrReference reference(String ns,String id){return DmnIrReference.newBuilder().setModelNamespace(ns).setElementId(id).build();}
    static DmnIrElementIdentity identity(String id){return DmnIrElementIdentity.newBuilder().setId(id).setName(id).build();}
    static DmnIrTypeReference scalar(int type){return DmnIrTypeReference.newBuilder().setScalarTypeValue(type).build();}
    static DmnIrTypeReference recordType(String ns,String id){return DmnIrTypeReference.newBuilder().setRecordType(reference(ns,id)).build();}
    static DmnIrTypeReference collectionType(String ns,String id){return DmnIrTypeReference.newBuilder().setCollectionType(reference(ns,id)).build();}
    static DmnIrExpression.Builder typed(DmnIrTypeReference type){var e=DmnIrExpression.newBuilder();switch(type.getKindCase()){case SCALAR_TYPE->e.setType(type.getScalarType());case RECORD_TYPE->e.setRecordType(type.getRecordType());case COLLECTION_TYPE->e.setCollectionType(type.getCollectionType());default->throw new AssertionError();}return e;}
    static DmnIrVariable.Builder variable(String name,DmnIrTypeReference type){var v=DmnIrVariable.newBuilder().setName(name);switch(type.getKindCase()){case SCALAR_TYPE->v.setType(type.getScalarType());case RECORD_TYPE->v.setRecordType(type.getRecordType());case COLLECTION_TYPE->v.setCollectionType(type.getCollectionType());default->throw new AssertionError();}return v;}
    static DmnIrBkmParameter parameter(String id,DmnIrTypeReference type){return DmnIrBkmParameter.newBuilder().setBindingId(id).setVariable(variable(id,type)).build();}
    static DmnIrBkm bkm(String id,DmnIrTypeReference type,List<DmnIrBkmParameter> params,DmnIrExpression body){return DmnIrBkm.newBuilder().setIdentity(identity(id)).setVariable(variable(id,type)).addAllParameters(params).setBody(body).build();}
    static DmnIrExpression local(String id,DmnIrTypeReference type){return typed(type).setLocalReference(DmnIrLocalReference.newBuilder().setBindingId(id)).build();}
    static DmnIrExpression bool(boolean value){return typed(scalar(2)).setLiteral(DmnIrLiteral.newBuilder().setBooleanValue(value)).build();}
    static DmnIrExpression nullValue(){return typed(scalar(1)).setLiteral(DmnIrLiteral.newBuilder().setNullValueValue(0)).build();}
    static DmnIrExpression number(String value){return typed(scalar(3)).setLiteral(DmnIrLiteral.newBuilder().setNumberValue(DmnIrDecimal.newBuilder().setUnscaledValue(value).setScale(0))).build();}
    static DmnIrExpression binary(int op,DmnIrExpression a,DmnIrExpression b){return typed(scalar(op==13?2:3)).setBinary(DmnIrBinaryExpression.newBuilder().setOperatorValue(op).setLeft(a).setRight(b)).build();}
    static DmnIrBkmArgument argument(int position,DmnIrExpression value){return DmnIrBkmArgument.newBuilder().setParameterPosition(position).setExpression(value).build();}
    static DmnIrExpression call(String ns,String id,DmnIrTypeReference type,List<DmnIrBkmArgument> arguments){return typed(type).setBkmCall(DmnIrBkmCall.newBuilder().setTarget(reference(ns,id)).addAllArguments(arguments)).build();}
    static void add(DmnIrModel.Builder model,String id,DmnIrTypeReference type,DmnIrExpression expression,List<DmnIrReference> deps,List<DmnIrReference> knowledge){model.addDecisions(DmnIrDecision.newBuilder().setIdentity(identity(id)).setVariable(variable(id,type)).setExpression(expression).addAllDependencies(deps).addAllKnowledgeDependencies(knowledge));}
    static ElementId key(String id){return new ElementId(NS,id);}
    static String encode(Object value){
        if(value==null)return "N";if(value instanceof Boolean b)return b?"T":"F";
        if(value instanceof BigDecimal d)return "D"+d.unscaledValue()+","+d.scale();
        if(value instanceof LocalDate d)return "A"+d.getYear()+","+d.getMonthValue()+","+d.getDayOfMonth();
        if(value instanceof String s)return "S"+s;
        if(value instanceof List<?> list)return "["+String.join("|",list.stream().map(ExportClosureFixture::encode).toList())+"]";
        throw new AssertionError(value.getClass());
    }
}
