package io.github.visplabs.tierwise.core.internal.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.visplabs.tierwise.core.internal.model.Hierarchy;
import io.github.visplabs.tierwise.core.internal.model.ScopeDef;
import io.github.visplabs.tierwise.core.internal.yaml.PNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests the context checks of examples (POL-31, POL-43, POL-44, CTX-3 to CTX-6). */
class ExampleContextTest {

  private static final Hierarchy HIERARCHY = new Hierarchy(List.of("global", "division", "account"),
      List.of("seller"), Map.of(
          "division", ScopeDef.closedCatalog(ScopeDef.ValuesSource.FILES, List.of("north", "south"),
              ScopeDef.OnUnknownNode.ERROR),
          "account", ScopeDef.openCatalog(ScopeDef.ValuesSource.SERVICE),
          "seller", ScopeDef.closedCatalog(ScopeDef.ValuesSource.FILES, List.of("acme"),
              ScopeDef.OnUnknownNode.IGNORE)));

  private static ExampleContext.Parsed parse(String yaml, Findings findings) {
    PNode context = CheckTestSupport.root(yaml);
    return ExampleContext.parse(context, HIERARCHY, findings);
  }

  @Test
  void aDeclaredLevelWithAListedNodeIsAcceptedAndTheRootLevelIsRejected() {
    Findings valid = new Findings();
    ExampleContext.Parsed accepted = parse("levels: { division: south, account: any }\n", valid);
    assertTrue(accepted.valid());
    assertEquals(Map.of("division", "south", "account", "any"), accepted.levels());
    assertEquals(List.of(), CheckTestSupport.descriptions(valid));

    Findings rooted = new Findings();
    assertFalse(parse("levels: { global: south }\n", rooted).valid());
    CheckTestSupport.assertFindings(rooted, "E_INVALID_DECLARATION 1 [levels, global]");
  }

  @Test
  void anUndeclaredNameAndAnUnlistedNodeAreReported() {
    Findings undeclared = new Findings();
    parse("levels: { region: south }\n", undeclared);
    CheckTestSupport.assertFindings(undeclared, "E_UNKNOWN_SCOPE 1 [levels, region]");

    Findings unlisted = new Findings();
    parse("levels: { division: east }\n", unlisted);
    CheckTestSupport.assertFindings(unlisted, "E_UNKNOWN_NODE 1 [levels, division]");
  }

  @Test
  void aMalformedNodeIdIsReportedAndAnUnlistedNodeOfAnIgnoringCatalogIsStillAnError() {
    Findings malformed = new Findings();
    parse("levels: { division: \"\" }\n", malformed);
    CheckTestSupport.assertFindings(malformed, "E_INVALID_NODE_ID 1 [levels, division]");

    Findings ignoring = new Findings();
    parse("axes: { seller: other }\n", ignoring);
    CheckTestSupport.assertFindings(ignoring, "E_UNKNOWN_NODE 1 [axes, seller]");
  }
}
