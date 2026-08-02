package io.finmsg.dmn.compiler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class DmnModelResolverTest {
  @Test
  void resolvesLocationsRelativeToImporter() {
    DmnSource imported = source("file:/models/shared/base.dmn", "<definitions/>");
    var resolver = new InMemoryDmnModelResolver(List.of(imported));
    var request = new DmnImportRequest(
        DmnSourceId.of("file:/models/root/main.dmn"), "../shared/base.dmn", "urn:base", "base");

    assertThat(resolver.resolve(request).uniqueSource()).contains(imported);
  }

  @Test
  void returnsMissingForUnknownOrNonLocationalImport() {
    var resolver = new InMemoryDmnModelResolver(List.of());

    assertThat(resolver.resolve(new DmnImportRequest(
        DmnSourceId.of("memory:/root.dmn"), "missing.dmn", "urn:missing", "")).isMissing())
        .isTrue();
    assertThat(resolver.resolve(new DmnImportRequest(
        DmnSourceId.of("memory:/root.dmn"), "", "urn:missing", "")).isMissing())
        .isTrue();
  }

  @Test
  void ordersCandidatesDeterministically() {
    var request = new DmnImportRequest(
        DmnSourceId.of("memory:/root.dmn"), "dependency.dmn", "", "");
    var result = new DmnResolutionResult(
        request, List.of(source("memory:/z.dmn", "z"), source("memory:/a.dmn", "a")));

    assertThat(result.candidates()).extracting(DmnSource::id)
        .containsExactly(DmnSourceId.of("memory:/a.dmn"), DmnSourceId.of("memory:/z.dmn"));
    assertThat(result.isAmbiguous()).isTrue();
    assertThat(result.uniqueSource()).isEmpty();
  }

  @Test
  void defensivelyCopiesContent() {
    byte[] bytes = "original".getBytes(StandardCharsets.UTF_8);
    DmnSource source = new DmnSource(DmnSourceId.of("memory:/model.dmn"), bytes);
    bytes[0] = 'X';
    byte[] returned = source.content();
    returned[0] = 'Y';

    assertThat(new String(source.content(), StandardCharsets.UTF_8)).isEqualTo("original");
    assertThat(source).isEqualTo(source("memory:/model.dmn", "original"));
  }

  @Test
  void rejectsInvalidContracts() {
    assertThatIllegalArgumentException().isThrownBy(() -> DmnSourceId.of("models/root.dmn"));
    assertThatIllegalArgumentException().isThrownBy(() -> new DmnImportRequest(
        DmnSourceId.of("memory:/root.dmn"), "", "", ""));
    DmnSource duplicate = source("memory:/duplicate.dmn", "model");
    assertThatIllegalArgumentException().isThrownBy(
        () -> new InMemoryDmnModelResolver(List.of(duplicate, duplicate)));
  }

  private static DmnSource source(String id, String content) {
    return new DmnSource(DmnSourceId.of(id), content.getBytes(StandardCharsets.UTF_8));
  }
}
