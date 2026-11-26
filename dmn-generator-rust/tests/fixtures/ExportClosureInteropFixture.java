import io.finmsg.dmn.compiler.dmnir.*;
import io.finmsg.dmn.compiler.dmnir.DmnIrRuntimeAdapter.ElementId;
import io.finmsg.dmn.runtime.DmnEvaluationException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import io.finmsg.dmn.dmnir.proto.v0.*;
class ExportClosureInteropFixture {
    static final String LIB="urn:collections:catalog";
static final String NS = "urn:collections:policy";
    public static void main(String[] args) throws Exception {
        var reader=new io.finmsg.dmn.dmnir.codec.DmnIrArtifactReader();
        var artifact=reader.read(Files.readAllBytes(Path.of(args[2]))).artifact().toBuilder();
        artifact.getSchemaVersionBuilder().setMinor(11);artifact.setSemanticsVersion("finmsg-dmnir-inline-feel/0");artifact.addRequiredCapabilities("dmnir.inline-feel");
        var root=artifact.getModelsBuilderList().stream().filter(m->m.getNamespace().equals(NS)).findFirst().orElseThrow();
        var catalog=artifact.getModelsBuilderList().stream().filter(m->m.getNamespace().equals(LIB)).findFirst().orElseThrow();
        var subtract=call(LIB,"subtract",scalar(3),List.of(argument(1,local("a",scalar(3))),argument(2,local("shift",scalar(3)))));
        var zero=call(LIB,"subtract",scalar(3),List.of(argument(1,local("d",scalar(3))),argument(2,local("d",scalar(3)))));
        var adjustment=DmnIrClosure.newBuilder().setResultType(scalar(3)).addAllCapturedBindingIds(List.of("a","d"))
            .addParameters(parameter("shift",scalar(3))).setBody(binary(1,subtract,zero)).build();
        var adjusted=closureContext(adjustment,List.of(local("d",scalar(3))),scalar(3));
        root.getBkmsBuilderList().stream().filter(b->b.getIdentity().getId().equals("adjust")).findFirst().orElseThrow().setBody(binary(1,adjusted,number("1")));
        var permitType=recordType(LIB,"permit");var fees=collectionType(LIB,"fees");
        var capturedExpiry=typed(scalar(5)).setFieldAccess(DmnIrFieldAccess.newBuilder().setSource(local("permit-arg",permitType)).setFieldName("Expiry")).build();
        var parameterExpiry=typed(scalar(5)).setFieldAccess(DmnIrFieldAccess.newBuilder().setSource(local("candidate",permitType)).setFieldName("Expiry")).build();
        var expiryBody=typed(scalar(5)).setConditional(DmnIrConditionalExpression.newBuilder().setCondition(bool(true)).setThenExpression(parameterExpiry).setElseExpression(capturedExpiry)).build();
        var expiry=DmnIrClosure.newBuilder().setResultType(scalar(5)).addCapturedBindingIds("permit-arg").addParameters(parameter("candidate",permitType)).setBody(expiryBody).build();
        catalog.getBkmsBuilderList().stream().filter(b->b.getIdentity().getId().equals("get-expiry")).findFirst().orElseThrow().setBody(closureContext(expiry,List.of(local("permit-arg",permitType)),scalar(5)));
        var keepBody=typed(fees).setConditional(DmnIrConditionalExpression.newBuilder().setCondition(bool(true)).setThenExpression(local("other-fees",fees)).setElseExpression(local("fees-arg",fees))).build();
        var keep=DmnIrClosure.newBuilder().setResultType(fees).addCapturedBindingIds("fees-arg").addParameters(parameter("other-fees",fees)).setBody(keepBody).build();
        catalog.getBkmsBuilderList().stream().filter(b->b.getIdentity().getId().equals("keep")).findFirst().orElseThrow().setBody(closureContext(keep,List.of(local("fees-arg",fees)),fees));
        var fee=typed(scalar(3)).setGlobalReference(reference(NS,"fee")).build();
        add(root,"dynamic",scalar(3),call(NS,"adjust",scalar(3),List.of(argument(2,number("2")),argument(1,fee))),List.of(reference(NS,"fee")),List.of(reference(NS,"adjust")));
        artifact.addEvaluationOrder(reference(NS,"dynamic"));
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
    static DmnIrExpression closureContext(DmnIrClosure definition,List<DmnIrExpression> arguments,DmnIrTypeReference type){
        var binding=DmnIrLocalBinding.newBuilder().setBindingId("interop-function").setClosure(DmnIrClosureBinding.newBuilder().setName("Function").setDefinition(definition));
        var invocation=typed(type).setClosureInvocation(DmnIrClosureInvocation.newBuilder().setLocalBindingId("interop-function").addAllArguments(arguments));
        return typed(type).setContext(DmnIrContextExpression.newBuilder().addBindings(binding).setResult(invocation)).build();
    }
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
        if(value instanceof List<?> list)return "["+String.join("|",list.stream().map(ExportClosureInteropFixture::encode).toList())+"]";
        throw new AssertionError(value.getClass());
    }
}
