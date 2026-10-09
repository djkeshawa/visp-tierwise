package io.github.tierwise.core.internal.yaml;

import io.github.tierwise.core.Finding;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of loading one YAML source.
 *
 * <p>The root is present when the source is one document with a mapping root and no file-level fault.
 * A source with a file-level fault has no root and only that fault as its finding (FIND-23). Otherwise
 * the findings are those of the nodes, and the root holds every node that was not dropped. A node is
 * dropped only together with the finding that drops it.
 *
 * @param root the root mapping, or empty for a file-level fault
 * @param findings the engine findings, in document order, with one finding for each finding key
 */
public record YamlLoadResult(Optional<PMap> root, List<Finding> findings) {

  /** Validates the components and copies the findings. */
  public YamlLoadResult {
    Objects.requireNonNull(root, "root");
    findings = List.copyOf(findings);
  }
}
