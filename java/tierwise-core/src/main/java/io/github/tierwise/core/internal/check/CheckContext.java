package io.github.tierwise.core.internal.check;

import io.github.tierwise.core.CompileOptions;
import io.github.tierwise.core.internal.project.ProjectLoad;
import io.github.tierwise.core.internal.project.SourceSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The state that the stages of one compilation share: the loaded sources, the findings so far, the parts
 * of the model that are built, and any compile-time data that a stage hands to a later stage.
 *
 * <p>Compile-time-only data, such as the declared kinds or the policies of a feature, is not part of the
 * {@link io.github.tierwise.core.internal.model.Model}. A stage publishes it with {@link #provide(Class,
 * Object)}, keyed by its type, and a later stage reads it with {@link #find(Class)}. An absent entry means
 * that the producing stage did not build it, and the reader skips the checks that need it (FIND-35).
 */
public final class CheckContext {

  private final ProjectLoad project;
  private final CompileOptions options;
  private final Findings findings = new Findings();
  private final ModelParts parts = new ModelParts();
  private final Map<Class<?>, Object> provided = new HashMap<>();

  /**
   * Creates the context of a compilation.
   *
   * @param project the outcome of loading the project
   * @param options the compile options
   */
  public CheckContext(ProjectLoad project, CompileOptions options) {
    this.project = Objects.requireNonNull(project, "project");
    this.options = Objects.requireNonNull(options, "options");
  }

  /**
   * Returns the outcome of loading the project, which {@link ManifestStage} reports.
   *
   * @return the loaded project with its findings
   */
  public ProjectLoad project() {
    return project;
  }

  /**
   * Returns the loaded sources.
   *
   * @return the immutable source set
   */
  public SourceSet sources() {
    return project.sources();
  }

  /**
   * Returns the compile options.
   *
   * @return the options
   */
  public CompileOptions options() {
    return options;
  }

  /**
   * Returns the findings accumulator.
   *
   * @return the accumulator
   */
  public Findings findings() {
    return findings;
  }

  /**
   * Returns the parts of the model that are built so far.
   *
   * @return the parts
   */
  public ModelParts parts() {
    return parts;
  }

  /**
   * Publishes compile-time data for later stages.
   *
   * @param type the type that keys the data
   * @param value the data
   * @param <T> the type
   */
  public <T> void provide(Class<T> type, T value) {
    provided.put(type, Objects.requireNonNull(value, "value"));
  }

  /**
   * Reads compile-time data that an earlier stage published.
   *
   * @param type the type that keys the data
   * @param <T> the type
   * @return the data, or empty when the producing stage did not build it
   */
  public <T> Optional<T> find(Class<T> type) {
    return Optional.ofNullable(type.cast(provided.get(type)));
  }
}
