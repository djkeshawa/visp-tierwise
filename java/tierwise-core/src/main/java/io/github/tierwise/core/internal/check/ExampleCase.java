package io.github.tierwise.core.internal.check;

import io.github.tierwise.core.internal.lint.LintExample;
import io.github.tierwise.core.internal.yaml.PNode;
import java.util.List;
import java.util.Map;

/**
 * A valid example, ready to run (POL-41 to POL-46).
 *
 * @param feature the feature that the example decides
 * @param name the example's name, which identifies it in findings (POL-29)
 * @param expectNode the {@code expect} node, where a failure is reported (FIND-34)
 * @param levels the non-root levels of the context
 * @param axes the axes of the context
 * @param facts the single-value facts, plain
 * @param children the child maps, plain
 * @param expected the expected effect, plain
 */
record ExampleCase(
    String feature,
    String name,
    PNode expectNode,
    Map<String, String> levels,
    Map<String, String> axes,
    Map<String, Object> facts,
    Map<String, List<Map<String, Object>>> children,
    Object expected) {

  /**
   * Returns the lint view of the example.
   *
   * @return the example as the lint rules read it
   */
  LintExample lint() {
    return new LintExample(feature, levels, axes, children);
  }
}
