package io.github.visplabs.tierwise.core.internal.project;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.internal.model.Model;
import io.github.visplabs.tierwise.core.internal.yaml.PMap;
import io.github.visplabs.tierwise.core.internal.yaml.PNode;
import io.github.visplabs.tierwise.core.internal.yaml.PSeq;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Checks the shape of the manifest and extracts its parts (MANIFEST-1 to MANIFEST-18, MANIFEST-19 to
 * MANIFEST-26, SRC-17 to SRC-23, NS-1, NS-2, NS-9). It reports every fault it finds and reads on, so a
 * fault in one part does not hide a fault in another (FIND-3).
 */
final class ManifestReader {

  private static final Set<String> TOP_LEVEL =
      Set.of("specVersion", "extends", "sources", "schema", "values", "rules", "namespaces", "lint");
  private static final Set<String> SOURCE_ROLES = Set.of("schema", "values", "rules");
  private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

  private final Reporter reporter;

  ManifestReader(Reporter reporter) {
    this.reporter = reporter;
  }

  /**
   * Reads the manifest.
   *
   * @param root the manifest root
   * @return the parts that passed their shape checks
   */
  Manifest read(PMap root) {
    for (Map.Entry<String, PNode> entry : root.entries().entrySet()) {
      if (!TOP_LEVEL.contains(entry.getKey())) {
        reporter.at(Code.E_MANIFEST, entry.getValue(),
            "the manifest has the top-level key " + entry.getKey() + ", which is not allowed (MANIFEST-3)");
      }
    }
    readSpecVersion(root);
    List<Manifest.ExtendsItem> extendsItems = readExtends(root);
    SourcesReader sources = new SourcesReader(reporter);
    sources.read(root.get("sources").orElse(null));
    return new Manifest(root, extendsItems, sources.schemaGlobs(), sources.ruleGlobs(), sources.valuesEntries(),
        inlineMapping(root, "schema", "MANIFEST-14"), inlineMapping(root, "values", "MANIFEST-15"),
        readInlineRules(root), readNamespaces(root), root.get("lint"));
  }

  private void readSpecVersion(PMap root) {
    Optional<PNode> node = root.get("specVersion");
    if (node.isEmpty()) {
      reporter.at(Code.E_MANIFEST, root, "the manifest has no specVersion (MANIFEST-4)");
      return;
    }
    Optional<String> text = Names.text(node.get());
    if (text.isEmpty()) {
      reporter.at(Code.E_MANIFEST, node.get(), "specVersion is not a string; quote it (MANIFEST-4)");
    } else if (!text.get().equals(Model.SPEC_VERSION)) {
      reporter.at(Code.E_MANIFEST, node.get(),
          "specVersion " + text.get() + " is not a version this compiler implements (MANIFEST-5, MANIFEST-8)");
    }
  }

  private List<Manifest.ExtendsItem> readExtends(PMap root) {
    List<Manifest.ExtendsItem> items = new ArrayList<>();
    Optional<PNode> node = root.get("extends");
    if (node.isEmpty()) {
      return items;
    }
    if (!(node.get() instanceof PSeq list)) {
      reporter.at(Code.E_MANIFEST, node.get(), "extends is not a list (MANIFEST-19)");
      return items;
    }
    for (int i = 0; i < list.items().size(); i++) {
      readExtendsItem(i, list.items().get(i)).ifPresent(items::add);
    }
    return items;
  }

  private Optional<Manifest.ExtendsItem> readExtendsItem(int index, PNode item) {
    Optional<String> text = Names.text(item);
    if (text.isPresent()) {
      String reference = text.get();
      if (reference.startsWith("tierwise:")) {
        return Optional.of(new Manifest.ExtendsItem(index, item, Manifest.PresetKind.BUILT_IN,
            reference.substring("tierwise:".length()), null));
      }
      return Optional.of(new Manifest.ExtendsItem(index, item, Manifest.PresetKind.PATH, reference, null));
    }
    if (item instanceof PMap mapping) {
      return readUrlReference(index, mapping);
    }
    reporter.at(Code.E_MANIFEST, item, "an extends item is neither a preset reference nor a URL mapping (MANIFEST-20)");
    return Optional.empty();
  }

  private Optional<Manifest.ExtendsItem> readUrlReference(int index, PMap item) {
    if (!item.entries().keySet().equals(Set.of("url", "sha256"))) {
      reporter.at(Code.E_MANIFEST, item, "a URL preset reference has exactly the keys url and sha256 (MANIFEST-26)");
      return Optional.empty();
    }
    boolean valid = true;
    PNode urlNode = item.get("url").orElseThrow();
    Optional<String> url = Names.text(urlNode);
    if (url.isEmpty() || !isHttps(url.get())) {
      reporter.at(Code.E_MANIFEST, urlNode, "the url of a URL preset is not an https URL (MANIFEST-26)");
      valid = false;
    }
    PNode digestNode = item.get("sha256").orElseThrow();
    Optional<String> digest = Names.text(digestNode);
    if (digest.isEmpty() || !SHA256.matcher(digest.get()).matches()) {
      reporter.at(Code.E_MANIFEST, digestNode,
          "the sha256 of a URL preset is not 64 lower-case hexadecimal digits (MANIFEST-26)");
      valid = false;
    }
    return valid
        ? Optional.of(new Manifest.ExtendsItem(index, item, Manifest.PresetKind.URL, url.get(), digest.get()))
        : Optional.empty();
  }

  private static boolean isHttps(String text) {
    try {
      URI uri = new URI(text);
      return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
    } catch (java.net.URISyntaxException e) {
      return false;
    }
  }

  private Optional<PMap> inlineMapping(PMap root, String key, String clause) {
    Optional<PNode> node = root.get(key);
    if (node.isEmpty()) {
      return Optional.empty();
    }
    if (node.get() instanceof PMap mapping) {
      return Optional.of(mapping);
    }
    reporter.at(Code.E_MANIFEST, node.get(), "inline " + key + " content is not a mapping (" + clause + ")");
    return Optional.empty();
  }

  private List<PMap> readInlineRules(PMap root) {
    List<PMap> rules = new ArrayList<>();
    Optional<PNode> node = root.get("rules");
    if (node.isEmpty()) {
      return rules;
    }
    if (!(node.get() instanceof PSeq list)) {
      reporter.at(Code.E_MANIFEST, node.get(), "inline rules content is not a list (MANIFEST-16)");
      return rules;
    }
    for (PNode item : list.items()) {
      if (item instanceof PMap mapping) {
        rules.add(mapping);
      } else {
        reporter.at(Code.E_MANIFEST, item, "an inline rules item is not a mapping (MANIFEST-16)");
      }
    }
    return rules;
  }

  private List<Manifest.NamespaceEntry> readNamespaces(PMap root) {
    List<Manifest.NamespaceEntry> entries = new ArrayList<>();
    Optional<PNode> node = root.get("namespaces");
    if (node.isEmpty()) {
      return entries;
    }
    if (!(node.get() instanceof PMap mapping)) {
      reporter.at(Code.E_MANIFEST, node.get(), "namespaces is not a mapping (NS-1)");
      return entries;
    }
    Map<String, PNode> byName = new LinkedHashMap<>(mapping.entries());
    for (Map.Entry<String, PNode> entry : byName.entrySet()) {
      readNamespace(entry.getKey(), entry.getValue()).ifPresent(entries::add);
    }
    return entries;
  }

  private Optional<Manifest.NamespaceEntry> readNamespace(String name, PNode entry) {
    boolean valid = true;
    if (name.isEmpty() || name.indexOf('.') >= 0) {
      reporter.at(Code.E_MANIFEST, entry, "a namespace name is non-empty and has no dot (NS-9)");
      valid = false;
    }
    if (!(entry instanceof PMap mapping)) {
      reporter.at(Code.E_MANIFEST, entry, "a namespaces entry is a mapping with a schema member (NS-2)");
      return Optional.empty();
    }
    for (Map.Entry<String, PNode> member : mapping.entries().entrySet()) {
      if (!member.getKey().equals("schema")) {
        reporter.at(Code.E_MANIFEST, member.getValue(), "a namespaces entry has only the key schema (NS-2)");
        valid = false;
      }
    }
    Optional<PNode> schema = mapping.get("schema");
    if (schema.isEmpty()) {
      reporter.at(Code.E_MANIFEST, mapping, "a namespaces entry has no schema member (NS-2)");
      return Optional.empty();
    }
    Optional<String> path = Names.text(schema.get());
    if (path.isEmpty()) {
      reporter.at(Code.E_MANIFEST, schema.get(), "the schema of a namespaces entry is not a string (NS-2)");
      return Optional.empty();
    }
    return valid ? Optional.of(new Manifest.NamespaceEntry(name, mapping, schema.get(), path.get()))
        : Optional.empty();
  }

  /** Reads {@code sources} (MANIFEST-10, MANIFEST-11, SRC-21, SRC-23). */
  private static final class SourcesReader {

    private final Reporter reporter;
    private final List<Manifest.Glob> schemaGlobs = new ArrayList<>();
    private final List<Manifest.Glob> ruleGlobs = new ArrayList<>();
    private final List<Manifest.ValuesEntry> valuesEntries = new ArrayList<>();

    SourcesReader(Reporter reporter) {
      this.reporter = reporter;
    }

    List<Manifest.Glob> schemaGlobs() {
      return schemaGlobs;
    }

    List<Manifest.Glob> ruleGlobs() {
      return ruleGlobs;
    }

    List<Manifest.ValuesEntry> valuesEntries() {
      return valuesEntries;
    }

    void read(PNode node) {
      if (node == null) {
        return;
      }
      if (!(node instanceof PMap sources)) {
        reporter.at(Code.E_MANIFEST, node, "sources is not a mapping (MANIFEST-10)");
        return;
      }
      for (Map.Entry<String, PNode> entry : sources.entries().entrySet()) {
        if (!SOURCE_ROLES.contains(entry.getKey())) {
          reporter.at(Code.E_MANIFEST, entry.getValue(),
              "sources has the key " + entry.getKey() + ", which is not allowed (MANIFEST-10)");
        }
      }
      readGlobs(sources.get("schema").orElse(null), "schema", schemaGlobs);
      readGlobs(sources.get("rules").orElse(null), "rules", ruleGlobs);
      readValues(sources.get("values").orElse(null));
    }

    private void readGlobs(PNode node, String role, List<Manifest.Glob> into) {
      if (node == null) {
        return;
      }
      if (!(node instanceof PSeq list)) {
        reporter.at(Code.E_MANIFEST, node, "sources." + role + " is not a list of glob patterns (MANIFEST-11)");
        return;
      }
      for (PNode item : list.items()) {
        Optional<String> text = Names.text(item);
        if (text.isPresent()) {
          into.add(new Manifest.Glob(item, text.get()));
        } else {
          reporter.at(Code.E_MANIFEST, item, "a sources." + role + " item is not a string (MANIFEST-11)");
        }
      }
    }

    private void readValues(PNode node) {
      if (node == null) {
        return;
      }
      if (!(node instanceof PSeq list)) {
        reporter.at(Code.E_MANIFEST, node, "sources.values is not a list (MANIFEST-11)");
        return;
      }
      for (PNode item : list.items()) {
        readValuesItem(item);
      }
    }

    private void readValuesItem(PNode item) {
      if (!(item instanceof PMap entry)) {
        reporter.at(Code.E_SOURCE_MAPPING, item, "a values item is a mapping (SRC-21)");
        return;
      }
      Set<String> keys = entry.entries().keySet();
      if (keys.equals(Set.of("pattern"))) {
        readPathMapped(entry);
      } else if (keys.equals(Set.of("file", "scope"))) {
        readFile(entry, Manifest.ValuesKind.ROOT);
      } else if (keys.equals(Set.of("file"))) {
        readFile(entry, Manifest.ValuesKind.MULTI_NODE);
      } else {
        reporter.at(Code.E_SOURCE_MAPPING, item,
            "a values item is {pattern}, {file, scope} or {file} (SRC-21)");
      }
    }

    private void readPathMapped(PMap entry) {
      PNode pattern = entry.get("pattern").orElseThrow();
      Optional<String> text = Names.text(pattern);
      if (text.isEmpty()) {
        reporter.at(Code.E_SOURCE_MAPPING, pattern, "the pattern of a values entry is a string (SRC-21)");
        return;
      }
      Optional<PathPattern> parsed = PathPattern.parse(text.get());
      if (parsed.isEmpty()) {
        reporter.at(Code.E_SOURCE_MAPPING, pattern,
            "a path pattern has exactly one {scope} and one {node} in different segments and no wildcard (SRC-23)");
        return;
      }
      valuesEntries.add(new Manifest.ValuesEntry(entry, Manifest.ValuesKind.PATH_MAPPED, pattern, text.get(),
          Optional.empty(), null, parsed.get()));
    }

    private void readFile(PMap entry, Manifest.ValuesKind kind) {
      PNode file = entry.get("file").orElseThrow();
      Optional<String> text = Names.text(file);
      if (text.isEmpty()) {
        reporter.at(Code.E_SOURCE_MAPPING, file, "the file of a values entry is a string (SRC-21)");
        return;
      }
      if (kind == Manifest.ValuesKind.MULTI_NODE) {
        valuesEntries.add(new Manifest.ValuesEntry(entry, kind, file, text.get(), Optional.empty(), null, null));
        return;
      }
      PNode scope = entry.get("scope").orElseThrow();
      Optional<String> scopeName = Names.text(scope);
      if (scopeName.isEmpty()) {
        reporter.at(Code.E_SOURCE_MAPPING, scope, "the scope of a root entry is a string (SRC-21)");
        return;
      }
      valuesEntries.add(new Manifest.ValuesEntry(entry, kind, file, text.get(),
          Optional.of(Names.nfc(scopeName.get())), scope, null));
    }
  }
}
