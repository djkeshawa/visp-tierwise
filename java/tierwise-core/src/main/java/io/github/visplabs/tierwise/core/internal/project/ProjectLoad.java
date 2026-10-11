package io.github.visplabs.tierwise.core.internal.project;

import io.github.visplabs.tierwise.core.Finding;
import java.util.List;
import java.util.Objects;

/**
 * The outcome of {@link ProjectLoader#load}: the sources and every finding the loader determined.
 *
 * @param sources the sources, possibly incomplete when a finding dropped a part
 * @param findings the MANIFEST, SRC and NS findings and the findings about YAML syntax, in no particular
 *     order; the pipeline orders them (FIND-26)
 */
public record ProjectLoad(SourceSet sources, List<Finding> findings) {

  /** Validates the components. */
  public ProjectLoad {
    Objects.requireNonNull(sources, "sources");
    findings = List.copyOf(findings);
  }
}
