package io.github.visplabs.tierwise.cli;

import java.io.PrintWriter;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * The root command {@code tierwise} and the way a command line is run.
 *
 * <p>{@link #create(Environment)} builds the command tree; {@link #execute(PrintWriter, PrintWriter,
 * Environment, String...)} runs it and returns the exit code (see {@link ExitCodes}). {@link Main}
 * calls it with the streams of the process, tests call it with their own.
 */
@Command(name = "tierwise", mixinStandardHelpOptions = true, versionProvider = VersionProvider.class,
    description = "Tierwise: validate, test and compile a project of layered settings.",
    synopsisSubcommandLabel = "COMMAND")
public final class TierwiseCli {

  private TierwiseCli() {
  }

  /**
   * Builds the command tree.
   *
   * @param environment the clock and environment variables that commands read
   * @return the root command with its subcommands
   */
  static CommandLine create(Environment environment) {
    CommandLine cli = new CommandLine(new TierwiseCli());
    cli.addSubcommand(new InitCommand());
    cli.addSubcommand(new ValidateCommand());
    cli.addSubcommand(new LintCommand());
    cli.addSubcommand(new TestCommand());
    cli.addSubcommand(new CompileCommand(environment));
    cli.addSubcommand(new ExplainCommand());
    cli.addSubcommand(new CodegenCommand());
    cli.setCaseInsensitiveEnumValuesAllowed(true);
    cli.setExecutionExceptionHandler(TierwiseCli::handle);
    return cli;
  }

  /**
   * Runs a command line.
   *
   * @param out standard output
   * @param err standard error
   * @param environment the clock and environment variables that commands read
   * @param args the arguments after the program name
   * @return the exit code: 0, 1 when a validation fails, 2 on a usage or I/O error
   */
  static int execute(PrintWriter out, PrintWriter err, Environment environment, String... args) {
    CommandLine cli = create(environment);
    cli.setOut(out);
    cli.setErr(err);
    int code = cli.execute(args);
    out.flush();
    err.flush();
    return code;
  }

  private static int handle(Exception error, CommandLine cli, CommandLine.ParseResult parsed) {
    String message = error instanceof UsageException ? error.getMessage()
        : "internal error: " + error;
    cli.getErr().println("tierwise: " + message);
    return ExitCodes.USAGE;
  }
}
