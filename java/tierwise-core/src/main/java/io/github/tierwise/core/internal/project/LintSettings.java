package io.github.tierwise.core.internal.project;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.Severity;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The valid lint settings of the presets and the manifest, in layer order (LINT-4).
 *
 * <p>A setting that the loader reported as invalid is not here. The loader reports every setting that
 * names an engine error code under LINT-8, and it has no effect (LINT-9).
 *
 * @param layers the settings of each preset in {@code extends} order, then the manifest's
 */
public record LintSettings(List<Layer> layers) {

  /** The wildcard of LINT-10 (a). */
  public static final String NAMING_WILDCARD = "naming/*";

  /**
   * The settings of one preset or of the manifest.
   *
   * @param source the file name of the preset, or {@code tierwise.yaml}
   * @param settings the rule identifier or {@code naming/*}, and its severity; an empty severity is
   *     {@code off}
   */
  public record Layer(String source, Map<String, Optional<Severity>> settings) {

    /** Copies the settings. */
    public Layer {
      Objects.requireNonNull(source, "source");
      settings = Map.copyOf(settings);
    }
  }

  /** Copies the layers. */
  public LintSettings {
    layers = List.copyOf(layers);
  }

  /** The settings of a project that sets nothing. */
  public static LintSettings none() {
    return new LintSettings(List.of());
  }

  /**
   * Resolves the severity of a lint rule (LINT-4, LINT-5). The last layer that names the rule wins, and
   * within one layer an exact setting of a naming rule wins over the wildcard.
   *
   * @param ruleId a rule identifier of LINT-1
   * @return the severity, or empty when the rule is off or no setting names it
   */
  public Optional<Severity> severityOf(String ruleId) {
    Optional<Severity> resolved = Optional.empty();
    for (Layer layer : layers) {
      Optional<Severity> exact = layer.settings().get(ruleId);
      if (layer.settings().containsKey(ruleId)) {
        resolved = exact;
      } else if (ruleId.startsWith(Code.NAMING_PREFIX) && layer.settings().containsKey(NAMING_WILDCARD)) {
        resolved = layer.settings().get(NAMING_WILDCARD);
      }
    }
    return resolved;
  }
}
