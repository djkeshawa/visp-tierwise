package io.github.tierwise.spring;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.CompileResult;
import io.github.tierwise.core.Tierwise;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Bundles for the tests. {@link #limit(int)} compiles a small project whose root value differs from call
 * to call, so that each value gives a different {@code bundleVersion}. The spec's conformance bundles are
 * read from {@code tierwise.spec.dir}, which the parent POM points at the repository's spec directory.
 */
final class TestBundles {

  /** A key with deny, a key with skip and no kind, and a service-sourced level named tenant. */
  private static final String SCHEMA = """
      decision_hierarchy:
        levels: [global, tenant]
        scopes:
          tenant: { nodes: open, values: service }
      keys:
        limit.max_items:
          type: integer
          min: 1
          max: 1000
          combine: min
          settable_at: [global, tenant]
          on_source_failure: deny
        theme.name:
          type: string
          combine: override
          default: light
          settable_at: [global, tenant]
          on_source_failure: skip
      """;

  private static final String MANIFEST = """
      specVersion: "0.1"
      sources:
        schema:
          - "schema/**/*.yaml"
        values:
          - file: "values/root.yaml"
            scope: global
      """;

  private TestBundles() {
  }

  /**
   * Compiles the small project with a root value for {@code limit.max_items}.
   *
   * @param maxItems the root value, 1 to 1000
   * @return the canonical bundle bytes
   */
  static byte[] limit(int maxItems) {
    Path root = null;
    try {
      root = Files.createTempDirectory("tierwise-starter-test");
      Files.writeString(root.resolve("tierwise.yaml"), MANIFEST, StandardCharsets.UTF_8);
      Files.createDirectories(root.resolve("schema"));
      Files.writeString(root.resolve("schema/core.yaml"), SCHEMA, StandardCharsets.UTF_8);
      Files.createDirectories(root.resolve("values"));
      Files.writeString(root.resolve("values/root.yaml"), "limit.max_items: " + maxItems + "\n", StandardCharsets.UTF_8);
      CompileResult result = Tierwise.compile(root);
      assertTrue(result.bundle().isPresent(), () -> "the test project must compile: " + result.findings());
      return result.bundle().get();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } finally {
      deleteQuietly(root);
    }
  }

  /**
   * Reads a conformance bundle.
   *
   * @param name the file name under {@code spec/conformance/bundles}
   * @return the bundle bytes
   */
  static byte[] spec(String name) {
    try {
      return Files.readAllBytes(specBundle(name));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Returns the path of a conformance bundle.
   *
   * @param name the file name under {@code spec/conformance/bundles}
   * @return the path
   */
  static Path specBundle(String name) {
    String dir = System.getProperty("tierwise.spec.dir");
    Path spec = dir != null ? Path.of(dir) : Path.of("..", "..", "spec");
    return spec.resolve("conformance").resolve("bundles").resolve(name).normalize();
  }

  private static void deleteQuietly(Path root) {
    if (root == null) {
      return;
    }
    try (Stream<Path> paths = Files.walk(root)) {
      paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
    } catch (IOException e) {
      // A temporary directory that stays behind is harmless.
    }
  }
}
