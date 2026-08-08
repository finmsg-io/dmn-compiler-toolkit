package io.finmsg.dmn.models.stream;

import io.finmsg.dmn.compiler.DmnCompilationResult;
import io.finmsg.dmn.compiler.DmnCompiler;
import io.finmsg.dmn.compiler.DmnModelLoadOptions;
import io.finmsg.dmn.compiler.DmnModelLoadResult;
import io.finmsg.dmn.compiler.DmnModelLoader;
import io.finmsg.dmn.compiler.DmnModelResolver;
import io.finmsg.dmn.compiler.DmnSource;
import io.finmsg.dmn.compiler.DmnSourceId;
import io.finmsg.dmn.frontend.xml.dmn.DmnReadOptions;
import io.finmsg.dmn.frontend.xml.dmn.DmnReadResult;
import io.finmsg.dmn.frontend.xml.dmn.DmnXmlReader;
import io.finmsg.dmn.model.Definitions;
import io.finmsg.dmn.model.Import;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Immutable bundle of location-addressable DMN sources supporting streaming ingestion
 * from ZIP archives, directory trees, classpath resources, and input streams.
 */
public record DmnStreamBundle(Map<String, DmnSource> sources) {

  public DmnStreamBundle {
    sources = Map.copyOf(Objects.requireNonNull(sources, "sources"));
  }

  // --- Factory Constructors ---

  /** Stream multi-file DMN models from a ZIP archive InputStream without unpacking to disk. */
  public static DmnStreamBundle fromZip(InputStream zipInputStream) {
    Objects.requireNonNull(zipInputStream, "zipInputStream");
    Map<String, DmnSource> map = new LinkedHashMap<>();
    try (ZipInputStream zip = new ZipInputStream(zipInputStream)) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (entry.isDirectory()) {
          continue;
        }
        String name = normalizeLocation(entry.getName());
        if (name.endsWith(".dmn") || name.endsWith(".xml")) {
          byte[] content = zip.readAllBytes();
          map.put(name, new DmnSource(toSourceId(name), content));
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read ZIP stream", e);
    }
    return new DmnStreamBundle(map);
  }

  /** Stream multi-file DMN models from raw ZIP bytes. */
  public static DmnStreamBundle fromZip(byte[] zipBytes) {
    Objects.requireNonNull(zipBytes, "zipBytes");
    return fromZip(new ByteArrayInputStream(zipBytes));
  }

  /** Stream multi-file DMN models from a filesystem directory tree. */
  public static DmnStreamBundle fromDirectory(Path directoryPath) {
    Objects.requireNonNull(directoryPath, "directoryPath");
    if (!Files.isDirectory(directoryPath)) {
      throw new IllegalArgumentException("Path is not a directory: " + directoryPath);
    }
    Map<String, DmnSource> map = new LinkedHashMap<>();
    try (Stream<Path> stream = Files.walk(directoryPath)) {
      stream.filter(Files::isRegularFile)
          .filter(path -> {
            String name = path.getFileName().toString();
            return name.endsWith(".dmn") || name.endsWith(".xml");
          })
          .forEach(path -> {
            Path relative = directoryPath.relativize(path);
            String location = normalizeLocation(relative.toString());
            try {
              byte[] content = Files.readAllBytes(path);
              map.put(location, new DmnSource(toSourceId(location), content));
            } catch (IOException e) {
              throw new UncheckedIOException("Failed to read file: " + path, e);
            }
          });
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to walk directory: " + directoryPath, e);
    }
    return new DmnStreamBundle(map);
  }

  /** Stream multi-file DMN models from a Classpath directory resource. */
  public static DmnStreamBundle fromClasspath(String resourcePath) {
    return fromClasspath(Thread.currentThread().getContextClassLoader(), resourcePath);
  }

  /** Stream multi-file DMN models from a Classpath directory resource using a specified ClassLoader. */
  public static DmnStreamBundle fromClasspath(ClassLoader classLoader, String resourcePath) {
    Objects.requireNonNull(classLoader, "classLoader");
    Objects.requireNonNull(resourcePath, "resourcePath");
    String cleanedPath = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
    URL url = classLoader.getResource(cleanedPath);
    if (url == null) {
      throw new IllegalArgumentException("Classpath resource not found: " + cleanedPath);
    }
    try {
      URI uri = url.toURI();
      if ("file".equalsIgnoreCase(uri.getScheme())) {
        return fromDirectory(Path.of(uri));
      } else if ("jar".equalsIgnoreCase(uri.getScheme())) {
        String[] parts = uri.toString().split("!");
        URL jarUrl = new URI(parts[0]).toURL();
        String entryPrefix = parts.length > 1 ? parts[1] : "";
        if (entryPrefix.startsWith("/")) {
          entryPrefix = entryPrefix.substring(1);
        }
        try (InputStream in = jarUrl.openStream()) {
          DmnStreamBundle fullZip = fromZip(in);
          String finalPrefix = entryPrefix;
          Map<String, DmnSource> filtered = new LinkedHashMap<>();
          fullZip.sources().forEach((loc, src) -> {
            if (loc.startsWith(finalPrefix)) {
              String subLoc = loc.substring(finalPrefix.length());
              if (subLoc.startsWith("/")) {
                subLoc = subLoc.substring(1);
              }
              filtered.put(subLoc, new DmnSource(toSourceId(subLoc), src.content()));
            }
          });
          return new DmnStreamBundle(filtered);
        }
      } else {
        throw new UnsupportedOperationException("Unsupported URI scheme for classpath resource: " + uri);
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to stream classpath model bundle: " + cleanedPath, e);
    }
  }

  /** Stream multi-file DMN models from a Map of location-to-InputStream. */
  public static DmnStreamBundle fromStreams(Map<String, InputStream> streamMap) {
    Objects.requireNonNull(streamMap, "streamMap");
    Map<String, DmnSource> map = new LinkedHashMap<>();
    streamMap.forEach((location, inputStream) -> {
      String norm = normalizeLocation(location);
      try {
        byte[] content = inputStream.readAllBytes();
        map.put(norm, new DmnSource(toSourceId(norm), content));
      } catch (IOException e) {
        throw new UncheckedIOException("Failed to read stream for " + location, e);
      }
    });
    return new DmnStreamBundle(map);
  }

  /** Create bundle from a collection of DmnSource instances. */
  public static DmnStreamBundle fromSources(Collection<DmnSource> sources) {
    Objects.requireNonNull(sources, "sources");
    Map<String, DmnSource> map = new LinkedHashMap<>();
    sources.forEach(src -> {
      String norm = normalizeLocation(src.id().toString());
      map.put(norm, src);
    });
    return new DmnStreamBundle(map);
  }

  // --- Querying & Resolution Methods ---

  public DmnModelResolver asResolver() {
    return new DmnStreamResolver(this);
  }

  public Optional<DmnSource> findSource(String location) {
    String norm = normalizeLocation(location);
    if (sources.containsKey(norm)) {
      return Optional.of(sources.get(norm));
    }
    return sources.values().stream()
        .filter(s -> s.id().toString().equalsIgnoreCase(norm) || basename(s.id().toString()).equalsIgnoreCase(basename(norm)))
        .findFirst();
  }

  public List<DmnSource> findMatchingSources(String location) {
    String norm = normalizeLocation(location);
    String targetBase = basename(norm);
    return sources.entrySet().stream()
        .filter(e -> e.getKey().equalsIgnoreCase(norm) || basename(e.getKey()).equalsIgnoreCase(targetBase))
        .map(Map.Entry::getValue)
        .toList();
  }

  public List<DmnSource> findSourcesByNamespace(String namespace) {
    if (namespace == null || namespace.isBlank()) {
      return List.of();
    }
    DmnXmlReader xmlReader = new DmnXmlReader();
    List<DmnSource> matches = new ArrayList<>();
    sources.forEach((loc, src) -> {
      try {
        DmnReadResult result = xmlReader.readResult(src.content(), DmnReadOptions.defaults().withSystemId(loc));
        if (result.model().isPresent() && namespace.equals(result.model().get().getNamespace())) {
          matches.add(src);
        } else {
          Matcher defMatcher = DEFINITIONS_NS_PATTERN.matcher(new String(src.content(), StandardCharsets.UTF_8));
          if (defMatcher.find() && namespace.equals(defMatcher.group(1))) {
            matches.add(src);
          }
        }
      } catch (Exception ignored) {
      }
    });
    return matches;
  }

  /**
   * Finds the root DMN source candidate in this multi-file bundle.
   * The root source is the DMN file that imports other files but is imported by none.
   */
  public Optional<DmnSource> findRootSource() {
    if (sources.isEmpty()) {
      return Optional.empty();
    }
    if (sources.size() == 1) {
      return Optional.of(sources.values().iterator().next());
    }

    DmnXmlReader xmlReader = new DmnXmlReader();
    Map<String, String> sourceNamespaces = new HashMap<>();
    Set<String> importedKeys = new HashSet<>();

    sources.forEach((loc, src) -> {
      try {
        DmnReadResult result = xmlReader.readResult(src.content(), DmnReadOptions.defaults().withSystemId(loc));
        if (result.model().isPresent()) {
          Definitions defs = result.model().get();
          if (defs.getNamespace() != null && !defs.getNamespace().isBlank()) {
            sourceNamespaces.put(loc, defs.getNamespace());
          }
          for (Import imp : defs.getImportsList()) {
            if (imp.getLocationUri() != null && !imp.getLocationUri().isBlank()) {
              String normImp = normalizeLocation(imp.getLocationUri());
              importedKeys.add(normImp);
              importedKeys.add(basename(normImp));
            }
            if (imp.getNamespace() != null && !imp.getNamespace().isBlank()) {
              importedKeys.add(imp.getNamespace());
            }
            if (imp.getName() != null && !imp.getName().isBlank()) {
              importedKeys.add(imp.getName());
              importedKeys.add(basename(imp.getName()));
            }
          }
        } else {
          extractNamespaceAndImportsFallback(src.content(), sourceNamespaces, importedKeys, loc);
        }
      } catch (Exception ignored) {
        extractNamespaceAndImportsFallback(src.content(), sourceNamespaces, importedKeys, loc);
      }
    });

    List<DmnSource> rootCandidates = sources.entrySet().stream()
        .filter(e -> {
          String loc = e.getKey();
          String ns = sourceNamespaces.get(loc);
          return !importedKeys.contains(loc)
              && !importedKeys.contains(basename(loc))
              && (ns == null || !importedKeys.contains(ns));
        })
        .map(Map.Entry::getValue)
        .toList();

    if (!rootCandidates.isEmpty()) {
      return Optional.of(rootCandidates.getFirst());
    }

    return Optional.of(sources.values().iterator().next());
  }

  // --- Convenient Load & Compile Helper Methods for Testing & Execution ---

  public DmnModelLoadResult load(DmnModelLoader loader) {
    DmnSource root = findRootSource().orElseThrow(() -> new IllegalStateException("Bundle is empty"));
    return loader.load(root, asResolver(), DmnModelLoadOptions.defaults());
  }

  public DmnModelLoadResult load(DmnModelLoader loader, String rootLocation) {
    DmnSource root = findSource(rootLocation)
        .orElseThrow(() -> new IllegalArgumentException("Root source not found in bundle: " + rootLocation));
    return loader.load(root, asResolver(), DmnModelLoadOptions.defaults());
  }

  public DmnCompilationResult compile(DmnCompiler compiler) {
    DmnSource root = findRootSource().orElseThrow(() -> new IllegalStateException("Bundle is empty"));
    return compiler.compile(root, asResolver());
  }

  public DmnCompilationResult compile(DmnCompiler compiler, String rootLocation) {
    DmnSource root = findSource(rootLocation)
        .orElseThrow(() -> new IllegalArgumentException("Root source not found in bundle: " + rootLocation));
    return compiler.compile(root, asResolver());
  }

  private static DmnSourceId toSourceId(String location) {
    String norm = normalizeLocation(location);
    if (norm.contains(":/")) {
      return DmnSourceId.of(norm);
    }
    try {
      return new DmnSourceId(new URI("memory", null, "/" + norm, null));
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Invalid DMN source location: " + location, e);
    }
  }

  private static String normalizeLocation(String location) {
    if (location == null) {
      return "";
    }
    String s = location.replace('\\', '/');
    if (s.startsWith("./")) {
      s = s.substring(2);
    }
    return s;
  }

  private static final Pattern DEFINITIONS_NS_PATTERN = Pattern.compile("<[^>]*definitions[^>]*namespace=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
  private static final Pattern IMPORT_PATTERN = Pattern.compile("<[^>]*import[^>]*>", Pattern.CASE_INSENSITIVE);
  private static final Pattern NS_ATTR_PATTERN = Pattern.compile("namespace=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
  private static final Pattern LOC_ATTR_PATTERN = Pattern.compile("locationURI=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
  private static final Pattern NAME_ATTR_PATTERN = Pattern.compile("name=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

  private static void extractNamespaceAndImportsFallback(
      byte[] xmlBytes, Map<String, String> sourceNamespaces, Set<String> importedKeys, String loc) {
    String xml = new String(xmlBytes, StandardCharsets.UTF_8);
    Matcher defMatcher = DEFINITIONS_NS_PATTERN.matcher(xml);
    if (defMatcher.find()) {
      sourceNamespaces.put(loc, defMatcher.group(1));
    }
    Matcher impMatcher = IMPORT_PATTERN.matcher(xml);
    while (impMatcher.find()) {
      String impBlock = impMatcher.group();
      Matcher nsMatcher = NS_ATTR_PATTERN.matcher(impBlock);
      if (nsMatcher.find()) {
        importedKeys.add(nsMatcher.group(1));
      }
      Matcher locMatcher = LOC_ATTR_PATTERN.matcher(impBlock);
      if (locMatcher.find()) {
        String normImp = normalizeLocation(locMatcher.group(1));
        importedKeys.add(normImp);
        importedKeys.add(basename(normImp));
      }
      Matcher nameMatcher = NAME_ATTR_PATTERN.matcher(impBlock);
      if (nameMatcher.find()) {
        String name = nameMatcher.group(1);
        importedKeys.add(name);
        importedKeys.add(basename(name));
      }
    }
  }

  private static String basename(String location) {
    int idx = location.lastIndexOf('/');
    return idx >= 0 ? location.substring(idx + 1) : location;
  }
}
