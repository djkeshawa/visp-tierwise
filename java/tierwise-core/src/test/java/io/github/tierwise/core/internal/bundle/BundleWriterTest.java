package io.github.tierwise.core.internal.bundle;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.tierwise.core.internal.json.CanonicalJson;
import io.github.tierwise.core.internal.json.Sha256;
import io.github.tierwise.core.internal.json.StrictJson;
import io.github.tierwise.core.internal.model.FeatureDef;
import io.github.tierwise.core.internal.model.Hierarchy;
import io.github.tierwise.core.internal.model.KeyDef;
import io.github.tierwise.core.internal.model.LoadedModel;
import io.github.tierwise.core.internal.model.Model;
import io.github.tierwise.core.internal.model.Returns;
import io.github.tierwise.core.internal.model.ScopeDef;
import io.github.tierwise.core.internal.model.TypeDecl;
import io.github.tierwise.core.internal.model.Values;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The writer: content from a model, canonical bytes, and the digest that names them (BUNDLE-2, BUNDLE-6, DET-1). */
class BundleWriterTest {

  private static Model model(Hierarchy hierarchy, Map<String, KeyDef> keys, Values values) {
    return new Model("1", "0.1", hierarchy, Map.of(), Map.of(), keys, values, Map.of());
  }

  private static Hierarchy divisions(List<String> nodes) {
    ScopeDef division = ScopeDef.closedCatalog(ScopeDef.ValuesSource.FILES, nodes, ScopeDef.OnUnknownNode.ERROR);
    return new Hierarchy(List.of("global", "division"), List.of(), Map.of("division", division));
  }

  @Test
  void BUNDLE_6_writtenDigestIsTheDigestOfTheContent() {
    BundleWriter.Written written = BundleWriter.write(model(divisions(List.of("north")), Map.of(), Values.EMPTY));
    Map<?, ?> envelope = (Map<?, ?>) StrictJson.parse(written.bytes());
    String digest = "sha256:" + Sha256.hexDigest(CanonicalJson.toBytes(envelope.get("content")));
    assertEquals(digest, written.bundleVersion());
    assertArrayEquals(written.bytes(), CanonicalJson.toBytes(envelope));
  }

  @Test
  void DET_1_writingTheSameModelTwiceGivesTheSameBytes() {
    Model model = model(divisions(List.of("north", "south")), Map.of(), Values.EMPTY);
    assertArrayEquals(BundleWriter.write(model).bytes(), BundleWriter.write(model).bytes());
  }

  @Test
  void BUNDLE_39_nodesAreWrittenInCodePointOrder() {
    Model model = model(divisions(List.of("south", "north")), Map.of(), Values.EMPTY);
    LoadedModel loaded = BundleReader.read(BundleWriter.write(model).bytes());
    assertEquals(List.of("north", "south"), loaded.model().hierarchy().scope("division").nodes());
  }

  @Test
  void BUNDLE_22_emptyScopeAndNodeEntriesAreOmitted() {
    Values values = new Values(Map.of("global", Map.of("", Map.of())));
    Map<String, Object> content = ContentWriter.write(model(divisions(List.of("north")), Map.of(), values));
    assertEquals(Map.of(), content.get("values"));
  }

  @Test
  void BUNDLE_20_absentOptionalMembersAreNotWritten() {
    KeyDef key = new KeyDef(TypeDecl.simple("boolean"), "all", Optional.empty(), List.of("global"), Optional.empty(),
        Optional.empty(), Optional.empty());
    Map<String, Object> content = ContentWriter.write(model(divisions(List.of("north")), Map.of("k", key), Values.EMPTY));
    Map<?, ?> member = (Map<?, ?>) ((Map<?, ?>) content.get("keys")).get("k");
    assertFalse(member.containsKey("default"));
    assertFalse(member.containsKey("combineAcrossAxes"));
    assertFalse(member.containsKey("onSourceFailure"));
    assertFalse(member.containsKey("kind"));
  }

  private static Map<?, ?> returnsOf(Returns returns) {
    FeatureDef feature = new FeatureDef("customer", returns, "FIRST", null, List.of("global"), Map.of(), Map.of());
    Model model = new Model("1", "0.1", divisions(List.of("north")), Map.of(), Map.of(), Map.of(), Values.EMPTY,
        Map.of("checkout.submission", feature));
    Map<?, ?> features = (Map<?, ?>) ContentWriter.write(model).get("features");
    return (Map<?, ?>) ((Map<?, ?>) features.get("checkout.submission")).get("returns");
  }

  @Test
  void BUNDLE_82_nullableIsWrittenOnlyWhenTrue() {
    assertFalse(returnsOf(Returns.enumeration(List.of("ALLOW", "DENY"), false)).containsKey("nullable"));
    assertEquals(Boolean.TRUE, returnsOf(Returns.enumeration(List.of("ALLOW"), true)).get("nullable"));
  }

  @Test
  void BUNDLE_75_enumValuesAreWrittenInCodePointOrder() {
    assertEquals(List.of("ALLOW", "DENY"), returnsOf(Returns.enumeration(List.of("DENY", "ALLOW"), false)).get("enum"));
  }
}
