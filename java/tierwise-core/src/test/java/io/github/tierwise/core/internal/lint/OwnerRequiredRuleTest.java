package io.github.tierwise.core.internal.lint;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tierwise.core.internal.project.SourceSet;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests {@code schema/owner-required} (LINT-46 to LINT-48). */
class OwnerRequiredRuleTest {

  @TempDir
  Path dir;

  private static final String MANIFEST_WITH_NAMESPACE = """
      specVersion: "0.1"
      sources:
        schema:
          - "schema/**/*.yaml"
        values:
          - file: "values/root.yaml"
            scope: global
      namespaces:
        ns1: { schema: "schema/ns1.yaml" }
      """;

  private static final String SHARED = """
      keys:
        search.ownerless:
          type: boolean
          combine: all
          settable_at: [global]
          description: No owner.
        search.owned:
          type: boolean
          combine: all
          settable_at: [global]
          owner: search-team
          description: Owned.
      """;

  private static final String NAMESPACED = """
      namespace: ns1
      keys:
        ns1.namespaced:
          type: boolean
          combine: all
          settable_at: [global]
          description: Declared in a namespace.
      """;

  @Test
  void aSharedKeyWithoutOwnerIsFlaggedOnlyWhenNamespacesExist() {
    SourceSet sources = LintFixture.sources(dir, Map.of("tierwise.yaml", MANIFEST_WITH_NAMESPACE,
        "schema/core.yaml", SHARED, "schema/ns1.yaml", NAMESPACED));
    assertEquals(List.of("2 [keys, search.ownerless]"), new OwnerRequiredRule()
        .check(LintContext.of(sources, LintFixture.emptyModel(), List.of())).stream().map(LintFixture::where).toList());
  }
}
