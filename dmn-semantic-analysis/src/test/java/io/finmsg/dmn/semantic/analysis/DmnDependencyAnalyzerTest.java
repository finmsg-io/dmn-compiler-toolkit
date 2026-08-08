package io.finmsg.dmn.semantic.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import io.finmsg.dmn.model.BusinessKnowledgeModel;
import io.finmsg.dmn.model.Decision;
import io.finmsg.dmn.model.Definitions;
import io.finmsg.dmn.model.DrgElement;
import io.finmsg.dmn.model.ElementReference;
import io.finmsg.dmn.model.InformationRequirement;
import io.finmsg.dmn.model.KnowledgeRequirement;
import io.finmsg.dmn.model.Node;
import java.util.List;
import org.junit.jupiter.api.Test;

class DmnDependencyAnalyzerTest {

	private final DmnDependencyAnalyzer analyzer = new DmnDependencyAnalyzer();

	@Test
	void ordersDependenciesBeforeConsumers() {
		DrgElement base = bkm("base", "Base");
		DrgElement derived = bkm("derived", "Derived", "base");
		DrgElement first = decision("first", "First", null, "derived");
		DrgElement second = decision("second", "Second", "first", null);
		Definitions model = Definitions.newBuilder().addDrgElements(second).addDrgElements(first)
				.addDrgElements(derived).addDrgElements(base).build();

		DmnDependencyAnalysisResult result = analyzer.analyze(model);

		assertThat(result.diagnostics()).isEmpty();
		assertThat(result.compilationOrder()).extracting(DmnDependencyAnalyzerTest::name).containsExactly("Base",
				"Derived", "First", "Second");
	}

	@Test
	void reportsDecisionCyclesAndSuppressesCompilationOrder() {
		Definitions model = Definitions.newBuilder().addDrgElements(decision("a", "A", "b", null))
				.addDrgElements(decision("b", "B", "a", null)).build();

		DmnDependencyAnalysisResult result = analyzer.analyze(model);

		assertThat(result.compilationOrder()).isEmpty();
		assertThat(result.diagnostics()).extracting(DmnSemanticDiagnostic::code).containsExactly("CYCLIC_DEPENDENCY");
		assertThat(result.diagnostics().getFirst().message()).contains("A", "B");
	}

	@Test
	void reportsSelfReferencingBkm() {
		Definitions model = Definitions.newBuilder().addDrgElements(bkm("self", "Self", "self")).build();

		assertThat(analyzer.analyze(model).diagnostics()).extracting(DmnSemanticDiagnostic::code)
				.containsExactly("CYCLIC_DEPENDENCY");
	}

	@Test
	void reportsDuplicateRequirements() {
		Decision decision = Decision.newBuilder().setNode(Node.newBuilder().setId("consumer").setName("Consumer"))
				.addInformationRequirements(informationRequirement("dependency"))
				.addInformationRequirements(informationRequirement("dependency")).build();
		Definitions model = Definitions.newBuilder().addDrgElements(DrgElement.newBuilder().setDecision(decision))
				.addDrgElements(decision("dependency", "Dependency", null, null)).build();

		DmnDependencyAnalysisResult result = analyzer.analyze(model);

		assertThat(result.diagnostics()).extracting(DmnSemanticDiagnostic::code)
				.containsExactly("DUPLICATE_REQUIREMENT");
		assertThat(result.compilationOrder()).extracting(DmnDependencyAnalyzerTest::name).containsExactly("Dependency",
				"Consumer");
	}

	private static DrgElement decision(String id, String name, String requiredDecision, String requiredBkm) {
		Decision.Builder decision = Decision.newBuilder().setNode(Node.newBuilder().setId(id).setName(name));
		if (requiredDecision != null) {
			decision.addInformationRequirements(informationRequirement(requiredDecision));
		}
		if (requiredBkm != null) {
			decision.addKnowledgeRequirements(knowledgeRequirement(requiredBkm));
		}
		return DrgElement.newBuilder().setDecision(decision).build();
	}

	private static DrgElement bkm(String id, String name, String... requiredBkms) {
		BusinessKnowledgeModel.Builder bkm = BusinessKnowledgeModel.newBuilder()
				.setNode(Node.newBuilder().setId(id).setName(name));
		List.of(requiredBkms).forEach(required -> bkm.addKnowledgeRequirements(knowledgeRequirement(required)));
		return DrgElement.newBuilder().setBusinessKnowledgeModel(bkm).build();
	}

	private static InformationRequirement informationRequirement(String id) {
		return InformationRequirement.newBuilder().setDecision(reference(id)).build();
	}

	private static KnowledgeRequirement knowledgeRequirement(String id) {
		return KnowledgeRequirement.newBuilder().setRequiredKnowledge(reference(id)).build();
	}

	private static ElementReference reference(String id) {
		return ElementReference.newBuilder().setHref("#" + id).build();
	}

	private static String name(DrgElement element) {
		return element.hasDecision()
				? element.getDecision().getNode().getName()
				: element.getBusinessKnowledgeModel().getNode().getName();
	}
}
