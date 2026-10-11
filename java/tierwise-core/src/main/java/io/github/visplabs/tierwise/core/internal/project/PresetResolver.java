package io.github.visplabs.tierwise.core.internal.project;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.CompileOptions;
import io.github.visplabs.tierwise.core.PresetFetcher;
import io.github.visplabs.tierwise.core.internal.json.Sha256;
import io.github.visplabs.tierwise.core.internal.yaml.PMap;
import io.github.visplabs.tierwise.core.internal.yaml.YamlLoadResult;
import io.github.visplabs.tierwise.core.internal.yaml.YamlLoader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves the items of {@code extends} to parsed presets, in order (MANIFEST-19 to MANIFEST-30).
 *
 * <p>A built-in preset comes from the classpath. A relative path is resolved inside the project root
 * (MANIFEST-12). A URL preset is fetched through the {@link PresetFetcher} of the options, and its bytes
 * are verified against the pinned SHA-256 digest before any of their content is interpreted
 * (MANIFEST-29). The compiler opens no network connection itself.
 */
final class PresetResolver {

  /**
   * A parsed preset.
   *
   * @param name the name findings use: {@code tierwise:<name>}, the project-relative path, or the URL
   *     (FIND-18)
   * @param root the root mapping
   */
  record Preset(String name, PMap root) {
  }

  private final ProjectFiles files;
  private final CompileOptions options;
  private final Reporter reporter;

  PresetResolver(ProjectFiles files, CompileOptions options, Reporter reporter) {
    this.files = files;
    this.options = options;
    this.reporter = reporter;
  }

  /**
   * Resolves the presets.
   *
   * @param items the well-formed items of {@code extends}
   * @return the presets that could be read, in {@code extends} order
   */
  List<Preset> resolve(List<Manifest.ExtendsItem> items) {
    List<Preset> presets = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (Manifest.ExtendsItem item : items) {
      reporter.notePresetOrigin(presetName(item), item.index());
      load(item, seen).ifPresent(presets::add);
    }
    return presets;
  }

  /** The name that findings located in the preset use (FIND-18). */
  private String presetName(Manifest.ExtendsItem item) {
    return switch (item.kind()) {
      case BUILT_IN -> "tierwise:" + item.text();
      case PATH -> {
        ProjectFiles.Resolved resolved = files.resolve(item.text());
        yield resolved.name();
      }
      case URL -> item.text();
    };
  }

  private Optional<Preset> load(Manifest.ExtendsItem item, Set<String> seen) {
    return switch (item.kind()) {
      case BUILT_IN -> loadBuiltIn(item, seen);
      case PATH -> loadPath(item, seen);
      case URL -> loadUrl(item, seen);
    };
  }

  private boolean repeated(Manifest.ExtendsItem item, String identity, Set<String> seen) {
    if (seen.add(identity)) {
      return false;
    }
    reporter.at(Code.E_PRESET, item.node(), "the preset is named more than once in extends (MANIFEST-30)");
    return true;
  }

  private Optional<Preset> loadBuiltIn(Manifest.ExtendsItem item, Set<String> seen) {
    if (repeated(item, "built-in:" + item.text(), seen)) {
      return Optional.empty();
    }
    Optional<byte[]> bytes = BuiltInPresets.read(item.text());
    if (bytes.isEmpty()) {
      reporter.at(Code.E_PRESET, item.node(), "tierwise:" + item.text() + " is not a built-in preset (MANIFEST-22)");
      return Optional.empty();
    }
    return parse("tierwise:" + item.text(), bytes.get());
  }

  private Optional<Preset> loadPath(Manifest.ExtendsItem item, Set<String> seen) {
    ProjectFiles.Resolved resolved = files.resolve(item.text());
    if (resolved.status() == ProjectFiles.Status.OUTSIDE) {
      reporter.at(Code.E_MANIFEST, item.node(), "the preset path resolves outside the project root (MANIFEST-12)");
      return Optional.empty();
    }
    if (repeated(item, "path:" + resolved.name(), seen)) {
      return Optional.empty();
    }
    if (resolved.status() != ProjectFiles.Status.FILE) {
      reporter.at(Code.E_PRESET, item.node(), "the preset path names no file (MANIFEST-24)");
      return Optional.empty();
    }
    Optional<byte[]> bytes = files.read(resolved.path());
    if (bytes.isEmpty()) {
      reporter.at(Code.E_PRESET, item.node(), "the preset file cannot be read (MANIFEST-24)");
      return Optional.empty();
    }
    return parse(resolved.name(), bytes.get());
  }

  private Optional<Preset> loadUrl(Manifest.ExtendsItem item, Set<String> seen) {
    if (repeated(item, "url:" + item.sha256(), seen)) {
      return Optional.empty();
    }
    Optional<PresetFetcher> fetcher = options.presetFetcher();
    if (fetcher.isEmpty()) {
      reporter.at(Code.E_PRESET, item.node(), "no mechanism can retrieve the URL preset (MANIFEST-27)");
      return Optional.empty();
    }
    byte[] bytes;
    try {
      bytes = fetcher.get().fetch(item.text());
    } catch (java.io.IOException | RuntimeException e) {
      bytes = null;
    }
    if (bytes == null) {
      reporter.at(Code.E_PRESET, item.node(), "the URL preset cannot be retrieved (MANIFEST-27)");
      return Optional.empty();
    }
    if (!Sha256.hexDigest(bytes).equals(item.sha256())) {
      reporter.at(Code.E_PRESET, item.node(), "the retrieved preset does not match its sha256 digest (MANIFEST-28)");
      return Optional.empty();
    }
    return parse(item.text(), bytes);
  }

  private Optional<Preset> parse(String name, byte[] bytes) {
    YamlLoadResult result = YamlLoader.load(bytes, name, false, NfcKeys.schema());
    reporter.addAll(result.findings());
    return result.root().map(root -> new Preset(name, root));
  }
}
