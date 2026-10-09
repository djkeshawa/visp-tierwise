package io.github.tierwise.core.internal.yaml;

/**
 * A file-level fault: the source cannot be read as one YAML document with a mapping root. Such a
 * fault replaces every other finding of the source, because the source has no tree to locate them in
 * (FIND-23). It is internal control flow and never escapes {@link YamlLoader}.
 */
final class YamlFault extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String code;

  /**
   * Creates the fault.
   *
   * @param code the code the source reports
   * @param message the message, which names the clause
   */
  YamlFault(String code, String message) {
    super(message, null, false, false);
    this.code = code;
  }

  /**
   * Returns the code of the fault.
   *
   * @return the code
   */
  String code() {
    return code;
  }
}
