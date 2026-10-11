package io.github.visplabs.tierwise.core.internal.check;

/**
 * Reports what the project loader found: every MANIFEST, SRC and NS fault, the findings about YAML
 * syntax and encoding, and the duplicate definitions across sources (section 1).
 *
 * <p>The loader does the work while it reads the project, because a fault such as an unreadable file
 * decides which sources exist. This stage moves its findings into the shared accumulator, first in the
 * pipeline, so that later stages see them (for example SCOPE-48 and the lint gate of LINT-11).
 */
public final class ManifestStage implements Stage {

  @Override
  public String name() {
    return "manifest";
  }

  @Override
  public void run(CheckContext context) {
    context.findings().addAll(context.project().findings());
  }
}
