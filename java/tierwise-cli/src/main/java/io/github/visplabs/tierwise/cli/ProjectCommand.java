package io.github.visplabs.tierwise.cli;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/** The base of the commands that work on one project directory. */
abstract class ProjectCommand implements Callable<Integer> {

  @Spec
  CommandSpec spec;

  @Parameters(index = "0", arity = "0..1", paramLabel = "DIR",
      description = "The project directory, which holds tierwise.yaml (default: the current directory).")
  Path dir = Path.of(".");

  /** Returns the standard output of the command. */
  PrintWriter out() {
    return spec.commandLine().getOut();
  }

  /** Returns the standard error of the command. */
  PrintWriter err() {
    return spec.commandLine().getErr();
  }
}
