package io.github.tierwise.core.internal.project;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tierwise.core.CompileOptions;
import io.github.tierwise.core.Finding;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** Writes a small project into a directory and loads it. */
final class ProjectFixture {

  /** A schema with two levels, one closed catalog, one service-sourced level and one key. */
  static final String SCHEMA = """
      decision_hierarchy:
        levels: [global, region]
        scopes:
          region:
            nodes: [north, south]
            on_unknown_node: error
      keys:
        checkout.allowed:
          type: boolean
          combine: any
          settable_at: [global, region]
      """;

  /** A manifest that selects {@code schema/core.yaml} and the values files of {@link #withBaseProject()}. */
  static final String MANIFEST = """
      specVersion: "0.1"
      sources:
        schema:
          - "schema/core.yaml"
        values:
          - file: "values/root.yaml"
            scope: global
          - file: "values/regions.yaml"
      """;

  private final Path root;

  ProjectFixture(Path root) {
    this.root = root;
  }

  Path root() {
    return root;
  }

  ProjectFixture file(String path, String content) {
    return bytes(path, content.getBytes(StandardCharsets.UTF_8));
  }

  ProjectFixture bytes(String path, byte[] content) {
    try {
      Path target = root.resolve(path);
      Files.createDirectories(target.getParent());
      Files.write(target, content);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return this;
  }

  ProjectFixture manifest(String content) {
    return file("tierwise.yaml", content);
  }

  /** The manifest, schema and two values files that are valid by themselves. */
  ProjectFixture withBaseProject() {
    return manifest(MANIFEST).file("schema/core.yaml", SCHEMA).file("values/root.yaml", "checkout.allowed: true\n")
        .file("values/regions.yaml", """
            region:
              north:
                checkout.allowed: true
            """);
  }

  ProjectLoad load() {
    return ProjectLoader.load(root, CompileOptions.defaults());
  }

  ProjectLoad load(CompileOptions options) {
    return ProjectLoader.load(root, options);
  }

  /** Describes a finding as {@code CODE file:line [path]}. */
  static String describe(Finding finding) {
    return finding.code() + " " + finding.file() + ":" + finding.line() + " " + finding.path();
  }

  /** The sorted descriptions of the findings of a load. */
  static List<String> descriptions(ProjectLoad load) {
    List<String> list = load.findings().stream().map(ProjectFixture::describe).collect(Collectors.toCollection(ArrayList::new));
    Collections.sort(list);
    return list;
  }

  /** Requires exactly the given findings, in any order. */
  static void assertFindings(ProjectLoad load, String... expected) {
    List<String> wanted = new ArrayList<>(List.of(expected));
    Collections.sort(wanted);
    assertEquals(wanted, descriptions(load));
  }

  static SourceDocument document(ProjectLoad load, String file) {
    return load.sources().documents().stream().filter(d -> d.file().equals(file)).findFirst()
        .orElseThrow(() -> new AssertionError("no document " + file + " in " + files(load)));
  }

  static List<String> files(ProjectLoad load) {
    return load.sources().documents().stream().map(SourceDocument::file).collect(Collectors.toList());
  }
}
