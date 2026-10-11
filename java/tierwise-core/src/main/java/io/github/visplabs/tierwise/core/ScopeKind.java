package io.github.visplabs.tierwise.core;

/** Whether a scope is a level or an axis (PROV-23). */
public enum ScopeKind {
  /** A level of the decision hierarchy. */
  LEVEL("level"),
  /** An axis. */
  AXIS("axis");

  private final String json;

  ScopeKind(String json) {
    this.json = json;
  }

  /**
   * Returns the spelling used in the JSON form of results.
   *
   * @return {@code level} or {@code axis}
   */
  public String json() {
    return json;
  }
}
