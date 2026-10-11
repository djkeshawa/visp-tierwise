package io.github.visplabs.tierwise.core.internal.project;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.internal.yaml.PNode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Decides which files the manifest selects, and under which role (SRC-12 to SRC-20, SRC-34, MANIFEST-12,
 * NS-8, NS-9).
 *
 * <p>Globs select files, a path-mapped entry selects files and captures names, and a root entry, a
 * multi-node entry and a {@code namespaces} entry name a file directly. A file that the manifest selects
 * more than once is resolved in manifest order: a later selection under another role is
 * {@code E_SOURCE_MAPPING} (SRC-20 (a)), a later selection by another values item is
 * {@code E_SOURCE_MAPPING} (SRC-34), and the finding sits at the later selection. A file matched twice
 * under one schema or rules role is selected once (SRC-20 (b)), and a schema file that a namespace lists
 * is one namespaced file (SRC-20 (c)).
 */
final class SourceSelector {

  /**
   * A file the manifest selects.
   *
   * @param file the normalised project-relative path
   * @param path the file system path
   * @param role the role
   * @param namespace the namespace, for a namespaced schema file
   * @param values the values entry, for a values file
   * @param captures the names that a path-mapped entry captured
   */
  record Selected(String file, Path path, SourceRole role, Optional<String> namespace,
      Optional<Manifest.ValuesEntry> values, Optional<PathPattern.Captures> captures) {
  }

  /** One selection before conflicts are resolved. */
  private record Candidate(String file, Path path, SourceRole role, Optional<String> namespace,
      Optional<Manifest.ValuesEntry> values, Optional<PathPattern.Captures> captures, PNode locator) {

    Selected toSelected(Optional<String> mergedNamespace) {
      return new Selected(file, path, role, mergedNamespace, values, captures);
    }
  }

  private final ProjectFiles files;
  private final Reporter reporter;

  SourceSelector(ProjectFiles files, Reporter reporter) {
    this.files = files;
    this.reporter = reporter;
  }

  /**
   * Selects the files.
   *
   * @param manifest the manifest parts
   * @return the selected files in code-point order of their paths
   */
  List<Selected> select(Manifest manifest) {
    List<Candidate> candidates = new ArrayList<>();
    for (Manifest.Glob glob : manifest.schemaGlobs()) {
      glob(glob, SourceRole.SCHEMA, candidates);
    }
    for (Manifest.Glob glob : manifest.ruleGlobs()) {
      glob(glob, SourceRole.RULES, candidates);
    }
    for (Manifest.ValuesEntry entry : manifest.valuesEntries()) {
      values(entry, candidates);
    }
    for (Manifest.NamespaceEntry entry : manifest.namespaces()) {
      namespace(entry, candidates);
    }
    return resolveConflicts(candidates);
  }

  private void glob(Manifest.Glob glob, SourceRole role, List<Candidate> into) {
    String text = glob.pattern();
    if (text.startsWith("/") || text.startsWith("\\")) {
      reporter.at(Code.E_MANIFEST, glob.node(), "a glob pattern is relative to the project root (SRC-18, MANIFEST-12)");
      return;
    }
    GlobPattern pattern = GlobPattern.parse(text);
    for (String relative : files.list(pattern.directoryPrefix())) {
      if (pattern.matches(relative)) {
        ProjectFiles.Resolved resolved = files.resolve(relative);
        if (resolved.status() == ProjectFiles.Status.FILE) {
          into.add(new Candidate(resolved.name(), resolved.path(), role, Optional.empty(), Optional.empty(),
              Optional.empty(), glob.node()));
        } else if (resolved.status() == ProjectFiles.Status.OUTSIDE) {
          outside(glob.node());
        }
      }
    }
  }

  private void values(Manifest.ValuesEntry entry, List<Candidate> into) {
    if (entry.kind() == Manifest.ValuesKind.PATH_MAPPED) {
      pathMapped(entry, into);
      return;
    }
    ProjectFiles.Resolved resolved = files.resolve(entry.text());
    if (resolved.status() != ProjectFiles.Status.FILE) {
      directFault(resolved, entry.locator());
      return;
    }
    into.add(new Candidate(resolved.name(), resolved.path(), SourceRole.VALUES, Optional.empty(),
        Optional.of(entry), Optional.empty(), entry.node()));
  }

  private void pathMapped(Manifest.ValuesEntry entry, List<Candidate> into) {
    PathPattern pattern = entry.pattern();
    for (String relative : files.list(pattern.directoryPrefix())) {
      Optional<PathPattern.Captures> captures = pattern.match(relative);
      if (captures.isEmpty()) {
        continue;
      }
      ProjectFiles.Resolved resolved = files.resolve(relative);
      if (resolved.status() == ProjectFiles.Status.FILE) {
        into.add(new Candidate(resolved.name(), resolved.path(), SourceRole.VALUES, Optional.empty(),
            Optional.of(entry), captures, entry.node()));
      } else if (resolved.status() == ProjectFiles.Status.OUTSIDE) {
        outside(entry.locator());
      }
    }
  }

  private void namespace(Manifest.NamespaceEntry entry, List<Candidate> into) {
    ProjectFiles.Resolved resolved = files.resolve(entry.schemaPath());
    if (resolved.status() != ProjectFiles.Status.FILE) {
      directFault(resolved, entry.schemaNode());
      return;
    }
    into.add(new Candidate(resolved.name(), resolved.path(), SourceRole.SCHEMA, Optional.of(entry.name()),
        Optional.empty(), Optional.empty(), entry.node()));
  }

  private void directFault(ProjectFiles.Resolved resolved, PNode locator) {
    if (resolved.status() == ProjectFiles.Status.OUTSIDE) {
      outside(locator);
    } else {
      reporter.at(Code.E_MANIFEST, locator, "the file does not exist or is a directory (SRC-17)");
    }
  }

  private void outside(PNode locator) {
    reporter.at(Code.E_MANIFEST, locator, "the path resolves outside the project root (MANIFEST-12)");
  }

  private List<Selected> resolveConflicts(List<Candidate> candidates) {
    Map<String, List<Candidate>> byFile = new LinkedHashMap<>();
    for (Candidate candidate : candidates) {
      byFile.computeIfAbsent(candidate.file(), name -> new ArrayList<>()).add(candidate);
    }
    List<Selected> selected = new ArrayList<>();
    for (List<Candidate> group : byFile.values()) {
      group.sort(Comparator.comparingInt((Candidate c) -> c.locator().line())
          .thenComparing(c -> c.locator().path(), PathOrder.ORDER));
      resolveFile(group).ifPresent(selected::add);
    }
    selected.sort(Comparator.comparing(Selected::file, CodePointOrder.ORDER));
    return selected;
  }

  private Optional<Selected> resolveFile(List<Candidate> group) {
    Candidate primary = group.get(0);
    Optional<String> namespace = primary.namespace();
    for (Candidate later : group.subList(1, group.size())) {
      if (later.role() != primary.role()) {
        reporter.at(Code.E_SOURCE_MAPPING, later.locator(),
            "the file is selected under two roles, " + primary.role() + " and " + later.role() + " (SRC-20)");
      } else if (primary.role() == SourceRole.VALUES) {
        reporter.at(Code.E_SOURCE_MAPPING, later.locator(), "the values file is selected by more than one item (SRC-34)");
      } else if (later.namespace().isPresent()) {
        if (namespace.isPresent() && !namespace.equals(later.namespace())) {
          reporter.at(Code.E_MANIFEST, later.locator(), "the schema file is listed under more than one namespace (NS-9)");
        } else {
          namespace = later.namespace();
        }
      }
    }
    return Optional.of(primary.toSelected(primary.role() == SourceRole.SCHEMA ? namespace : Optional.empty()));
  }
}
