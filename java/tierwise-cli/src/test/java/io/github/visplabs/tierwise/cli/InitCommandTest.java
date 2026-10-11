package io.github.visplabs.tierwise.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.visplabs.tierwise.core.CompileResult;
import io.github.visplabs.tierwise.core.Tierwise;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class InitCommandTest {

  @TempDir
  Path temp;

  @ParameterizedTest
  @EnumSource(Preset.class)
  void initWritesAProjectThatValidatesWithZeroFindings(Preset preset) {
    Path project = temp.resolve(preset.id());
    CliRun.Result init = CliRun.run("init", "--preset", preset.id(), project.toString());
    assertEquals(0, init.code(), init.err());
    assertTrue(Files.isRegularFile(project.resolve("tierwise.yaml")));

    CompileResult result = Tierwise.compile(project);
    assertEquals(java.util.List.of(), result.findings());
    assertTrue(result.bundle().isPresent());

    CliRun.Result validate = CliRun.run("validate", project.toString());
    assertEquals(0, validate.code(), validate.out());
    assertEquals("", validate.out());
    assertEquals("validate: no findings\n", validate.err());
  }

  @Test
  void theProjectExtendsTheChosenPreset() throws IOException {
    for (Preset preset : Preset.values()) {
      Path project = temp.resolve("extends-" + preset.id());
      CliRun.run("init", "--preset", preset.id(), project.toString());
      assertTrue(Files.readString(project.resolve("tierwise.yaml")).contains("- tierwise:" + preset.id()), preset.id());
    }
  }

  @Test
  void theDefaultPresetIsRecommended() throws IOException {
    Path project = temp.resolve("default");
    CliRun.Result init = CliRun.run("init", project.toString());
    assertEquals(0, init.code());
    assertTrue(Files.readString(project.resolve("tierwise.yaml")).contains("- tierwise:recommended"));
  }

  @Test
  void initCreatesTheDirectoryWhenItIsMissing() {
    Path project = temp.resolve("a").resolve("b");
    assertEquals(0, CliRun.run("init", project.toString()).code());
    assertTrue(Files.isRegularFile(project.resolve("tierwise.yaml")));
  }

  @Test
  void initNeverOverwritesAManifest() throws IOException {
    Path manifest = temp.resolve("tierwise.yaml");
    Files.writeString(manifest, "keep: me\n");
    CliRun.Result init = CliRun.run("init", temp.toString());
    assertEquals(2, init.code());
    assertTrue(init.err().contains("already exists"), init.err());
    assertEquals("keep: me\n", Files.readString(manifest));
  }

  @Test
  void anUnknownPresetIsAUsageError() {
    CliRun.Result init = CliRun.run("init", "--preset", "nonsense", temp.resolve("x").toString());
    assertEquals(2, init.code());
    assertTrue(init.err().contains("nonsense"), init.err());
  }

  @Test
  void presetNamesAreCaseInsensitive() {
    assertEquals(0, CliRun.run("init", "--preset", "Minimal", temp.resolve("m").toString()).code());
  }
}
