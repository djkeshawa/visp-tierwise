package io.github.visplabs.tierwise.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CliTest {

  @Test
  void versionPrintsTheBuildVersion() {
    CliRun.Result result = CliRun.run("--version");
    assertEquals(0, result.code());
    assertTrue(result.out().matches("tierwise \\d+\\.\\d+\\.\\d+\\S*\n"), result.out());
  }

  @Test
  void versionComesFromTheFilteredResource() {
    assertFalse(VersionProvider.version().startsWith("${"));
    assertFalse(VersionProvider.version().equals("unknown"));
  }

  @Test
  void helpListsTheCommands() {
    CliRun.Result result = CliRun.run("--help");
    assertEquals(0, result.code());
    for (String command : new String[] {"init", "validate", "lint", "test", "compile", "explain"}) {
      assertTrue(result.out().contains("\n  " + command + " "), command + "\n" + result.out());
    }
  }

  @Test
  void aMissingCommandIsAUsageError() {
    assertEquals(2, CliRun.run().code());
  }

  @Test
  void anUnknownCommandIsAUsageError() {
    assertEquals(2, CliRun.run("frobnicate").code());
  }
}
