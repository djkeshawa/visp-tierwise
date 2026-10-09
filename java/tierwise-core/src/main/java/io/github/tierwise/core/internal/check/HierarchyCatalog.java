package io.github.tierwise.core.internal.check;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.internal.yaml.PMap;
import io.github.tierwise.core.internal.yaml.PNode;
import io.github.tierwise.core.internal.yaml.PSeq;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Locates and reads the generated node catalogs that a scope names with {@code nodes: {from: …}} (SCOPE-27 to
 * SCOPE-31).
 *
 * <p>A path is relative to the project directory and must resolve inside it. Symbolic links are resolved
 * before the containment check (SCOPE-28), as the project loader does for source files (MANIFEST-12). A path
 * that is absolute, outside, missing, or a directory is {@code E_SCOPE_DECLARATION} at the {@code from} member.
 * The file's own faults are located in the file (FIND-19 to FIND-23).
 */
final class HierarchyCatalog {

  /**
   * A catalog that could be read and has the shape of SCOPE-29.
   *
   * @param file the catalog's path relative to the project directory, with {@code /} separators
   * @param items the items of its {@code nodes} array, in document order
   */
  record Catalog(String file, List<PNode> items) {

    /** Copies the items. */
    Catalog {
      items = List.copyOf(items);
    }
  }

  private final Path root;
  private final Path realRoot;

  /**
   * Creates a locator for one project directory.
   *
   * @param projectRoot the directory that contains {@code tierwise.yaml}
   */
  HierarchyCatalog(Path projectRoot) {
    this.root = projectRoot.toAbsolutePath().normalize();
    Path real;
    try {
      real = projectRoot.toRealPath();
    } catch (IOException e) {
      real = root;
    }
    this.realRoot = real;
  }

  /**
   * Reads the catalog that a {@code from} path names.
   *
   * @param from the path as the source writes it, already known to be a non-empty string
   * @param fromNode the value of the {@code from} member, where a fault about the path is located
   * @param report the report that receives every fault
   * @return the catalog, or empty when it could not be read (a fault has then been reported)
   */
  Optional<Catalog> read(String from, PNode fromNode, ScopeReport report) {
    Optional<Path> located = locate(from, fromNode, report);
    if (located.isEmpty()) {
      return Optional.empty();
    }
    Path path = located.get();
    String file = root.relativize(path).toString().replace('\\', '/');
    byte[] bytes;
    try {
      bytes = Files.readAllBytes(path);
    } catch (IOException e) {
      report.at(Code.E_SCOPE_DECLARATION, fromNode, "the catalog cannot be read (SCOPE-28)");
      return Optional.empty();
    }
    HierarchyJson.Read read = HierarchyJson.read(file, bytes);
    read.findings().forEach(report::add);
    if (read.root().isEmpty()) {
      return Optional.empty();
    }
    return shape(file, read.root().get(), report);
  }

  /** Checks the shape of SCOPE-29 and SCOPE-30: an object whose only property is {@code nodes}, an array. */
  private static Optional<Catalog> shape(String file, PNode node, ScopeReport report) {
    if (!(node instanceof PMap catalog)) {
      report.at(Code.E_SCOPE_DECLARATION, node, "the catalog's top-level value is not an object (SCOPE-30)");
      return Optional.empty();
    }
    for (Map.Entry<String, PNode> entry : catalog.entries().entrySet()) {
      if (!entry.getKey().equals("nodes")) {
        report.at(Code.E_SCOPE_DECLARATION, entry.getValue(),
            "the catalog has a property " + entry.getKey() + " other than nodes (SCOPE-29, SCOPE-30)");
      }
    }
    Optional<PNode> nodes = catalog.get("nodes");
    if (nodes.isEmpty()) {
      report.at(Code.E_SCOPE_DECLARATION, catalog, "the catalog has no nodes property (SCOPE-30)");
      return Optional.empty();
    }
    if (!(nodes.get() instanceof PSeq list)) {
      report.at(Code.E_SCOPE_DECLARATION, nodes.get(), "the nodes property is not an array (SCOPE-29, SCOPE-30)");
      return Optional.empty();
    }
    return Optional.of(new Catalog(file, list.items()));
  }

  /** Resolves a {@code from} path to a regular file inside the project directory (SCOPE-27, SCOPE-28). */
  private Optional<Path> locate(String from, PNode fromNode, ScopeReport report) {
    if (from.startsWith("/") || from.startsWith("\\")) {
      report.at(Code.E_SCOPE_DECLARATION, fromNode, "the from path is absolute (SCOPE-27, SCOPE-28)");
      return Optional.empty();
    }
    Path candidate;
    try {
      candidate = root.resolve(from).normalize();
    } catch (InvalidPathException e) {
      report.at(Code.E_SCOPE_DECLARATION, fromNode, "the from path is not a file path (SCOPE-28)");
      return Optional.empty();
    }
    if (!candidate.startsWith(root) || candidate.equals(root)) {
      report.at(Code.E_SCOPE_DECLARATION, fromNode, "the from path leaves the project directory (SCOPE-27, SCOPE-28)");
      return Optional.empty();
    }
    if (!Files.exists(candidate)) {
      report.at(Code.E_SCOPE_DECLARATION, fromNode, "the from path names a missing file (SCOPE-28)");
      return Optional.empty();
    }
    try {
      if (!candidate.toRealPath().startsWith(realRoot)) {
        report.at(Code.E_SCOPE_DECLARATION, fromNode,
            "the from path resolves outside the project directory through a link (SCOPE-27, SCOPE-28)");
        return Optional.empty();
      }
    } catch (IOException e) {
      report.at(Code.E_SCOPE_DECLARATION, fromNode, "the from path names a missing file (SCOPE-28)");
      return Optional.empty();
    }
    if (!Files.isRegularFile(candidate)) {
      report.at(Code.E_SCOPE_DECLARATION, fromNode, "the from path names a directory (SCOPE-28)");
      return Optional.empty();
    }
    return Optional.of(candidate);
  }
}
