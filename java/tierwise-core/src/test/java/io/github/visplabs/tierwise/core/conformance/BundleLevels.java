package io.github.visplabs.tierwise.core.conformance;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the level structure out of a bundle file so that the runner knows which levels are
 * service-sourced (VEC-24, VEC-38). It reads the bundle on its own and does not use the loader under
 * test.
 *
 * @param declared every declared level and axis name
 * @param serviceSourced the levels whose {@code values} member is {@code "service"}
 */
record BundleLevels(Set<String> declared, Set<String> serviceSourced) {

  /** A bundle that cannot be read has no known levels. The vector then expects a load error. */
  static final BundleLevels UNKNOWN = new BundleLevels(Set.of(), Set.of());

  /**
   * Reads the level structure.
   *
   * @param bundleBytes the bytes of a bundle file
   * @return the levels, or {@link #UNKNOWN} when the bytes are not a bundle of the expected shape
   */
  static BundleLevels read(byte[] bundleBytes) {
    try {
      if (!(JsonValues.parse(bundleBytes) instanceof Map<?, ?> envelope)
          || !(envelope.get("content") instanceof Map<?, ?> content)
          || !(content.get("hierarchy") instanceof Map<?, ?> hierarchy)) {
        return UNKNOWN;
      }
      Set<String> declared = new LinkedHashSet<>();
      if (hierarchy.get("levels") instanceof List<?> levels) {
        levels.forEach(level -> declared.add(String.valueOf(level)));
      }
      if (hierarchy.get("axes") instanceof List<?> axes) {
        axes.forEach(axis -> declared.add(String.valueOf(axis)));
      }
      Set<String> service = new LinkedHashSet<>();
      if (hierarchy.get("scopes") instanceof Map<?, ?> scopes) {
        for (Map.Entry<?, ?> scope : scopes.entrySet()) {
          if (scope.getValue() instanceof Map<?, ?> definition && "service".equals(definition.get("values"))) {
            service.add(String.valueOf(scope.getKey()));
          }
        }
      }
      return new BundleLevels(Set.copyOf(declared), Set.copyOf(service));
    } catch (IOException | RuntimeException e) {
      return UNKNOWN;
    }
  }
}
