package io.finmsg.dmn.compiler;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilesystemDmnModelResolverTest {

  @TempDir Path temporaryDirectory;

  @Test
  void resolvesAndReadsRelativeImportInsideConfiguredRoot() throws IOException {
    Path models = Files.createDirectories(temporaryDirectory.resolve("models"));
    Path rootModel = Files.writeString(models.resolve("root.dmn"), "root");
    Path imported = Files.createDirectories(models.resolve("shared")).resolve("base.dmn");
    Files.writeString(imported, "base model");
    var resolver = new FilesystemDmnModelResolver(models);
    DmnSourceId expectedId = new DmnSourceId(imported.toRealPath().toUri());

    DmnResolutionResult result = resolver.resolve(request(rootModel, "shared/base.dmn"));

    assertThat(result.uniqueSource()).hasValueSatisfying(source -> {
      assertThat(source.id()).isEqualTo(expectedId);
      assertThat(new String(source.content(), StandardCharsets.UTF_8)).isEqualTo("base model");
    });
  }

  @Test
  void confinesResolutionToConfiguredRoot() throws IOException {
    Path models = Files.createDirectories(temporaryDirectory.resolve("models"));
    Path rootModel = Files.writeString(models.resolve("root.dmn"), "root");
    Files.writeString(temporaryDirectory.resolve("outside.dmn"), "outside");
    var resolver = new FilesystemDmnModelResolver(models);

    assertThat(resolver.resolve(request(rootModel, "../outside.dmn")).isMissing()).isTrue();
  }

  @Test
  void ignoresMissingNonFileAndNonLocationalImports() throws IOException {
    Path models = Files.createDirectories(temporaryDirectory.resolve("models"));
    Path rootModel = Files.writeString(models.resolve("root.dmn"), "root");
    var resolver = new FilesystemDmnModelResolver(models);

    assertThat(resolver.resolve(request(rootModel, "missing.dmn")).isMissing()).isTrue();
    assertThat(resolver.resolve(new DmnImportRequest(
        DmnSourceId.of("classpath:/models/root.dmn"), "base.dmn", "", "")).isMissing())
        .isTrue();
    assertThat(resolver.resolve(new DmnImportRequest(
        new DmnSourceId(rootModel.toUri()), "", "urn:base", "")).isMissing()).isTrue();
  }

  private static DmnImportRequest request(Path importer, String location) {
    return new DmnImportRequest(new DmnSourceId(importer.toUri()), location, "", "");
  }
}
