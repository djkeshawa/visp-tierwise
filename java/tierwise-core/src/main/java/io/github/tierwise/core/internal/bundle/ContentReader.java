package io.github.tierwise.core.internal.bundle;

import io.github.tierwise.core.internal.model.EntityDef;
import io.github.tierwise.core.internal.model.FactDef;
import io.github.tierwise.core.internal.model.FeatureDef;
import io.github.tierwise.core.internal.model.Hierarchy;
import io.github.tierwise.core.internal.model.KeyDef;
import io.github.tierwise.core.internal.model.Model;
import io.github.tierwise.core.internal.model.Values;
import java.util.Map;
import java.util.Set;

/**
 * Builds the {@link Model} from the {@code content} object (BUNDLE-14 to BUNDLE-108). The readers run in the order
 * of section 10, because each one needs the declarations that come before it.
 */
final class ContentReader {

  private static final Set<String> MEMBERS = Set.of("bundleFormatVersion", "specVersion", "hierarchy", "entities",
      "facts", "keys", "values", "features");

  private ContentReader() {
  }

  /**
   * Reads the content.
   *
   * @param content the {@code content} object, whose versions have been checked
   * @return the model
   * @throws BundleFault with {@code E_BUNDLE_INTEGRITY} for a structural failure
   */
  static Model read(Map<String, Object> content) {
    Members.members(content, "content", MEMBERS, Set.of());
    Hierarchy hierarchy = HierarchyReader.read(content.get("hierarchy"));
    Map<String, EntityDef> entities = DeclarationReader.entities(content.get("entities"));
    Map<String, FactDef> facts = DeclarationReader.facts(content.get("facts"), entities);
    Map<String, KeyDef> keys = KeyReader.read(content.get("keys"), hierarchy);
    Values values = ValuesReader.read(content.get("values"), hierarchy, keys);
    ValuesReader.requireStartingValues(values, keys, hierarchy);
    Map<String, FeatureDef> features = FeatureReader.read(content.get("features"), hierarchy, entities, facts);
    try {
      return new Model(Model.FORMAT_VERSION, Model.SPEC_VERSION, hierarchy, entities, facts, keys, values, features);
    } catch (IllegalArgumentException e) {
      // A record rejects a combination that the readers above did not catch. Such a bundle is still not valid.
      throw BundleFault.integrity("the content does not form a model: " + e.getMessage());
    }
  }
}
