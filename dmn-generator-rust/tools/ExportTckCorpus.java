import io.finmsg.dmn.compiler.*;
import io.finmsg.dmn.compiler.dmnir.*;
import io.finmsg.dmn.dmnir.proto.v0.*;
import io.finmsg.dmn.tck.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** Exports every pinned official case; unsupported cases remain in the ledger. */
class ExportTckCorpus {
    record Group(String id,DmnIrArtifact artifact,String failure,String diagnostic) {}
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]),output=Path.of(args[1]);Files.createDirectory(output);
        String filter = args.length > 2 ? args[2] : null;
        var inventory=new TckCatalogueDiscovery().discover(root,List.of("compliance-level-2","compliance-level-3"));
        if(filter == null && inventory.decodedCaseCount()!=3407)throw new IllegalStateException("Unexpected pinned denominator: "+inventory.decodedCaseCount());
        if(inventory.entries().stream().anyMatch(e->!e.executable()))throw new IllegalStateException("Invalid catalogue entries: "+inventory.entries().stream().filter(e->!e.executable()).map(e->e.id()+": "+e.invalidReason()).toList());
        Map<String,Group> groups=new LinkedHashMap<>();
        StringBuilder manifest=new StringBuilder("entryId\tcaseId\tstatus\tgroup\tdiagnostic\n");
        int index=0,ready=0;
        for(var entry:inventory.entries()) {
            if (filter != null && !entry.id().contains(filter)) continue;
            for(var test:entry.testCases()) {
                String status="READY",diagnostic="",groupId="";
                if(test.invocableName().isPresent()) { status="INVOCATION_UNSUPPORTED";diagnostic="Harness needs direct BKM/service invocation mapping"; }
                else try {
                    String cacheKey=entry.id()+"|"+String.join("|",new TreeSet<>(test.expectedResults().keySet()));
                    Group group=groups.get(cacheKey);
                    if(group==null) {
                        groupId=String.format("g%04d",groups.size());
                        try {
                            List<DmnSource> sources=new ArrayList<>();
                            Path model=entry.rootDmn().orElseThrow();
                            var original=new DmnSource(new DmnSourceId(model.toUri()),Files.readAllBytes(model));
                            sources.add(new TckDmnDecisionPruner().prune(original,test.expectedResults().keySet()));
                            for(Path path:entry.dmnFiles())if(!path.equals(model))sources.add(new DmnSource(new DmnSourceId(path.toUri()),Files.readAllBytes(path)));
                            var verified=new DmnIrScalarProducer().produce(sources.getFirst(),new InMemoryDmnModelResolver(sources.subList(1,sources.size())));
                            Path dir=output.resolve(groupId);Files.createDirectory(dir);Files.write(dir.resolve("artifact.dmnir"),verified.artifact().toByteArray());
                            group=new Group(groupId,verified.artifact(),null,"");
                        } catch(DmnIrProductionException failure) {
                            String location=Arrays.stream(failure.getStackTrace())
                                .filter(frame->frame.getClassName().startsWith("io.finmsg.dmn.compiler.dmnir."))
                                .filter(frame->!frame.getMethodName().equals("unsupported"))
                                .findFirst().map(Object::toString).orElse("unknown location");
                            group=new Group(groupId,null,"EXPORT_"+failure.category(),failure.getMessage()+" "+failure.diagnostics()+" at "+location+(failure.getCause()==null?"":" cause: "+failure.getCause()));
                        } catch(Exception failure) { group=new Group(groupId,null,"EXPORT_ERROR",failure.toString()); }
                        groups.put(cacheKey,group);
                    }
                    groupId=group.id();
                    if(group.failure()!=null){status=group.failure();diagnostic=group.diagnostic();}
                    else {
                        String source=renderCase(index,entry.id()+"#"+test.id(),test,group.artifact());
                        Files.writeString(output.resolve(group.id()).resolve("cases.rs"),source,StandardOpenOption.CREATE,StandardOpenOption.APPEND);ready++;
                    }
                } catch(UnsupportedOperationException failure) {status="VALUE_UNSUPPORTED";diagnostic=failure.getMessage();}
                catch(Exception failure) {status="HARNESS_ERROR";diagnostic=failure.toString();}
                manifest.append(clean(entry.id())).append('\t').append(clean(test.id())).append('\t').append(status).append('\t').append(groupId).append('\t').append(clean(diagnostic)).append('\n');
                index++;
            }
            System.out.println("Exported ledger "+index+"/"+inventory.decodedCaseCount()+", Rust-ready "+ready);
        }
        Files.writeString(output.resolve("manifest.tsv"),manifest);
        System.out.println("COMPLETE cases="+index+" ready="+ready+" groups="+groups.size());
    }
    static String renderCase(int index,String key,TckTestCase test,DmnIrArtifact artifact) {
        String ns=artifact.getRootModelNamespace();
        Map<String,String> aliases=new HashMap<>();aliases.put(ns,"");
        var root=artifact.getModelsList().stream().filter(m->m.getNamespace().equals(ns)).findFirst().orElseThrow();
        root.getImportsList().forEach(i->aliases.put(i.getModelNamespace(),i.getName()+"."));
        StringBuilder input=new StringBuilder("std::collections::BTreeMap::from([");
        Map<String,String> results=new HashMap<>();
        for(var model:artifact.getModelsList()) {
            String prefix=aliases.get(model.getNamespace());
            if(prefix==null&&!model.getInputsList().isEmpty())throw new UnsupportedOperationException("Transitive input alias not mapped");
            for(var declaration:model.getInputsList()) {
                String inputName=declaration.getVariable().getName();
                String declaredNamespace=test.inputNamespaces().get(inputName);
                var value=declaredNamespace==null?test.inputs().get(prefix+inputName):
                    declaredNamespace.equals(model.getNamespace())?test.inputs().get(inputName):null;
                input.append("((").append(q(model.getNamespace())).append(',').append(q(declaration.getIdentity().getId())).append("),").append(value==null?"Value::Null":value(value.runtimeValue())).append("),");
            }
            if(prefix!=null)for(var decision:model.getDecisionsList())results.put(prefix+decision.getVariable().getName(),"("+q(model.getNamespace())+","+q(decision.getIdentity().getId())+")");
        }
        input.append("])");
        StringBuilder checks=new StringBuilder();
        for(var expected:new TreeMap<>(test.expectedResults()).entrySet()) {
            String address=results.get(expected.getKey());
            if(address==null)throw new UnsupportedOperationException("Expected decision not mapped: "+expected.getKey());
            String expectedValue=test.expectedErrorResults().contains(expected.getKey())?"Value::Null":value(expected.getValue().runtimeValue());
            checks.append("assert!(matches!(actual.get(&").append(address).append("),Some(value) if equivalent(value,&").append(expectedValue).append(")), ").append(q("Expected result differs: "+key+"/"+expected.getKey()+"; actual={:?}")).append(",actual.get(&").append(address).append("));");
        }
        boolean allErrors=!test.expectedErrorResults().isEmpty()&&test.expectedErrorResults().containsAll(test.expectedResults().keySet());
        return "#[test] fn case_"+String.format("%05d",index)+"() { let inputs="+input+"; match std::panic::catch_unwind(|| evaluate(&inputs)) { Ok(Ok(actual))=>{"+checks+"}, Ok(Err(error))=>{if !"+allErrors+" {println!(\"RUST_TCK_EXECUTION_ERROR: {}\",error);panic!(\"unexpected evaluation error\");}},Err(_)=>{println!(\"RUST_TCK_EXECUTION_ERROR: panic\");panic!(\"evaluation panicked\");} } }\n";
    }
    static String value(Object value) {
        if(value==null)return "Value::Null";
        if(value instanceof BigDecimal d)return "Value::Number { coefficient:"+q(d.unscaledValue().toString())+".into(),scale:"+d.scale()+"}";
        if(value instanceof Boolean b)return "Value::Boolean("+b+")";
        if(value instanceof String s)return "Value::Text("+q(s)+".into())";
        if(value instanceof LocalDate d)return "Value::Date {year:"+d.getYear()+",month:"+d.getMonthValue()+",day:"+d.getDayOfMonth()+"}";
        if(value instanceof List<?> list)return "Value::List(vec!["+list.stream().map(ExportTckCorpus::value).collect(Collectors.joining(","))+"])";
        if(value instanceof Map<?,?> map)return "Value::Record(std::collections::BTreeMap::from(["+map.entrySet().stream().sorted(Comparator.comparing(e->e.getKey().toString())).map(e->"("+q(e.getKey().toString())+".into(),"+value(e.getValue())+")").collect(Collectors.joining(","))+ "]))";
        throw new UnsupportedOperationException("TCK value not represented by Rust Value: "+value.getClass().getSimpleName());
    }
    static String q(String value) {
        StringBuilder out=new StringBuilder("\"");
        value.codePoints().forEach(c->{switch(c){case '"'->out.append("\\\"");case '\\'->out.append("\\\\");case '\n'->out.append("\\n");case '\r'->out.append("\\r");case '\t'->out.append("\\t");default->{if(c<32||c==127)out.append("\\u{").append(Integer.toHexString(c)).append('}');else out.appendCodePoint(c);}}});
        return out.append('"').toString();
    }
    static String clean(String value){return String.valueOf(value).replace('\t',' ').replace('\r',' ').replace('\n',' ');}
}
