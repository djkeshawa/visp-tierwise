package io.github.tierwise.cli;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * {@code tierwise explain [CODE]}: prints the catalogue entry of an error code or a lint rule
 * (FIND-29). Without an argument it lists every code and rule. An unknown name exits with 1 and, when
 * a known name is close, suggests it.
 */
@Command(name = "explain", mixinStandardHelpOptions = true, versionProvider = VersionProvider.class,
    description = "Explain an error code or a lint rule, for example E_UNKNOWN_KEY or values/no-op."
        + " Without an argument, list them all.")
final class ExplainCommand implements Callable<Integer> {

  @Spec
  CommandSpec spec;

  @Parameters(index = "0", arity = "0..1", paramLabel = "CODE|RULE",
      description = "An engine or evaluation error code, or a lint rule identifier.")
  String query;

  @Override
  public Integer call() {
    Explanations explanations = Explanations.load();
    if (query == null) {
      explanations.names().forEach(name -> spec.commandLine().getOut().println(name));
      return ExitCodes.OK;
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
}
