package io.github.visplabs.tierwise.core;

import java.util.Optional;

/** Options for {@link Tierwise#compile(java.nio.file.Path, CompileOptions)}. */
public final class CompileOptions {

  private static final CompileOptions DEFAULTS = new CompileOptions(null);

  private final PresetFetcher presetFetcher;

  private CompileOptions(PresetFetcher presetFetcher) {
    this.presetFetcher = presetFetcher;
  }

  /**
   * Returns the default options: no preset fetcher, so every preset URL reports {@code E_PRESET}.
   *
   * @return the default options
   */
  public static CompileOptions defaults() {
    return DEFAULTS;
  }

  /**
   * Starts a builder.
   *
   * @return a new builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Returns the fetcher for URL presets.
   *
   * @return the fetcher, or empty when none is configured
   */
  public Optional<PresetFetcher> presetFetcher() {
    return Optional.ofNullable(presetFetcher);
  }

  /** Builds {@link CompileOptions}. */
  public static final class Builder {

    private PresetFetcher presetFetcher;

    private Builder() {
    }

    /**
     * Sets the fetcher for URL presets.
     *
     * @param fetcher the fetcher, or {@code null} for none
     * @return this builder
     */
    public Builder presetFetcher(PresetFetcher fetcher) {
      this.presetFetcher = fetcher;
      return this;
    }

    /**
     * Builds the options.
     *
     * @return the options
     */
    public CompileOptions build() {
      return new CompileOptions(presetFetcher);
    }
  }
}
