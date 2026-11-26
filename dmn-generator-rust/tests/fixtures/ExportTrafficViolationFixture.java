import io.finmsg.dmn.compiler.*;
import io.finmsg.dmn.compiler.dmnir.*;
import io.finmsg.dmn.compiler.dmnir.DmnIrRuntimeAdapter.ElementId;
import io.finmsg.dmn.runtime.DmnEvaluationException;
import io.finmsg.dmn.runtime.RuntimeContextValue;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
class ExportTrafficViolationFixture {
    public static void main(String[] args) throws Exception {
        var source=new DmnSource(DmnSourceId.of(Path.of(args[0]).toUri().toString()),Files.readAllBytes(Path.of(args[0])));
        var verified=new DmnIrScalarProducer().produce(source);
        var model=new DmnIrRuntimeAdapter().adapt(verified);
        var root=verified.artifact().getModelsList().stream().filter(m->m.getNamespace().equals(verified.artifact().getRootModelNamespace())).findFirst().orElseThrow();
        var inputIds=new HashMap<String,ElementId>();root.getInputsList().forEach(i->inputIds.put(i.getVariable().getName(),new ElementId(root.getNamespace(),i.getIdentity().getId())));
        var names=new HashMap<ElementId,String>();root.getDecisionsList().forEach(d->names.put(new ElementId(root.getNamespace(),d.getIdentity().getId()),d.getVariable().getName().equals("Fine")?"fine":"suspend"));
        String[] types={"speed","parking","driving under the influence","other",null};
        String[] margins={"-1","0","9.999","10","29.999","30","30.001"};
        String[] points={"null","0","12","13","15","16","17","19","20"};
        StringBuilder vectors=new StringBuilder();
        for(int type=0;type<types.length;type++)for(String margin:margins)for(String score:points)
            vector(model,inputIds,names,vectors,type,types[type],margin,score,0);
        for(int scenario=1;scenario<=6;scenario++)vector(model,inputIds,names,vectors,0,"speed","10","17",scenario);
        Files.write(Path.of(args[1]),verified.artifact().toByteArray());Files.writeString(Path.of(args[2]),vectors);
    }
    static void vector(DmnIrRuntimeAdapter.AdaptedModel model,Map<String,ElementId> ids,Map<ElementId,String> names,StringBuilder vectors,int type,String kind,String margin,String score,int scenario) {
        Map<String,Object> driver=new HashMap<>();driver.put("Name","Example");driver.put("Age",new BigDecimal("40"));driver.put("State","Example");driver.put("City","Example");driver.put("Points",score.equals("null")||scenario==6?null:new BigDecimal(score));
        Map<String,Object> violation=new HashMap<>();violation.put("Code","example");violation.put("Date",scenario==3?null:LocalDate.of(2024,1,1));violation.put("Type",kind);violation.put("SpeedLimit",scenario==5?null:new BigDecimal("50"));violation.put("ActualSpeed",scenario==4?null:new BigDecimal("50").add(new BigDecimal(margin)));
        Map<ElementId,Object> inputs=new HashMap<>();inputs.put(ids.get("Driver"),scenario==1?null:driver);inputs.put(ids.get("Violation"),scenario==2?null:violation);
        String expected;
        try {var result=model.evaluate(inputs);var sorted=new TreeMap<String,String>();result.forEach((id,value)->sorted.put(names.get(id),encode(value)));expected=String.join(";",sorted.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).toList());}
        catch(DmnEvaluationException e){expected="ERROR";}
        vectors.append(type).append('\t').append(margin).append('\t').append(score).append('\t').append(scenario).append('\t').append(expected).append('\n');
    }
    static String encode(Object value){
        if(value==null)return "N";if(value instanceof BigDecimal d)return "D"+d.unscaledValue()+","+d.scale();if(value instanceof String s)return "S"+s;
        if(value instanceof RuntimeContextValue c)return encode(c.namedFields());
        if(value instanceof Map<?,?> m){var sorted=new TreeMap<String,String>();m.forEach((k,v)->sorted.put(k.toString(),encode(v)));return "R"+String.join("|",sorted.entrySet().stream().map(e->e.getKey()+":"+e.getValue()).toList());}throw new AssertionError(value);
    }
}
