import io.finmsg.dmn.compiler.DmnSource;
import io.finmsg.dmn.compiler.DmnSourceId;
import io.finmsg.dmn.compiler.InMemoryDmnModelResolver;
import io.finmsg.dmn.compiler.dmnir.DmnIrRuntimeAdapter;
import io.finmsg.dmn.compiler.dmnir.DmnIrRuntimeAdapter.ElementId;
import io.finmsg.dmn.compiler.dmnir.DmnIrScalarProducer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

class ExportRecordFixture {
    private static final String NS = "urn:rust-proof:records";
    private static final String LIB = """
            <definitions xmlns="https://www.omg.org/spec/DMN/20240513/MODEL/"
              namespace="urn:rust-proof:record-types" id="types" name="Types">
              <itemDefinition id="money" name="tMoney">
                <itemComponent id="money-amount" name="Amount"><typeRef>number</typeRef></itemComponent>
                <itemComponent name="Label"><typeRef>string</typeRef></itemComponent>
              </itemDefinition>
              <itemDefinition id="account" name="tAccount">
                <itemComponent name="Balance"><typeRef>tMoney</typeRef></itemComponent>
                <itemComponent name="Active"><typeRef>boolean</typeRef></itemComponent>
              </itemDefinition>
            </definitions>
            """;
    private static final String ROOT = """
            <definitions xmlns="https://www.omg.org/spec/DMN/20240513/MODEL/"
              xmlns:lib="urn:rust-proof:record-types"
              namespace="urn:rust-proof:records" id="records" name="Records">
              <import name="Lib" namespace="urn:rust-proof:record-types" locationURI="types.dmn" importType="DMN"/>
              <itemDefinition id="applicant-type" name="tApplicant">
                <itemComponent name="Account"><typeRef>lib:tAccount</typeRef></itemComponent>
                <itemComponent name="Name"><typeRef>string</typeRef></itemComponent>
              </itemDefinition>
              <inputData id="applicant" name="Applicant"><variable name="Applicant" typeRef="tApplicant"/></inputData>
              <inputData id="use" name="Use"><variable name="Use" typeRef="boolean"/></inputData>
              <decision id="balance" name="Balance"><variable name="Balance" typeRef="lib:tMoney"/>
                <informationRequirement><requiredInput href="#applicant"/></informationRequirement>
                <literalExpression><text>Applicant.Account.Balance</text></literalExpression></decision>
              <decision id="amount" name="Amount"><variable name="Amount" typeRef="number"/>
                <informationRequirement><requiredDecision href="#balance"/></informationRequirement>
                <literalExpression><text>Balance.Amount</text></literalExpression></decision>
              <decision id="chosen" name="Chosen"><variable name="Chosen" typeRef="lib:tMoney"/>
                <informationRequirement><requiredDecision href="#balance"/></informationRequirement>
                <informationRequirement><requiredInput href="#use"/></informationRequirement>
                <literalExpression><text>if Use then Balance else null</text></literalExpression></decision>
              <decision id="selected" name="Selected"><variable name="Selected" typeRef="number"/>
                <informationRequirement><requiredDecision href="#chosen"/></informationRequirement>
                <literalExpression><text>Chosen.Amount</text></literalExpression></decision>
              <decision id="adjusted" name="Adjusted"><variable name="Adjusted" typeRef="number"/>
                <informationRequirement><requiredDecision href="#amount"/></informationRequirement>
                <literalExpression><text>Amount + 1.25</text></literalExpression></decision>
            </definitions>
            """;

    public static void main(String[] args) throws Exception {
        var artifact = new DmnIrScalarProducer().produce(source("root.dmn", ROOT),
                new InMemoryDmnModelResolver(List.of(source("types.dmn", LIB))));
        if (artifact.artifact().getSchemaVersion().getMinor() != 2) {
            throw new AssertionError("Expected schema 0.2");
        }
        var model = new DmnIrRuntimeAdapter().adapt(artifact);
        for (int vector = 0; vector < 5; vector++) {
            var amount = vector == 3 ? null : new BigDecimal("12.500");
            var money = record("Amount", amount, "Label", "Grüezi 🌍", "Extra", "retained");
            Object balance = vector == 2 ? null : money;
            Object account = vector == 1 ? null : record("Balance", balance, "Active", true);
            Object applicant = vector == 0 ? null : record("Account", account, "Name", "Example");
            for (boolean use : List.of(false, true)) {
                Map<ElementId, Object> inputs = new HashMap<>();
                inputs.put(key("applicant"), applicant);
                inputs.put(key("use"), use);
                var values = model.evaluate(inputs);
                Object expectedBalance = vector <= 2 ? null : money;
                BigDecimal expectedAmount = vector <= 3 ? null : amount;
                expect(values, "balance", expectedBalance);
                expect(values, "amount", expectedAmount);
                expect(values, "chosen", use ? expectedBalance : null);
                expect(values, "selected", use ? expectedAmount : null);
                expect(values, "adjusted", expectedAmount == null ? null : expectedAmount.add(new BigDecimal("1.25")));
            }
        }
        Files.write(Path.of(args[0]), artifact.artifact().toByteArray());
    }

    private static Map<String, Object> record(Object... fields) {
        var result = new HashMap<String, Object>();
        for (int index = 0; index < fields.length; index += 2) {
            result.put((String) fields[index], fields[index + 1]);
        }
        return result;
    }

    private static DmnSource source(String name, String xml) {
        return new DmnSource(DmnSourceId.of("memory:/" + name), xml.getBytes(StandardCharsets.UTF_8));
    }

    private static ElementId key(String id) {
        return new ElementId(NS, id);
    }

    private static void expect(Map<ElementId, Object> values, String id, Object expected) {
        if (!values.containsKey(key(id)) || !Objects.equals(values.get(key(id)), expected)) {
            throw new AssertionError(id + ": typed record parity differs");
        }
    }
}
