package io.github.visplabs.tierwise.core.internal.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.visplabs.tierwise.core.CompileOptions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The sample projects of the repository load without a finding of the project loader. */
class SamplesLoadTest {

  private static Path sample(String name) {
    return Path.of(System.getProperty("tierwise.spec.dir")).resolve("../samples").resolve(name).normalize();
  }

  @ParameterizedTest
  @ValueSource(strings = {"commerce", "retail-pos", "saas-entitlements"})
  void theLoaderReportsNothingForASample(String name) {
    assertTrue(Files.isDirectory(sample(name)), name);
    ProjectLoad load = ProjectLoader.load(sample(name), CompileOptions.defaults());
    assertEquals(List.of(), ProjectFixture.descriptions(load), name);
    assertFalse(load.sources().documents().isEmpty());
    assertFalse(load.sources().schemas().isEmpty());
    assertFalse(load.sources().values().isEmpty());
    assertTrue(load.sources().rootLevel().isPresent());
    for (SourceDocument document : load.sources().values()) {
      assertFalse(document.groups().isEmpty(), document.file());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"commerce", "retail-pos", "saas-entitlements"})
  void thePresetsOfASampleComeFirstAndTheProjectIsTheLastLayer(String name) {
    ProjectLoad load = ProjectLoader.load(sample(name), CompileOptions.defaults());
    List<SourceDocument> presets = load.sources().documents().stream().filter(SourceDocument::isPreset).toList();
    for (int i = 0; i < presets.size(); i++) {
      assertEquals(i, load.sources().documents().indexOf(presets.get(i)));
      assertEquals(i, presets.get(i).layer());
      assertTrue(presets.get(i).file().startsWith("tierwise:"));
    }
    for (SourceDocument document : load.sources().documents().subList(presets.size(), load.sources().documents().size())) {
      assertEquals(presets.size(), document.layer());
    }
    assertEquals(presets.size() + 1, load.sources().lint().layers().size());
  }
}
