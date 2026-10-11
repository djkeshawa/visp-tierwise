package io.github.visplabs.tierwise.core.internal.check;

import io.github.visplabs.tierwise.core.CompileOptions;
import io.github.visplabs.tierwise.core.internal.project.CodePointOrder;
import io.github.visplabs.tierwise.core.internal.project.Names;
import io.github.visplabs.tierwise.core.internal.project.ProjectLoad;
import io.github.visplabs.tierwise.core.internal.project.ProjectLoader;
import io.github.visplabs.tierwise.core.internal.yaml.PMap;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads the naming patterns in effect for a project, for the explanation of {@code naming/<name>} (FIND-30).
 *
 * <p>The project and its presets are loaded as a compilation loads them, and the patterns are combined as
 * {@link KindStage} combines them: a later layer replaces an earlier pattern entirely (MANIFEST-32,
 * MANIFEST-33). The findings are discarded, so a project with faults still gives every pattern whose
 * {@code category} and {@code pattern} are strings.
 */
public final class DeclaredNamingPatterns {

  private DeclaredNamingPatterns() {
  }

  /**
   * Reads the patterns in effect.
   *
   * @param projectRoot the directory that contains {@code tierwise.yaml}
   * @param options the compile options, which supply the preset fetcher
   * @return the patterns by name, in code-point order of their names
   */
  public static Map<String, io.github.visplabs.tierwise.core.NamingPattern> read(Path projectRoot, CompileOptions options) {
    ProjectLoad load = ProjectLoader.load(projectRoot, options);
    NamingTable table = NamingDeclarations.read(new CheckContext(load, options, projectRoot));
    List<String> names = table.effective().keySet().stream().sorted(CodePointOrder.ORDER).toList();
    Map<String, io.github.visplabs.tierwise.core.NamingPattern> patterns = new LinkedHashMap<>();
    for (String name : names) {
      publicForm(table.effective().get(name)).ifPresent(pattern -> patterns.put(name, pattern));
    }
    return Collections.unmodifiableMap(patterns);
  }

  private static Optional<io.github.visplabs.tierwise.core.NamingPattern> publicForm(NamingPattern declared) {
    if (!(declared.entry() instanceof PMap map)) {
      return Optional.empty();
    }
    Optional<String> category = map.get("category").flatMap(Names::text);
    Optional<String> pattern = map.get("pattern").flatMap(Names::text);
    if (category.isEmpty() || pattern.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new io.github.visplabs.tierwise.core.NamingPattern(declared.name(), category.get(), pattern.get(),
        declared.description()));
  }
}
