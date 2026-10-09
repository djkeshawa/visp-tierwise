package io.github.tierwise.core.internal.check;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.CompileOptions;
import io.github.tierwise.core.internal.model.Hierarchy;
import io.github.tierwise.core.internal.model.ScopeDef;
import io.github.tierwise.core.internal.project.ProjectLoad;
import io.github.tierwise.core.internal.project.ProjectLoader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Writes a small project, loads it, and runs {@link KindStage}, {@link KeyStage} and {@link ValueStage} over
 * a fixed hierarchy, so that these stages are tested without the hierarchy stage.
 *
 * <p>The hierarchy has the levels {@code global}, {@code division} (closed: north, south) and
 * {@code account} (open, service-sourced), and the axis {@code seller} (closed: acme).
 */
final class KeyValueHarness {

  /** The hierarchy that the harness gives the stages. */
  static final Hierarchy HIERARCHY = new Hierarchy(List.of("global", "division", "account"), List.of("seller"),
      Map.of(
          "division", ScopeDef.closedCatalog(ScopeDef.ValuesSource.FILES, List.of("north", "south"),
              ScopeDef.OnUnknownNode.ERROR),
          "account", ScopeDef.openCatalog(ScopeDef.ValuesSource.SERVICE),
          "seller", ScopeDef.closedCatalog(ScopeDef.ValuesSource.FILES, List.of("acme"),
              ScopeDef.OnUnknownNode.ERROR)));

  /** The declaration that the loader reads to split values files; it matches {@link #HIERARCHY}. */
  private static final String HIERARCHY_YAML = """
      decision_hierarchy:
        levels: [global, division, account]
        axes: [seller]
        scopes:
          division: { nodes: [north, south], on_unknown_node: error }
          account: { nodes: open, values: service }
          seller: { nodes: [acme], on_unknown_node: error }
      """;

  private final Path dir;
  private final StringBuilder valuesEntries = new StringBuilder();
  private final StringBuilder extra = new StringBuilder();
  private int schemaFiles;
  private boolean tolerateLoaderFindings;

  KeyValueHarness(Path dir) {
    this.dir = dir;
  }

  /** Adds a schema file {@code schema/NN.yaml}. Files are read in code point order of their names. */
  KeyValueHarness schema(String yaml) {
    write("schema/s" + (schemaFiles++) + ".yaml", yaml);
    return this;
  }

  /** Adds the root values file. */
  KeyValueHarness root(String yaml) {
    write("values/root.yaml", yaml);
    valuesEntries.append("    - file: values/root.yaml\n      scope: global\n");
    return this;
  }

  /** Adds a multi-node values file with the given name under {@code values/}. */
  KeyValueHarness nodes(String name, String yaml) {
    write("values/" + name, yaml);
    valuesEntries.append("    - file: values/").append(name).append('\n');
    return this;
  }

  /** Adds a multi-node values file {@code values/nodes.yaml}. */
  KeyValueHarness nodes(String yaml) {
    return nodes("nodes.yaml", yaml);
  }

  /** Adds a preset file and makes the manifest extend it. */
  KeyValueHarness preset(String path, String yaml) {
    write(path, yaml);
    extra.append("extends:\n  - ").append(path).append('\n');
    return this;
  }

  /** Lets the project have findings of the loader, such as a repeated declaration, which the stages do not report. */
  KeyValueHarness tolerateLoaderFindings() {
    tolerateLoaderFindings = true;
    return this;
  }

  /** Writes any file under the project. */
  KeyValueHarness write(String path, String content) {
    try {
      Path target = dir.resolve(path);
      Files.createDirectories(target.getParent());
      Files.writeString(target, content);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return this;
  }

  /** Runs the three stages with the fixed hierarchy. */
  CheckContext run() {
    return run(true);
  }

  /** Runs the three stages, with the fixed hierarchy or, when {@code hierarchy} is false, without one. */
  CheckContext run(boolean hierarchy) {
    StringBuilder manifest = new StringBuilder("specVersion: \"0.1\"\n").append(extra)
        .append("sources:\n  schema:\n    - \"schema/**/*.yaml\"\n");
    if (valuesEntries.length() > 0) {
      manifest.append("  values:\n").append(valuesEntries);
    }
    write("tierwise.yaml", manifest.toString());
    write("schema/a_hierarchy.yaml", HIERARCHY_YAML);
    ProjectLoad load = ProjectLoader.load(dir, CompileOptions.defaults());
    assertTrue(tolerateLoaderFindings || load.findings().isEmpty(), "the fixture project has loader findings: " + load.findings());
    CheckContext context = new CheckContext(load, CompileOptions.defaults());
    if (hierarchy) {
      context.parts().hierarchy(HIERARCHY);
    }
    new KindStage().run(context);
    new KeyStage().run(context);
    new ValueStage().run(context);
    return context;
  }
}
