import io.finmsg.dmn.dmnir.proto.v0.*;
import io.finmsg.dmn.dmnir.codec.DmnIrArtifactReader;
import io.finmsg.dmn.compiler.dmnir.DmnIrRuntimeAdapter;
import java.math.*;
import java.nio.file.*;
import java.util.*;
class ExportPowerFixture {
    public static void main(String[] args)throws Exception {
        String ns="urn:rust-proof:powers";
        var artifact=DmnIrArtifact.newBuilder().setSchemaVersion(DmnIrSchemaVersion.newBuilder().setMajor(0).setMinor(1)).setSemanticsVersion("finmsg-dmnir-scalar-expressions/0")
            .addRequiredCapabilities("dmnir.scalar").addRequiredCapabilities("dmnir.scalar-expressions").setRootModelNamespace(ns);
        var model=DmnIrModel.newBuilder().setNamespace(ns).setIdentity(DmnIrElementIdentity.newBuilder().setId("powers").setName("Powers"));
        int count=0;
        for(String base:List.of("-9007199254740992","-10000000","-12","-2","-1","0","1","2","3","10","1000","9999999","10000000","9007199254740991","9007199254740992","2.00","-2.00","1E+3","0.00"))
            for(String exponent:List.of("0","1","2","3.0","4","5","7","8","10","16","31","53")) {
                var b=new BigDecimal(base);var e=new BigDecimal(exponent);
                if(b.toBigIntegerExact().pow(e.intValueExact()).abs().compareTo(BigInteger.ONE.shiftLeft(53))>0)continue;
                String id=String.format("power-%03d",count++);
                model.addDecisions(DmnIrDecision.newBuilder().setIdentity(DmnIrElementIdentity.newBuilder().setId(id).setName(id)).setVariable(DmnIrVariable.newBuilder().setName(id).setTypeValue(3))
                    .setExpression(DmnIrExpression.newBuilder().setTypeValue(3).setBinary(DmnIrBinaryExpression.newBuilder().setOperatorValue(5).setLeft(literal(b)).setRight(literal(e)))));
                artifact.addEvaluationOrder(DmnIrReference.newBuilder().setModelNamespace(ns).setElementId(id));
            }
        artifact.addModels(model);
        var verified=new DmnIrArtifactReader().read(artifact.build().toByteArray());
        var values=new DmnIrRuntimeAdapter().adapt(verified).evaluate(Map.of());
        StringBuilder vectors=new StringBuilder();var sorted=new TreeMap<String,BigDecimal>();values.forEach((id,value)->sorted.put(id.elementId(),(BigDecimal)value));
        sorted.forEach((id,value)->vectors.append(id).append('\t').append(value.unscaledValue()).append('\t').append(value.scale()).append('\n'));
        Files.write(Path.of(args[0]),verified.artifact().toByteArray());Files.writeString(Path.of(args[1]),vectors);
        System.out.println("Verified "+count+" Java power results");
    }
    static DmnIrExpression literal(BigDecimal value){return DmnIrExpression.newBuilder().setTypeValue(3).setLiteral(DmnIrLiteral.newBuilder().setNumberValue(DmnIrDecimal.newBuilder().setUnscaledValue(value.unscaledValue().toString()).setScale(value.scale()))).build();}
}
