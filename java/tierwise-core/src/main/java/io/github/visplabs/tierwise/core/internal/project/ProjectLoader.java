package io.github.visplabs.tierwise.core.internal.project;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.CompileOptions;
import io.github.visplabs.tierwise.core.internal.yaml.PMap;
import io.github.visplabs.tierwise.core.internal.yaml.PNode;
import io.github.visplabs.tierwise.core.internal.yaml.YamlLoadResult;
import io.github.visplabs.tierwise.core.internal.yaml.YamlLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Loads a project: reads {@code tierwise.yaml}, resolves and checks presets, selects and parses source
 * files, and returns an immutable {@link SourceSet} with every MANIFEST, SRC and NS finding (section 1).
 *
 * <p>The loader reads every part it can and reports every fault it finds (FIND-3). A part that a fault
 * makes unusable is left out of the {@link SourceSet}, together with the finding that drops it. It
 * performs no network access: a URL preset goes through the {@code PresetFetcher} of the options
 * (MANIFEST-27).
 */
public final class ProjectLoader {

  /** The file name of the manifest and the {@code file} of every finding located in it (MANIFEST-1, FIND-17). */
  public static final String MANIFEST_FILE = "tierwise.yaml";

  private final ProjectFiles files;
  private final CompileOptions options;
  private final Reporter reporter = new Reporter();

  private ProjectLoader(Path projectRoot, CompileOptions options) {
    this.files = new ProjectFiles(projectRoot);
    this.options = options;
  }

  /**
   * Loads the project in a directory.
   *
   * @param projectRoot the directory that contains {@code tierwise.yaml}
   * @param options the compile options, which supply the preset fetcher
   * @return the sources and the findings
   */
  public static ProjectLoad load(Path projectRoot, CompileOptions options) {
    Objects.requireNonNull(projectRoot, "projectRoot");
    Objects.requireNonNull(options, "options");
    return new ProjectLoader(projectRoot, options).load();
  }

  private ProjectLoad load() {
    Optional<PMap> manifestRoot = readManifest();
    if (manifestRoot.isEmpty()) {
      return new ProjectLoad(SourceSet.empty(), reporter.findings());
    }
    Manifest manifest = new ManifestReader(reporter).read(manifestRoot.get());
    List<PresetResolver.Preset> presets = new PresetResolver(files, options, reporter).resolve(manifest.extendsItems());
    int projectLayer = presets.size();
    List<SourceSelector.Selected> selected = new SourceSelector(files, reporter).select(manifest);

    List<SourceDocument> documents = new ArrayList<>();
    for (PresetResolver.Preset preset : presets) {
      documents.add(presetDocument(preset, documents.size()));
    }
    List<SourceDocument> project = projectSchemas(manifest, selected, projectLayer);
    checkSchemaDocuments(concat(documents, project));
    HierarchyPeek hierarchy = HierarchyPeek.of(concat(documents, project));
    project.addAll(valuesAndRules(manifest, selected, projectLayer, hierarchy));
    project.sort(definitionOrder());
    documents.addAll(project);

    new DuplicateDefinitions(reporter).checkSchemas(roleOf(documents, SourceRole.SCHEMA));
    new DuplicateDefinitions(reporter).checkRules(roleOf(documents, SourceRole.RULES));
    LintSettings lint = readLint(manifest, documents, presets);
    return new ProjectLoad(new SourceSet(manifestRoot.get(), documents, lint, hierarchy.rootLevel()),
        reporter.findings());
  }

  private List<SourceDocument> projectSchemas(Manifest manifest, List<SourceSelector.Selected> selected, int layer) {
    List<SourceDocument> schemas = new ArrayList<>();
    manifest.inlineSchema().ifPresent(inline -> schemas.add(inlineDocument(inline, SourceRole.SCHEMA, layer)));
    for (SourceSelector.Selected file : selected) {
      if (file.role() == SourceRole.SCHEMA) {
        loadSchemaFile(file, layer).ifPresent(schemas::add);
      }
    }
    return schemas;
  }

  private List<SourceDocument> valuesAndRules(Manifest manifest, List<SourceSelector.Selected> selected, int layer,
      HierarchyPeek hierarchy) {
    ValuesChecks checks = new ValuesChecks(reporter, hierarchy);
    List<SourceDocument> documents = new ArrayList<>();
    manifest.inlineValues().ifPresent(inline -> documents.add(inlineValues(inline, layer, checks)));
    for (PMap rules : manifest.inlineRules()) {
      documents.add(inlineDocument(rules, SourceRole.RULES, layer));
    }
    for (SourceSelector.Selected file : selected) {
      if (file.role() == SourceRole.VALUES) {
        loadValuesFile(file, layer, hierarchy, checks).ifPresent(documents::add);
      } else if (file.role() == SourceRole.RULES) {
        loadRulesFile(file, layer).ifPresent(documents::add);
      }
    }
    return documents;
  }

  private static List<SourceDocument> concat(List<SourceDocument> first, List<SourceDocument> second) {
    List<SourceDocument> all = new ArrayList<>(first);
    all.addAll(second);
    return all;
  }

  // ---- the manifest ---------------------------------------------------------------------------

  private Optional<PMap> readManifest() {
    ProjectFiles.Resolved resolved = files.resolve(MANIFEST_FILE);
    if (resolved.status() != ProjectFiles.Status.FILE) {
      reporter.atFile(Code.E_MANIFEST, MANIFEST_FILE, "the project root contains no manifest tierwise.yaml (MANIFEST-1, MANIFEST-2)");
      return Optional.empty();
    }
    Optional<byte[]> bytes = files.read(resolved.path());
    if (bytes.isEmpty()) {
      reporter.atFile(Code.E_MANIFEST, MANIFEST_FILE, "the manifest cannot be read (MANIFEST-2)");
      return Optional.empty();
    }
    YamlLoadResult result = YamlLoader.load(bytes.get(), MANIFEST_FILE, true, NfcKeys.manifest());
    reporter.addAll(result.findings());
    return result.root();
  }

  // ---- documents ------------------------------------------------------------------------------

  private static SourceDocument presetDocument(PresetResolver.Preset preset, int layer) {
    return new SourceDocument(preset.name(), Origin.PRESET, layer, SourceRole.SCHEMA, Optional.empty(),
        Optional.empty(), preset.root(), List.of());
  }

  private static SourceDocument inlineDocument(PMap inline, SourceRole role, int layer) {
    return new SourceDocument(inline.file(), Origin.INLINE, layer, role, Optional.empty(), Optional.empty(), inline,
        List.of());
  }

  private SourceDocument inlineValues(PMap inline, int layer, ValuesChecks checks) {
    ValuesMapping mapping = new ValuesMapping.MultiNode();
    return new SourceDocument(inline.file(), Origin.INLINE, layer, SourceRole.VALUES, Optional.empty(),
        Optional.of(mapping), inline, checks.groups(inline, mapping));
  }

  private Optional<SourceDocument> loadSchemaFile(SourceSelector.Selected file, int layer) {
    return parse(file, NfcKeys.schema()).map(root -> new SourceDocument(file.file(), Origin.FILE, layer,
        SourceRole.SCHEMA, file.namespace(), Optional.empty(), root, List.of()));
  }

  private Optional<SourceDocument> loadRulesFile(SourceSelector.Selected file, int layer) {
    return parse(file, path -> false).map(root -> new SourceDocument(file.file(), Origin.FILE, layer,
        SourceRole.RULES, Optional.empty(), Optional.empty(), root, List.of()));
  }

  private Optional<SourceDocument> loadValuesFile(SourceSelector.Selected file, int layer, HierarchyPeek hierarchy,
      ValuesChecks checks) {
    Manifest.ValuesEntry entry = file.values().orElseThrow();
    ValuesMapping mapping;
    switch (entry.kind()) {
      case PATH_MAPPED -> {
        PathPattern.Captures captures = file.captures().orElseThrow();
        String scope = Names.nfc(captures.scope());
        if (!checks.capturedScopeAllowed(scope, entry.locator())) {
          return Optional.empty();
        }
        mapping = new ValuesMapping.PathMapped(scope, Names.nfc(captures.node()));
      }
      case ROOT -> {
        if (!checks.rootEntryAllowed(entry.scope().orElseThrow(), entry.scopeNode())) {
          return Optional.empty();
        }
        mapping = new ValuesMapping.RootEntry(entry.scope().orElseThrow(), entry.scopeNode());
      }
      default -> mapping = new ValuesMapping.MultiNode();
    }
    Predicate<List<Object>> nfc = mapping instanceof ValuesMapping.MultiNode
        ? NfcKeys.multiNodeValues(hierarchy.rootLevel().orElse(null))
        : path -> false;
    return parse(file, nfc).map(root -> new SourceDocument(file.file(), Origin.FILE, layer, SourceRole.VALUES,
        Optional.empty(), Optional.of(mapping), root, checks.groups(root, mapping)));
  }

  private Optional<PMap> parse(SourceSelector.Selected file, Predicate<List<Object>> nfcKeysAt) {
    Optional<byte[]> bytes = files.read(file.path());
    if (bytes.isEmpty()) {
      reporter.atFile(Code.E_PARSE, file.file(), "the file cannot be read (SRC-10)");
      return Optional.empty();
    }
    YamlLoadResult result = YamlLoader.load(bytes.get(), file.file(), false, nfcKeysAt);
    reporter.addAll(result.findings());
    return result.root();
  }

  private static Comparator<SourceDocument> definitionOrder() {
    // Inline content comes first (FIND-32), then files in code-point order of their paths.
    return Comparator.<SourceDocument, Integer>comparing(document -> document.origin() == Origin.INLINE ? 0 : 1)
        .thenComparing(SourceDocument::file, CodePointOrder.ORDER);
  }

  private static List<SourceDocument> roleOf(List<SourceDocument> documents, SourceRole role) {
    List<SourceDocument> selected = new ArrayList<>();
    for (SourceDocument document : documents) {
      if (document.role() == role) {
        selected.add(document);
      }
    }
    return selected;
  }

  // ---- checks over the schema documents ----------------------------------------------------------

  private void checkSchemaDocuments(List<SourceDocument> schemas) {
    SchemaChecks checks = new SchemaChecks(reporter);
    for (SourceDocument document : schemas) {
      if (document.isPreset()) {
        checks.checkPreset(document.root());
      } else if (document.isNamespaced()) {
        checks.checkNamespaced(document.root(), document.namespace().get());
      } else {
        checks.checkShared(document.root());
      }
    }
  }

  // ---- lint settings ----------------------------------------------------------------------------

  private LintSettings readLint(Manifest manifest, List<SourceDocument> documents, List<PresetResolver.Preset> presets) {
    Set<String> naming = new LinkedHashSet<>();
    for (SourceDocument document : documents) {
      if (document.role() == SourceRole.SCHEMA && !document.isNamespaced()
          && document.root().get("naming").orElse(null) instanceof PMap patterns) {
        naming.addAll(patterns.entries().keySet());
      }
    }
    List<LintSettings.Layer> layers = new ArrayList<>();
    LintSettingsReader presetReader = new LintSettingsReader(reporter, true, naming);
    for (PresetResolver.Preset preset : presets) {
      layers.add(presetReader.read(preset.name(), preset.root().get("lint").orElse(null)));
    }
    PNode lint = manifest.lint().orElse(null);
    layers.add(new LintSettingsReader(reporter, false, naming).read(MANIFEST_FILE, lint));
    return new LintSettings(layers);
  }
}
