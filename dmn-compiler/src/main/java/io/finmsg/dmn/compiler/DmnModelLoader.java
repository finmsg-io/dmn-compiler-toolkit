package io.finmsg.dmn.compiler;

import io.finmsg.dmn.frontend.xml.dmn.DmnReadOptions;
import io.finmsg.dmn.frontend.xml.dmn.DmnReadResult;
import io.finmsg.dmn.frontend.xml.dmn.DmnXmlReader;
import io.finmsg.dmn.model.Definitions;
import io.finmsg.dmn.model.Import;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Loads and parses a bounded graph of location-addressable DMN imports. */
public final class DmnModelLoader {
  private final DmnXmlReader xmlReader;

  public DmnModelLoader() {
    this(new DmnXmlReader());
  }

  DmnModelLoader(DmnXmlReader xmlReader) {
    this.xmlReader = Objects.requireNonNull(xmlReader, "xmlReader");
  }

  public DmnModelLoadResult load(DmnSource root, DmnModelResolver resolver) {
    return load(root, resolver, DmnModelLoadOptions.defaults());
  }

  public DmnModelLoadResult load(
      DmnSource root, DmnModelResolver resolver, DmnModelLoadOptions options) {
    Objects.requireNonNull(root, "root");
    Objects.requireNonNull(resolver, "resolver");
    Objects.requireNonNull(options, "options");
    LoadSession session = new LoadSession(resolver, options);
    session.load(root, 0);
    return new DmnModelLoadResult(
        root.id(), new ArrayList<>(session.models.values()), session.edges);
  }

  private final class LoadSession {
    private final DmnModelResolver resolver;
    private final DmnModelLoadOptions options;
    private final Map<DmnSourceId, LoadedDmnModel> models = new LinkedHashMap<>();
    private final Map<DmnSourceId, DmnSourceId> aliases = new HashMap<>();
    private final List<DmnImportEdge> edges = new ArrayList<>();

    private LoadSession(DmnModelResolver resolver, DmnModelLoadOptions options) {
      this.resolver = resolver;
      this.options = options;
    }

    private void load(DmnSource source, int depth) {
      if (models.containsKey(source.id())) {
        return;
      }
      if (models.size() >= options.maxSources()) {
        throw new DmnModelLoadException(
            "DMN model graph exceeds maxSources=" + options.maxSources());
      }
      Definitions definitions = parse(source);
      models.put(source.id(), new LoadedDmnModel(source, definitions));

      for (int index = 0; index < definitions.getImportsCount(); index++) {
        Import imported = definitions.getImports(index);
        DmnImportRequest request = new DmnImportRequest(
            source.id(), imported.getLocationUri(), imported.getNamespace(), imported.getName());
        if (request.resolvedLocation().isEmpty()) {
          continue;
        }
        if (depth >= options.maxImportDepth()) {
          throw new DmnModelLoadException(
              "DMN model graph exceeds maxImportDepth=" + options.maxImportDepth()
                  + " at " + source.id());
        }

        DmnSource target = cachedTarget(request);
        if (target == null) {
          DmnResolutionResult resolution = resolver.resolve(request);
          target = resolution.uniqueSource().orElseThrow(() -> new DmnModelLoadException(
              resolution.isMissing()
                  ? "Cannot resolve DMN import '" + request.location() + "' from " + source.id()
                  : "Ambiguous DMN import '" + request.location() + "' from " + source.id()));
          DmnSourceId resolvedTargetId = target.id();
          request.resolvedLocation().ifPresent(id -> aliases.put(id, resolvedTargetId));
        }

        edges.add(new DmnImportEdge(source.id(), index, request, target.id()));
        load(target, depth + 1);
      }
    }

    private DmnSource cachedTarget(DmnImportRequest request) {
      DmnSourceId requestedId = request.resolvedLocation().orElseThrow();
      DmnSourceId actualId = aliases.getOrDefault(requestedId, requestedId);
      LoadedDmnModel cached = models.get(actualId);
      return cached == null ? null : cached.source();
    }

    private Definitions parse(DmnSource source) {
      DmnReadOptions readOptions = DmnReadOptions.defaults().withSystemId(source.id().toString());
      DmnReadResult result = xmlReader.readResult(source.content(), readOptions);
      if (result.model().isPresent() && !result.hasErrors()) {
        return result.model().orElseThrow();
      }
      String detail = result.diagnostics().isEmpty()
          ? "DMN XML did not produce a model"
          : result.diagnostics().getFirst().getMessage();
      throw new DmnModelLoadException("Cannot parse DMN source " + source.id() + ": " + detail);
    }
  }
}
