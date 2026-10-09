package io.github.tierwise.core.internal.lint;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.CompileOptions;
import io.github.tierwise.core.internal.model.EntityDef;
import io.github.tierwise.core.internal.model.FactDef;
import io.github.tierwise.core.internal.model.FeatureDef;
import io.github.tierwise.core.internal.model.Hierarchy;
import io.github.tierwise.core.internal.model.KeyDef;
import io.github.tierwise.core.internal.model.Model;
import io.github.tierwise.core.internal.model.Values;
import io.github.tierwise.core.internal.project.ProjectLoad;
import io.github.tierwise.core.internal.project.ProjectLoader;
import io.github.tierwise.core.internal.project.SourceSet;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Writes small projects and builds the model parts that the lint rule tests need. */
final class LintFixture {

  /** A manifest with one schema glob, one root values file and one rules glob. */
  static final String MANIFEST = """
      specVersion: "0.1"
      sources:
        schema:
          - "schema/**/*.yaml"
        values:
          - file: "values/root.yaml"
            scope: global
        rules:
          - "rules/**/*.yaml"
      """;

  private LintFixture() {
  }

  /**
   * Writes the files of a project, with the default manifest and an empty root values file unless the
   * files give them, and loads the sources, which must produce no finding.
   *
   * @param dir the project directory
   * @param files the files by path relative to the directory
   * @return the loaded sources
   */
  static SourceSet sources(Path dir, Map<String, String> files) {
    Map<String, String> all = new java.util.LinkedHashMap<>();
    all.put("tierwise.yaml", MANIFEST);
    all.put("values/root.yaml", "{}\n");
    all.putAll(files);
    for (Map.Entry<String, String> file : all.entrySet()) {
      try {
        Path target = dir.resolve(file.getKey());
        Files.createDirectories(target.getParent());
        Files.writeString(target, file.getValue(), StandardCharsets.UTF_8);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    ProjectLoad load = ProjectLoader.load(dir, CompileOptions.defaults());
    assertTrue(load.findings().isEmpty(), "the fixture must load cleanly: " + load.findings());
    return load.sources();
  }

  /**
   * Returns a model with one level, no declarations and no values.
   *
   * @return the model
   */
  static Model emptyModel() {
    return model(List.of("global"), Map.of(), Map.of(), Map.of(), Values.EMPTY, Map.of());
  }

  /**
   * Builds a model from its parts.
   *
   * @param levels the level names, the root first
   * @param entities the entities
   * @param facts the facts
   * @param keys the keys
   * @param values the authored values
   * @param features the features
   * @return the model
   */
  static Model model(List<String> levels, Map<String, EntityDef> entities, Map<String, FactDef> facts,
      Map<String, KeyDef> keys, Values values, Map<String, FeatureDef> features) {
    return new Model(Model.FORMAT_VERSION, Model.SPEC_VERSION, new Hierarchy(levels, List.of(), Map.of()),
        entities, facts, keys, values, features);
  }

  /**
   * Describes where a hit is located: the line and the path.
   *
   * @param hit the hit
   * @return the description
   */
  static String where(LintHit hit) {
    return hit.node().line() + " " + hit.node().path();
  }

  /**
   * Describes a finding: its rule, line and path.
   *
   * @param finding the finding
   * @return the description
   */
  static String describe(LintFinding finding) {
    return finding.ruleId() + " " + finding.node().line() + " " + finding.node().path();
  }
}
