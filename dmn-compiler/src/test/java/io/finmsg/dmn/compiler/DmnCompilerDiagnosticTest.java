package io.finmsg.dmn.compiler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.finmsg.dmn.model.Definitions;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DmnCompilerDiagnosticTest {

  private static final DmnSourceId ROOT_ID = DmnSourceId.of("memory:/models/root.dmn");

  @Test
  void retainsExplicitlyAbsentModelAndImportIdentity() {
    DmnDiagnosticOrigin origin = DmnDiagnosticOrigin.source(ROOT_ID);
    DmnCompilerDiagnostic diagnostic = diagnostic(
        DmnDiagnosticSeverity.WARNING, DmnCompilerPhase.XML_FRONTEND, "DMN-TEST", origin);

    assertThat(diagnostic.origin().modelIdentity()).isEmpty();
    assertThat(diagnostic.origin().importIndex()).isEmpty();
    assertThat(diagnostic.importRequest()).isEmpty();
  }

  @Test
  void normalizesRelatedIdentitiesAndMakesCollectionsImmutable() {
    DmnSourceId first = DmnSourceId.of("memory:/models/a.dmn");
    DmnSourceId second = DmnSourceId.of("memory:/models/b.dmn");
    DmnCompilerDiagnostic diagnostic = new DmnCompilerDiagnostic(
        DmnDiagnosticSeverity.ERROR,
        DmnCompilerPhase.SOURCE_RESOLUTION,
        DmnDiagnosticCodes.IMPORT_AMBIGUOUS,
        "ambiguous",
        DmnDiagnosticOrigin.source(ROOT_ID),
        Optional.empty(),
        List.of(second, first, second),
        List.of());

    assertThat(diagnostic.relatedSourceIds()).containsExactly(first, second);
    assertThatThrownBy(() -> diagnostic.relatedSourceIds().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void rejectsInvalidRequiredFieldsOriginsAndCyclePaths() {
    DmnDiagnosticOrigin origin = DmnDiagnosticOrigin.source(ROOT_ID);

    assertThatIllegalArgumentException().isThrownBy(() -> new DmnCompilerDiagnostic(
        DmnDiagnosticSeverity.ERROR, DmnCompilerPhase.SOURCE_RESOLUTION, " ", "message",
        origin, Optional.empty(), List.of(), List.of()));
    assertThatIllegalArgumentException().isThrownBy(() -> new DmnCompilerDiagnostic(
        DmnDiagnosticSeverity.ERROR, DmnCompilerPhase.SOURCE_RESOLUTION, "DMN-TEST", " ",
        origin, Optional.empty(), List.of(), List.of()));
    assertThatNullPointerException().isThrownBy(() -> new DmnCompilerDiagnostic(
        null, DmnCompilerPhase.SOURCE_RESOLUTION, "DMN-TEST", "message",
        origin, Optional.empty(), List.of(), List.of()));
    assertThatIllegalArgumentException().isThrownBy(() -> new DmnDiagnosticOrigin(
        ROOT_ID, Optional.empty(), Optional.of(-1)));
    assertThatIllegalArgumentException().isThrownBy(() -> new DmnCompilerDiagnostic(
        DmnDiagnosticSeverity.ERROR,
        DmnCompilerPhase.SOURCE_RESOLUTION,
        DmnDiagnosticCodes.IMPORT_CYCLE,
        "cycle",
        origin,
        Optional.empty(),
        List.of(),
        List.of(ROOT_ID, DmnSourceId.of("memory:/models/other.dmn"))));
    assertThatIllegalArgumentException().isThrownBy(() -> new DmnCompilerDiagnostic(
        DmnDiagnosticSeverity.ERROR, DmnCompilerPhase.SOURCE_RESOLUTION, "DMN-TEST", "message",
        origin, Optional.empty(), List.of(), List.of(ROOT_ID, ROOT_ID)));
  }

  @Test
  void ordersDiagnosticsByTheDocumentedComparator() {
    DmnSourceId earlierSource = DmnSourceId.of("memory:/models/a.dmn");
    DmnDiagnosticOrigin absentIndex = DmnDiagnosticOrigin.source(earlierSource);
    DmnDiagnosticOrigin presentIndex = new DmnDiagnosticOrigin(
        earlierSource, Optional.empty(), Optional.of(0));
    DmnCompilerDiagnostic first = diagnostic(
        DmnDiagnosticSeverity.WARNING, DmnCompilerPhase.XML_FRONTEND, "DMN-Z", absentIndex);
    DmnCompilerDiagnostic second = diagnostic(
        DmnDiagnosticSeverity.INFO, DmnCompilerPhase.SOURCE_RESOLUTION, "DMN-Z", presentIndex);
    DmnCompilerDiagnostic third = diagnostic(
        DmnDiagnosticSeverity.ERROR, DmnCompilerPhase.SOURCE_RESOLUTION, "DMN-A", presentIndex);
    List<DmnCompilerDiagnostic> reordered = new ArrayList<>(List.of(third, first, second));

    reordered.sort(DmnCompilerDiagnostic.ORDER);

    assertThat(reordered).containsExactly(first, second, third);
  }

  @Test
  void resultValidityDependsOnlyOnErrorSeverity() {
    LoadedDmnModel root = new LoadedDmnModel(
        new DmnSource(ROOT_ID, "root".getBytes(StandardCharsets.UTF_8)),
        Definitions.getDefaultInstance());
    DmnDiagnosticOrigin origin = DmnDiagnosticOrigin.source(ROOT_ID);
    DmnCompilerDiagnostic info = diagnostic(
        DmnDiagnosticSeverity.INFO, DmnCompilerPhase.XML_FRONTEND, "DMN-INFO", origin);
    DmnCompilerDiagnostic warning = diagnostic(
        DmnDiagnosticSeverity.WARNING, DmnCompilerPhase.XML_FRONTEND, "DMN-WARNING", origin);
    DmnCompilerDiagnostic error = diagnostic(
        DmnDiagnosticSeverity.ERROR, DmnCompilerPhase.XML_FRONTEND, "DMN-ERROR", origin);

    DmnModelLoadResult nonErrorResult = new DmnModelLoadResult(
        ROOT_ID, List.of(root), List.of(), List.of(info, warning));
    DmnModelLoadResult errorResult = new DmnModelLoadResult(
        ROOT_ID, List.of(root), List.of(), List.of(warning, error));

    assertThat(nonErrorResult.isValid()).isTrue();
    assertThat(nonErrorResult.hasErrors()).isFalse();
    assertThat(errorResult.isValid()).isFalse();
    assertThat(errorResult.hasErrors()).isTrue();
  }

  private static DmnCompilerDiagnostic diagnostic(
      DmnDiagnosticSeverity severity,
      DmnCompilerPhase phase,
      String code,
      DmnDiagnosticOrigin origin) {
    return new DmnCompilerDiagnostic(
        severity, phase, code, "message", origin, Optional.empty(), List.of(), List.of());
  }
}
