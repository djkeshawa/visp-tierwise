package io.github.tierwise.core.internal.check;

import io.github.tierwise.core.CompileOptions;
import io.github.tierwise.core.Finding;
import io.github.tierwise.core.internal.project.ProjectLoad;
import io.github.tierwise.core.internal.project.ProjectLoader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Writes a small project into a directory and runs the stages of this area over it: the manifest, the hierarchy
 * and the entities and facts. Other stages are left out, so each test sees only the findings of these stages.
 */
final class HierarchyFixture {

  /** The schema file that most tests use. */
  static final String SCHEMA = "schema/model.yaml";

  private HierarchyFixture() {
  }

  /**
   * Writes the files of a project. A file whose name starts with {@code schema/} is listed as a schema source;
   * a file whose name starts with {@code rules/} is not a source and needs no entry.
   *
   * @param dir the project directory
   * @param files the file contents by project-relative path
   * @throws IOException if a file cannot be written
   */
  static void write(Path dir, Map<String, String> files) throws IOException {
    Map<String, String> sorted = new TreeMap<>(files);
    StringBuilder manifest = new StringBuilder("specVersion: \"0.1\"\nsources:\n  schema:\n");
    for (String name : sorted.keySet()) {
      if (name.startsWith("schema/")) {
        manifest.append("    - ").append(name).append('\n');
      }
    }
    sorted.put("tierwise.yaml", manifest.toString());
    for (Map.Entry<String, String> file : sorted.entrySet()) {
      Path path = dir.resolve(file.getKey());
      Files.createDirectories(path.getParent());
      Files.write(path, file.getValue().getBytes(StandardCharsets.UTF_8));
    }
  }

  /**
   * Writes a single schema file as the whole project.
   *
   * @param dir the project directory
   * @param schema the content of {@link #SCHEMA}
   * @return the directory
   * @throws IOException if a file cannot be written
   */
  static Path schema(Path dir, String schema) throws IOException {
    write(dir, Map.of(SCHEMA, schema));
    return dir;
  }

  /**
   * Runs the manifest, hierarchy and entity and fact stages over a project.
   *
   * @param dir the project directory
   * @return the context, holding the findings and the built parts
   */
  static CheckContext run(Path dir) {
    CompileOptions options = CompileOptions.defaults();
    ProjectLoad load = ProjectLoader.load(dir, options);
    CheckContext context = new CheckContext(load, options, dir);
    new ManifestStage().run(context);
    new HierarchyStage().run(context);
    new EntityFactStage().run(context);
    return context;
  }

  /**
   * Describes every finding of a context in a stable form: code, file, path and line.
   *
   * @param context the context
   * @return the descriptions in finding order
   */
  static List<String> describe(CheckContext context) {
    List<String> list = new ArrayList<>();
    for (Finding finding : context.findings().sorted()) {
      list.add(finding.code() + " " + finding.file() + " " + finding.path() + " " + finding.line());
    }
    return list;
  }

  /**
   * Returns the findings of a context whose code is one of the given codes, described as {@link #describe}.
   *
   * @param context the context
   * @param codes the codes
   * @return the matching descriptions
   */
  static List<String> describe(CheckContext context, String... codes) {
    List<String> wanted = List.of(codes);
    List<String> list = new ArrayList<>();
    for (String line : describe(context)) {
      if (wanted.contains(line.split(" ", 2)[0])) {
        list.add(line);
      }
    }
    return Collections.unmodifiableList(list);
  }
}
