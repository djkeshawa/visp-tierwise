package io.github.visplabs.tierwise.core.internal.lint;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.visplabs.tierwise.core.internal.project.SourceSet;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests {@code rules/dead-rule} (LINT-26 to LINT-28). */
class DeadRuleRuleTest {

  @TempDir
  Path dir;

  @Test
  void aRuleAfterAnEmptyWhenAndALiteralEmptyMembershipAreFlaggedAtTheRuleItem() {
    SourceSet sources = LintFixture.sources(dir, Map.of("rules/order.yaml", """
        feature: order.submission
        policies:
          review:
            default: DENY
            rules:
              - id: always
                name: Always
                when: {}
                then: ALLOW
              - id: after-empty
                name: After an empty when
                when:
                  order.Paid: true
                then: ALLOW
              - id: literal-empty
                name: Literal empty membership
                when:
                  item.stock: { all_in: [] }
                then: ALLOW
        """));
    List<LintHit> hits = new DeadRuleRule().check(LintContext.of(sources, LintFixture.emptyModel(), List.of()));
    assertEquals(List.of("10 [policies, review, rules, 1]", "15 [policies, review, rules, 2]"),
        hits.stream().map(LintFixture::where).toList());
  }
}
