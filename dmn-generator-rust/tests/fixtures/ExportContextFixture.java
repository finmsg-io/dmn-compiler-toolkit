import io.finmsg.dmn.compiler.dmnir.DmnIrRuntimeAdapter;
import io.finmsg.dmn.compiler.dmnir.DmnIrRuntimeAdapter.ElementId;
import io.finmsg.dmn.dmnir.codec.DmnIrArtifactReader;
import io.finmsg.dmn.dmnir.proto.v0.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
class ExportContextFixture {
    public static void main(String[] args) throws Exception {
        var reader = new DmnIrArtifactReader();
        var original = reader.read(Files.readAllBytes(Path.of(args[0])));
        var artifact = original.artifact().toBuilder();
        artifact.getSchemaVersionBuilder().setMinor(3);
        artifact.setSemanticsVersion("finmsg-dmnir-local-bindings/0");
        artifact.addRequiredCapabilities("dmnir.local-bindings");
        for (var model : artifact.getModelsBuilderList()) {
            for (var decision : model.getDecisionsBuilderList()) {
                var expression = decision.getExpression();
                var variable = decision.getVariable().toBuilder().clearId().setName("Local").build();
                var reference = expression.toBuilder().clearNode().setLocalReference(
                        DmnIrLocalReference.newBuilder().setBindingId("first")).build();
                var copy = reference.toBuilder().setLocalReference(
                        DmnIrLocalReference.newBuilder().setBindingId("copy")).build();
                var nested = expression.toBuilder().clearNode().setContext(DmnIrContextExpression.newBuilder()
                        .addBindings(DmnIrLocalBinding.newBuilder().setBindingId("shadow")
                                .setVariable(variable).setValue(expression.toBuilder().clearNode()
                                        .setLiteral(DmnIrLiteral.newBuilder().setNullValueValue(0))))
                        .setResult(copy));
                decision.setExpression(expression.toBuilder().clearNode().setContext(DmnIrContextExpression.newBuilder()
                        .addBindings(DmnIrLocalBinding.newBuilder().setBindingId("first")
                                .setVariable(variable).setValue(expression))
                        .addBindings(DmnIrLocalBinding.newBuilder().setBindingId("copy")
                                .setVariable(variable.toBuilder().setName("Copy")).setValue(reference))
                        .setResult(nested)));
            }
        }
        var verified = reader.read(artifact.build().toByteArray());
        var baseline = new DmnIrRuntimeAdapter().adapt(original);
        var contexts = new DmnIrRuntimeAdapter().adapt(verified);
        String ns = "urn:rust-proof:records";
        for (int vector = 0; vector < 5; vector++) {
            Map<String, Object> money = new HashMap<>();
            money.put("Amount", vector == 3 ? null : new BigDecimal("12.500"));
            money.put("Label", "Gr\u00fcezi \ud83c\udf0d");
            money.put("Extra", "retained");
            Map<String, Object> account = new HashMap<>();
            account.put("Balance", vector == 2 ? null : money);
            account.put("Active", true);
            Map<String, Object> applicant = new HashMap<>();
            applicant.put("Account", vector == 1 ? null : account);
            applicant.put("Name", "Example");
            for (boolean use : List.of(false, true)) {
                Map<ElementId, Object> inputs = new HashMap<>();
                inputs.put(new ElementId(ns, "applicant"), vector == 0 ? null : applicant);
                inputs.put(new ElementId(ns, "use"), use);
                if (!baseline.evaluate(inputs).equals(contexts.evaluate(inputs)))
                    throw new AssertionError("Context parity differs");
            }
        }
        Files.write(Path.of(args[1]), verified.artifact().toByteArray());
    }
}
