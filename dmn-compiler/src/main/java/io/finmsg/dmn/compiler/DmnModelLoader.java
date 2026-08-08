package io.finmsg.dmn.compiler;

import io.finmsg.dmn.frontend.xml.dmn.DmnReadOptions;
import io.finmsg.dmn.frontend.xml.dmn.DmnReadResult;
import io.finmsg.dmn.frontend.xml.dmn.DmnXmlReader;
import io.finmsg.dmn.model.Definitions;
import io.finmsg.dmn.model.Import;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
    session.load(root, 0, null);
    return new DmnModelLoadResult(
        root.id(), new ArrayList<>(session.models.values()), session.edges, session.diagnostics);
  }

  private final class LoadSession {
    private final DmnModelResolver resolver;
    private final DmnModelLoadOptions options;
    private final Map<DmnSourceId, LoadedDmnModel> models = new LinkedHashMap<>();
    private final Map<DmnSourceId, DmnSourceId> aliases = new HashMap<>();
    private final List<DmnImportEdge> edges = new ArrayList<>();
    private final List<DmnCompilerDiagnostic> diagnostics = new ArrayList<>();
    private final List<DmnSourceId> activePath = new ArrayList<>();

    private LoadSession(DmnModelResolver resolver, DmnModelLoadOptions options) {
      this.resolver = resolver;
      this.options = options;
    }

    private void load(DmnSource source, int depth, DmnImportEdge incomingEdge) {
      if (models.containsKey(source.id())) {
        return;
      }
      if (models.size() >= options.maxSources()) {
        throw new DmnModelLoadException(
            "DMN model graph exceeds maxSources=" + options.maxSources());
      }
      Definitions definitions = parse(source);
      models.put(source.id(), new LoadedDmnModel(source, definitions));
      detectDuplicateModelIdentity(source.id(), definitions, incomingEdge);
      activePath.add(source.id());

      try {
        for (int index = 0; index < definitions.getImportsCount(); index++) {
          Import imported = definitions.getImports(index);
          DmnImportRequest request = new DmnImportRequest(
              source.id(), imported.getLocationUri(), imported.getNamespace(), imported.getName());
          if (depth >= options.maxImportDepth()) {
            throw new DmnModelLoadException(
                "DMN model graph exceeds maxImportDepth=" + options.maxImportDepth()
                    + " at " + source.id());
          }

          DmnSource target = cachedTarget(request);
          if (target == null) {
            DmnResolutionResult resolution = resolver.resolve(request);
            if (resolution.isMissing()) {
              diagnostics.add(diagnostic(
                  DmnDiagnosticCodes.IMPORT_MISSING, index, request, List.of(), List.of(),
                  "Cannot resolve DMN import '" + request.location() + "' from " + source.id()));
              continue;
            }
            if (resolution.isAmbiguous()) {
              List<DmnSourceId> candidates = resolution.candidates().stream()
                  .map(DmnSource::id).toList();
              diagnostics.add(diagnostic(
                  DmnDiagnosticCodes.IMPORT_AMBIGUOUS, index, request, candidates, List.of(),
                  "Ambiguous DMN import '" + request.location() + "' from " + source.id()));
              continue;
            }
            target = resolution.uniqueSource().orElseThrow();
            DmnSourceId resolvedTargetId = target.id();
            request.resolvedLocation().ifPresent(id -> aliases.put(id, resolvedTargetId));
          }

          DmnImportEdge edge = new DmnImportEdge(source.id(), index, request, target.id());
          edges.add(edge);
          LoadedDmnModel existing = models.get(target.id());
          if (existing != null && !Arrays.equals(existing.source().content(), target.content())) {
            diagnostics.add(diagnostic(
                DmnDiagnosticCodes.IMPORT_DUPLICATE, index, request, List.of(target.id()), List.of(),
                "Conflicting content for DMN source identity " + target.id()));
            continue;
          }
          if (activePath.contains(target.id())) {
            List<DmnSourceId> cycle = canonicalCycle(target.id());
            diagnostics.add(diagnostic(
                DmnDiagnosticCodes.IMPORT_CYCLE, index, request, cycle, cycle,
                "Cyclic DMN import: " + cycle));
            continue;
          }
          load(target, depth + 1, edge);
        }
      } finally {
        activePath.removeLast();
      }
    }

    private DmnCompilerDiagnostic diagnostic(
        String code,
        int importIndex,
        DmnImportRequest request,
        List<DmnSourceId> relatedSourceIds,
        List<DmnSourceId> cyclePath,
        String message) {
      LoadedDmnModel importer = models.get(request.importer());
      Optional<DmnModelIdentity> modelIdentity = importer == null
          ? Optional.empty()
          : Optional.of(new DmnModelIdentity(
              importer.model().getNamespace(), importer.model().getNode().getName()));
      DmnDiagnosticOrigin origin = new DmnDiagnosticOrigin(
          request.importer(), modelIdentity, Optional.of(importIndex));
      return new DmnCompilerDiagnostic(
          DmnDiagnosticSeverity.ERROR,
          DmnCompilerPhase.SOURCE_RESOLUTION,
          code,
          message,
          origin,
          Optional.of(request),
          relatedSourceIds,
          cyclePath);
    }

    private void detectDuplicateModelIdentity(
        DmnSourceId sourceId, Definitions definitions, DmnImportEdge incomingEdge) {
      if (incomingEdge == null) {
        return;
      }
      models.values().stream()
          .filter(model -> !model.id().equals(sourceId))
          .filter(model -> model.model().getNamespace().equals(definitions.getNamespace()))
          .filter(model -> model.model().getNode().getName().equals(definitions.getNode().getName()))
          .findFirst()
          .ifPresent(existing -> diagnostics.add(diagnostic(
              DmnDiagnosticCodes.IMPORT_DUPLICATE,
              incomingEdge.importIndex(),
              incomingEdge.request(),
              List.of(existing.id(), sourceId),
              List.of(),
              "Duplicate DMN model identity namespace='" + definitions.getNamespace()
                  + "', name='" + definitions.getNode().getName() + "'")));
    }

    private List<DmnSourceId> canonicalCycle(DmnSourceId targetId) {
      int start = activePath.indexOf(targetId);
      List<DmnSourceId> nodes = new ArrayList<>(activePath.subList(start, activePath.size()));
      int canonicalStart = 0;
      for (int index = 1; index < nodes.size(); index++) {
        if (nodes.get(index).compareTo(nodes.get(canonicalStart)) < 0) {
          canonicalStart = index;
        }
      }
      List<DmnSourceId> cycle = new ArrayList<>(nodes.size() + 1);
      cycle.addAll(nodes.subList(canonicalStart, nodes.size()));
      cycle.addAll(nodes.subList(0, canonicalStart));
      cycle.add(cycle.getFirst());
      return List.copyOf(cycle);
    }

    private DmnSource cachedTarget(DmnImportRequest request) {
      if (request.resolvedLocation().isEmpty()) {
        return null;
      }
      DmnSourceId requestedId = request.resolvedLocation().get();
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
