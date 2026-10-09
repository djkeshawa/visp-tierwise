package io.github.tierwise.cli.codegen;

import java.util.Optional;

/** The languages that codegen writes, with their spelling in {@code --lang}. */
public enum CodegenLanguage {

  /** Java: a package of final classes, interfaces and records. */
  JAVA("java"),

  /** TypeScript: one module that types the keys and features of the project. */
  TYPESCRIPT("ts");

  private final String option;

  CodegenLanguage(String option) {
    this.option = option;
  }

  /**
   * Finds the language for a {@code --lang} value.
   *
   * @param option the value as written on the command line
   * @return the language, or empty for an unknown value
   */
  public static Optional<CodegenLanguage> fromOption(String option) {
    for (CodegenLanguage language : values()) {
      if (language.option.equals(option)) {
        return Optional.of(language);
      }
    }
    return Optional.empty();
  }
}
