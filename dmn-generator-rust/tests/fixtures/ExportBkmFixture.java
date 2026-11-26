import io.finmsg.dmn.compiler.dmnir.*;
import io.finmsg.dmn.compiler.dmnir.DmnIrRuntimeAdapter.ElementId;
import io.finmsg.dmn.runtime.DmnEvaluationException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import io.finmsg.dmn.dmnir.proto.v0.*;
class ExportBkmFixture {
    static final String LIB="urn:collections:catalog";
static final String NS = "urn:collections:policy";
    public static void main(String[] args) throws Exception {
        var reader=new io.finmsg.dmn.dmnir.codec.DmnIrArtifactReader();
        var artifact=reader.read(Files.readAllBytes(Path.of(args[3]))).artifact().toBuilder();
        artifact.getSchemaVersionBuilder().setMinor(10);artifact.setSemanticsVersion("finmsg-dmnir-named-feel-bkms/0");
        artifact.addAllRequiredCapabilities(List.of("dmnir.scalar-filters","dmnir.builtin-calls","dmnir.named-feel-bkms"));
        var catalog=artifact.getModelsBuilderList().stream().filter(m->m.getNamespace().equals(LIB)).findFirst().orElseThrow();
        catalog.addBkms(bkm("subtract",scalar(3),List.of(parameter("left",scalar(3)),parameter("right",scalar(3))),binary(2,local("left",scalar(3)),local("right",scalar(3)))));
        var permitType=recordType(LIB,"permit");var fees=collectionType(LIB,"fees");var flags=collectionType(LIB,"flags");
        catalog.addBkms(bkm("get-expiry",scalar(5),List.of(parameter("permit-arg",permitType)),typed(scalar(5)).setFieldAccess(DmnIrFieldAccess.newBuilder().setSource(local("permit-arg",permitType)).setFieldName("Expiry")).build()));
        catalog.addBkms(bkm("keep",fees,List.of(parameter("fees-arg",fees)),local("fees-arg",fees)));
        var filtered=typed(fees).setFilter(DmnIrFilterExpression.newBuilder().setSource(local("has-arg",fees)).setItemBindingId("has-item").setPredicate(bool(true))).build();
        var context=typed(scalar(2)).setContext(DmnIrContextExpression.newBuilder().addBindings(DmnIrLocalBinding.newBuilder().setBindingId("selected").setVariable(variable("Selected",fees).setId("has-local")).setValue(filtered)).setResult(bool(true))).build();
        catalog.addBkms(bkm("has",scalar(2),List.of(parameter("has-arg",fees)),context));
        var recordSource=typed(fees).setFieldAccess(DmnIrFieldAccess.newBuilder().setSource(local("record-arg",permitType)).setFieldName("Previous Fees")).build();
        var recordFiltered=typed(fees).setFilter(DmnIrFilterExpression.newBuilder().setSource(recordSource).setItemBindingId("record-item").setPredicate(bool(true))).build();
        var recordContext=typed(scalar(2)).setContext(DmnIrContextExpression.newBuilder().addBindings(DmnIrLocalBinding.newBuilder().setBindingId("record-selected").setVariable(variable("Selected",fees).setId("has-record-local")).setValue(recordFiltered)).setResult(bool(true))).build();
        catalog.addBkms(bkm("has-record",scalar(2),List.of(parameter("record-arg",permitType)),recordContext));

        var root=artifact.getModelsBuilderList().stream().filter(m->m.getNamespace().equals(NS)).findFirst().orElseThrow();
        var sub=call(LIB,"subtract",scalar(3),List.of(argument(1,local("a",scalar(3))),argument(2,local("d",scalar(3)))));
        root.addBkms(bkm("adjust",scalar(3),List.of(parameter("a",scalar(3)),parameter("d",scalar(3))),binary(1,sub,number("1"))).toBuilder().addKnowledgeDependencies(reference(LIB,"subtract")));
        add(root,"difference",scalar(3),call(NS,"adjust",scalar(3),List.of(argument(2,number("2")),argument(1,number("10")))),List.of(),List.of(reference(NS,"adjust")));
        add(root,"nullable",scalar(3),call(NS,"adjust",scalar(3),List.of(argument(1,nullValue()),argument(2,number("2")))),List.of(),List.of(reference(NS,"adjust")));
        var applicantExpression=typed(recordType(NS,"applicant-type")).setGlobalReference(reference(NS,"applicant")).build();
        var permitValue=typed(permitType).setFieldAccess(DmnIrFieldAccess.newBuilder().setSource(applicantExpression).setFieldName("Permit")).build();
        add(root,"expiry",scalar(5),call(LIB,"get-expiry",scalar(5),List.of(argument(1,permitValue))),List.of(reference(NS,"applicant")),List.of(reference(LIB,"get-expiry")));
        var external=typed(fees).setGlobalReference(reference(NS,"external")).build();var has=call(LIB,"has",scalar(2),List.of(argument(1,external)));
        var deps=List.of(reference(NS,"external"),reference(NS,"include"));var knows=List.of(reference(LIB,"has"),reference(LIB,"keep"));
        add(root,"keep",fees,call(LIB,"keep",fees,List.of(argument(1,external))),deps,knows);
        var includeExpression=typed(scalar(2)).setGlobalReference(reference(NS,"include")).build();
        add(root,"lazy",scalar(2),binary(13,includeExpression,has),deps,knows);
        var source=typed(flags).setList(DmnIrListExpression.newBuilder().addElements(bool(true)).addElements(bool(false)).addElements(nullValue())).build();
        add(root,"predicate",flags,typed(flags).setFilter(DmnIrFilterExpression.newBuilder().setSource(source).setItemBindingId("predicate-item").setPredicate(has)).build(),deps,knows);
        root.addDecisions(DmnIrDecision.newBuilder().setIdentity(identity("default")).setVariable(variable("default",scalar(2))).addAllDependencies(deps).addDependencies(reference(NS,"applicant")).addAllKnowledgeDependencies(knows).addKnowledgeDependencies(reference(LIB,"has-record"))
            .setDecisionTable(DmnIrDecisionTable.newBuilder().setHitPolicyValue(2).addInputs(DmnIrTableInput.newBuilder().setTypeValue(2).setExpression(bool(false)))
                .addOutputs(DmnIrTableOutput.newBuilder().setTypeValue(2).setDefaultExpression(call(LIB,"has-record",scalar(2),List.of(argument(1,permitValue)))))
                .addRules(DmnIrTableRule.newBuilder().addInputEntries(DmnIrUnaryTests.newBuilder().addTests(DmnIrUnaryTest.newBuilder().setComparison(DmnIrTestComparison.newBuilder().setOperatorValue(6).setEndpoint(bool(true))))).addOutputEntries(bool(false)))));
        for(String id:List.of("difference","nullable","expiry","keep","lazy","predicate","default"))artifact.addEvaluationOrder(reference(NS,id));
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
        for(String selected:List.of("lazy","default")) {
            var copy=verified.artifact().toBuilder();
            for(var m:copy.getModelsBuilderList()) {var keep=m.getDecisionsList().stream().filter(d->d.getIdentity().getId().equals(selected)).toList();m.clearDecisions().addAllDecisions(keep);}
            copy.clearEvaluationOrder().addEvaluationOrder(io.finmsg.dmn.dmnir.proto.v0.DmnIrReference.newBuilder().setModelNamespace(NS).setElementId(selected));
            var checked=new io.finmsg.dmn.dmnir.codec.DmnIrArtifactReader().read(copy.build().toByteArray());
            Files.write(Path.of(args[2],"bkms-"+selected+"-0.10.dmnir"),checked.artifact().toByteArray());
        }

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
        if(value instanceof List<?> list)return "["+String.join("|",list.stream().map(ExportBkmFixture::encode).toList())+"]";
        throw new AssertionError(value.getClass());
    }
}
