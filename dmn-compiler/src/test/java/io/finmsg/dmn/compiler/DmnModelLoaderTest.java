package io.finmsg.dmn.compiler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DmnModelLoaderTest {

  private final DmnModelLoader loader = new DmnModelLoader();

  @Test
  void loadsRootWithoutImports() {
    DmnSource root = source("root.dmn");

    DmnModelLoadResult result = loader.load(root, new InMemoryDmnModelResolver(List.of()));

    assertThat(result.rootId()).isEqualTo(root.id());
    assertThat(result.models()).extracting(LoadedDmnModel::id).containsExactly(root.id());
    assertThat(result.importEdges()).isEmpty();
    assertThatThrownBy(() -> result.models().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> result.importEdges().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void loadsOneDirectImport() {
    DmnSource root = source("root.dmn", "base.dmn");
    DmnSource base = source("base.dmn");

    DmnModelLoadResult result = loader.load(root, new InMemoryDmnModelResolver(List.of(base)));

    assertThat(result.models()).extracting(LoadedDmnModel::id)
        .containsExactly(base.id(), root.id());
    assertThat(result.importEdges()).singleElement().satisfies(edge -> {
      assertThat(edge.importer()).isEqualTo(root.id());
      assertThat(edge.imported()).isEqualTo(base.id());
      assertThat(edge.importIndex()).isZero();
    });
  }

  @Test
  void loadsThreeLevelChainInStableIdentityOrder() {
    DmnSource root = source("root.dmn", "middle.dmn");
    DmnSource middle = source("middle.dmn", "leaf.dmn");
    DmnSource leaf = source("leaf.dmn");

    DmnModelLoadResult result = loader.load(
        root, new InMemoryDmnModelResolver(List.of(middle, leaf)));

    assertThat(result.models()).extracting(LoadedDmnModel::id)
        .containsExactly(leaf.id(), middle.id(), root.id());
    assertThat(result.importEdges()).extracting(DmnImportEdge::imported)
        .containsExactly(leaf.id(), middle.id());
  }

  @Test
  void loadsDiamondDependencyOnce() {
    DmnSource root = source("root.dmn", "b.dmn", "a.dmn");
    DmnSource first = source("a.dmn", "leaf.dmn");
    DmnSource second = source("b.dmn", "leaf.dmn");
    DmnSource leaf = source("leaf.dmn");
    CountingResolver resolver = new CountingResolver(
        new InMemoryDmnModelResolver(List.of(first, second, leaf)));

    DmnModelLoadResult result = loader.load(root, resolver);

    assertThat(result.models()).extracting(LoadedDmnModel::id)
        .containsExactly(first.id(), second.id(), leaf.id(), root.id());
    assertThat(result.importEdges()).hasSize(4);
    assertThat(resolver.callsByLocation())
        .containsEntry("a.dmn", 1)
        .containsEntry("b.dmn", 1)
        .containsEntry("leaf.dmn", 1);
    assertThat(resolver.totalCalls()).isEqualTo(3);
  }

  @Test
  void resultDoesNotDependOnResolverRegistrationOrder() {
    DmnSource root = source("root.dmn", "b.dmn", "a.dmn");
    DmnSource first = source("a.dmn");
    DmnSource second = source("b.dmn");
    List<DmnSource> forward = List.of(first, second);
    List<DmnSource> reverse = new ArrayList<>(forward);
    Collections.reverse(reverse);

    DmnModelLoadResult firstResult = loader.load(
        root, new InMemoryDmnModelResolver(forward));
    DmnModelLoadResult secondResult = loader.load(
        root, new InMemoryDmnModelResolver(reverse));

    assertThat(secondResult).isEqualTo(firstResult);
  }

  @Test
  void cachesCanonicalIdentityReturnedForRequestedLocation() {
    DmnSource root = source("root.dmn", "alias.dmn", "alias.dmn");
    DmnSource canonical = new DmnSource(
        DmnSourceId.of("memory:/models/canonical.dmn"), xml("canonical.dmn").getBytes(
            StandardCharsets.UTF_8));
    CountingResolver resolver = new CountingResolver(
        request -> DmnResolutionResult.resolved(request, canonical));

    DmnModelLoadResult result = loader.load(root, resolver);

    assertThat(result.models()).extracting(LoadedDmnModel::id)
        .containsExactly(canonical.id(), root.id());
    assertThat(result.importEdges()).hasSize(2);
    assertThat(resolver.totalCalls()).isOne();
  }

  @Test
  void stopsAtCachedCycleAndPreservesClosingEdge() {
    DmnSource root = source("root.dmn", "other.dmn");
    DmnSource other = source("other.dmn", "root.dmn");
    CountingResolver resolver = new CountingResolver(
        new InMemoryDmnModelResolver(List.of(root, other)));

    DmnModelLoadResult result = loader.load(root, resolver);

    assertThat(result.models()).hasSize(2);
    assertThat(result.importEdges()).hasSize(2);
    assertThat(result.importEdges()).anySatisfy(edge -> {
      assertThat(edge.importer()).isEqualTo(other.id());
      assertThat(edge.imported()).isEqualTo(root.id());
    });
    assertThat(resolver.totalCalls()).isOne();
  }

  @Test
  void enforcesSourceCountAndImportDepthLimits() {
    DmnSource root = source("root.dmn", "middle.dmn");
    DmnSource middle = source("middle.dmn", "leaf.dmn");
    DmnSource leaf = source("leaf.dmn");
    DmnModelResolver resolver = new InMemoryDmnModelResolver(List.of(middle, leaf));

    assertThatThrownBy(() -> loader.load(root, resolver, new DmnModelLoadOptions(2, 10)))
        .isInstanceOf(DmnModelLoadException.class)
        .hasMessage("DMN model graph exceeds maxSources=2");
    assertThatThrownBy(() -> loader.load(root, resolver, new DmnModelLoadOptions(10, 1)))
        .isInstanceOf(DmnModelLoadException.class)
        .hasMessageContaining("maxImportDepth=1")
        .hasMessageContaining(middle.id().toString());
  }

  private static DmnSource source(String name, String... imports) {
    return new DmnSource(
        DmnSourceId.of("memory:/models/" + name), xml(name, imports).getBytes(StandardCharsets.UTF_8));
  }

  private static String xml(String name, String... imports) {
    StringBuilder xml = new StringBuilder()
        .append("<definitions xmlns=\"https://www.omg.org/spec/DMN/20230324/MODEL/\"")
        .append(" id=\"").append(name).append("\"")
        .append(" name=\"").append(name).append("\"")
        .append(" namespace=\"urn:test:").append(name).append("\">");
    for (String imported : imports) {
      xml.append("<import namespace=\"urn:test:").append(imported)
          .append("\" locationURI=\"").append(imported).append("\"/>");
    }
    return xml.append("</definitions>").toString();
  }

  private static final class CountingResolver implements DmnModelResolver {
    private final DmnModelResolver delegate;
    private final Map<String, Integer> callsByLocation = new HashMap<>();

    private CountingResolver(DmnModelResolver delegate) {
      this.delegate = delegate;
    }

    @Override
    public DmnResolutionResult resolve(DmnImportRequest request) {
      callsByLocation.merge(request.location(), 1, Integer::sum);
      return delegate.resolve(request);
    }

    private Map<String, Integer> callsByLocation() {
      return Map.copyOf(callsByLocation);
    }

    private int totalCalls() {
      return callsByLocation.values().stream().mapToInt(Integer::intValue).sum();
    }
  }
}
