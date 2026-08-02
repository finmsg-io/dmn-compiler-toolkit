package io.finmsg.dmn.compiler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ClasspathDmnModelResolverTest {

  @Test
  void resolvesRelativeResourceAndClosesItsStream() {
    TrackingClassLoader loader = new TrackingClassLoader("models/shared/base.dmn", "base");
    var resolver = new ClasspathDmnModelResolver(loader, "models");
    var request = new DmnImportRequest(
        DmnSourceId.of("classpath:/models/root.dmn"), "shared/base.dmn", "", "");

    DmnSource source = resolver.resolve(request).uniqueSource().orElseThrow();

    assertThat(source.id()).isEqualTo(DmnSourceId.of("classpath:/models/shared/base.dmn"));
    assertThat(new String(source.content(), StandardCharsets.UTF_8)).isEqualTo("base");
    assertThat(loader.closed).isTrue();
  }

  @Test
  void confinesResolutionToLogicalClasspathRoot() {
    TrackingClassLoader loader = new TrackingClassLoader("outside.dmn", "outside");
    var resolver = new ClasspathDmnModelResolver(loader, "models");
    var request = new DmnImportRequest(
        DmnSourceId.of("classpath:/models/root.dmn"), "../outside.dmn", "", "");

    assertThat(resolver.resolve(request).isMissing()).isTrue();
    assertThat(loader.opened).isFalse();
  }

  @Test
  void ignoresMissingNonClasspathAndNonLocationalImports() {
    var resolver = new ClasspathDmnModelResolver(new TrackingClassLoader("unused", "unused"));

    assertThat(resolver.resolve(new DmnImportRequest(
        DmnSourceId.of("classpath:/models/root.dmn"), "missing.dmn", "", "")).isMissing())
        .isTrue();
    assertThat(resolver.resolve(new DmnImportRequest(
        DmnSourceId.of("file:/models/root.dmn"), "base.dmn", "", "")).isMissing()).isTrue();
    assertThat(resolver.resolve(new DmnImportRequest(
        DmnSourceId.of("classpath:/models/root.dmn"), "", "urn:base", "")).isMissing())
        .isTrue();
  }

  @Test
  void rejectsParentTraversalInConfiguredRoot() {
    assertThatIllegalArgumentException().isThrownBy(
        () -> new ClasspathDmnModelResolver(getClass().getClassLoader(), "../models"));
  }

  @Test
  void wrapsReadFailuresAndStillClosesTheStream() {
    boolean[] closed = {false};
    ClassLoader loader = new ClassLoader(null) {
      @Override
      public InputStream getResourceAsStream(String name) {
        return new InputStream() {
          @Override
          public int read() throws IOException {
            throw new IOException("broken resource");
          }

          @Override
          public void close() {
            closed[0] = true;
          }
        };
      }
    };
    var resolver = new ClasspathDmnModelResolver(loader);
    var request = new DmnImportRequest(
        DmnSourceId.of("classpath:/models/root.dmn"), "base.dmn", "", "");

    assertThatThrownBy(() -> resolver.resolve(request))
        .isInstanceOf(DmnResolutionException.class)
        .hasMessageContaining("classpath:/models/base.dmn")
        .hasCauseInstanceOf(IOException.class);
    assertThat(closed[0]).isTrue();
  }

  private static final class TrackingClassLoader extends ClassLoader {
    private final String expectedName;
    private final byte[] content;
    private boolean opened;
    private boolean closed;

    private TrackingClassLoader(String expectedName, String content) {
      super(null);
      this.expectedName = expectedName;
      this.content = content.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public InputStream getResourceAsStream(String name) {
      if (!expectedName.equals(name)) {
        return null;
      }
      opened = true;
      return new ByteArrayInputStream(content) {
        @Override
        public void close() {
          closed = true;
        }
      };
    }
  }
}
