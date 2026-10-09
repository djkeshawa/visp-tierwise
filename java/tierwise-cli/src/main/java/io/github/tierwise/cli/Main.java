package io.github.tierwise.cli;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/** The entry point of the {@code tierwise} command line. */
public final class Main {

  private Main() {
  }

  /**
   * Runs the command line and exits the process with its exit code.
   *
   * @param args the command-line arguments
   */
  public static void main(String[] args) {
    PrintWriter out = new PrintWriter(System.out, false, StandardCharsets.UTF_8);
    PrintWriter err = new PrintWriter(System.err, false, StandardCharsets.UTF_8);
    int code = TierwiseCli.execute(out, err, Environment.system(), args);
    System.exit(code);
  }
}
