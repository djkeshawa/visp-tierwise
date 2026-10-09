package io.github.tierwise.cli;

import io.github.tierwise.core.CompileOptions;
import io.github.tierwise.core.NamingPattern;
import io.github.tierwise.core.Tierwise;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * {@code tierwise explain [--project DIR] [CODE]}: prints the catalogue entry of an error code or a lint
 * rule (FIND-29). Without an argument it lists every code and rule. An unknown name exits with 1 and, when
 * a known name is close, suggests it.
 *
 * <p>A naming rule {@code naming/<name>} is explained by its pattern's {@code description}, or, when the
 * pattern has none, by the naming family entry followed by the pattern (FIND-30, ADR 0026). The pattern
 * comes from the project named by {@code --project}, or from the current directory when it holds
 * {@code tierwise.yaml}. Without a project the command prints the family entry and says that the pattern
 * needs a project.
 */
@Command(name = "explain", mixinStandardHelpOptions = true, versionProvider = VersionProvider.class,
    description = "Explain an error code or a lint rule, for example E_UNKNOWN_KEY or values/no-op."
        + " Without an argument, list them all.")
final class ExplainCommand implements Callable<Integer> {

  private static final String NAMING_PREFIX = "naming/";
  private static final String MANIFEST = "tierwise.yaml";

  @Spec
  CommandSpec spec;

  @Parameters(index = "0", arity = "0..1", paramLabel = "CODE|RULE",
      description = "An engine or evaluation error code, or a lint rule identifier.")
  String query;

  @Option(names = "--project", paramLabel = "DIR",
      description = "The project whose naming patterns explain naming/<name> rules (default: the current"
          + " directory, when it holds tierwise.yaml).")
  Path project;

  @Override
  public Integer call() {
    Explanations explanations = Explanations.load();
    if (query == null) {
      explanations.names().forEach(name -> spec.commandLine().getOut().println(name));
      return ExitCodes.OK;
    }
    String trimmed = query.trim();
    if (isNamingRule(trimmed)) {
      Optional<Path> dir = projectDir();
      if (dir.isPresent()) {
        return explainNamingRule(explanations, trimmed, dir.get());
      }
    }
    Optional<String> text = explanations.explain(query);
    if (text.isPresent()) {
      spec.commandLine().getOut().println(text.get());
      return ExitCodes.OK;
    }
    List<String> close = explanations.suggestions(query);
    String hint = close.isEmpty() ? "" : " Did you mean " + String.join(", ", close) + "?";
    spec.commandLine().getErr().println("explain: no explanation for '" + query + "'." + hint
        + " Run `tierwise explain` to list every code and rule.");
    return ExitCodes.INVALID;
  }

  private static boolean isNamingRule(String name) {
    return name.startsWith(NAMING_PREFIX) && name.length() > NAMING_PREFIX.length()
        && !name.equals(NAMING_PREFIX + "*");
  }

  /** The project to read patterns from: {@code --project}, or the current directory when it holds a manifest. */
  private Optional<Path> projectDir() {
    if (project != null) {
      if (!Files.isDirectory(project)) {
        throw new UsageException("not a directory: " + project);
      }
      if (!Files.isRegularFile(project.resolve(MANIFEST))) {
        throw new UsageException("no " + MANIFEST + " in " + project);
      }
      return Optional.of(project);
    }
    Path current = Path.of("");
    return Files.isRegularFile(current.resolve(MANIFEST)) ? Optional.of(current) : Optional.empty();
  }

  private int explainNamingRule(Explanations explanations, String rule, Path dir) {
    String name = rule.substring(NAMING_PREFIX.length());
    CompileOptions options = CompileOptions.builder().presetFetcher(new HttpPresetFetcher()).build();
    Map<String, NamingPattern> patterns = Tierwise.namingPatterns(dir, options);
    NamingPattern pattern = patterns.get(name);
    if (pattern == null) {
      Optional<String> close = Explanations.closest(name, patterns.keySet());
      String hint = close.map(c -> " Did you mean " + NAMING_PREFIX + c + "?").orElse("");
      spec.commandLine().getErr().println("explain: the project in " + dir.toAbsolutePath().normalize()
          + " declares no naming pattern '" + name + "'." + hint);
      return ExitCodes.INVALID;
    }
    String text = pattern.description()
        .map(description -> rule + "\n" + description)
        .orElseGet(() -> rule + "\n" + explanations.namingFamily() + "\n\n**Category:** " + pattern.category()
            + "\n**Pattern:** " + pattern.pattern());
    spec.commandLine().getOut().println(text);
    return ExitCodes.OK;
  }
}
