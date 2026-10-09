package io.github.tierwise.core.internal.bundle;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.TierwiseException;
import io.github.tierwise.core.internal.json.CanonicalJson;
import io.github.tierwise.core.internal.json.Sha256;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds bundle bytes for the load tests. {@link #richContent()} is a valid {@code content} that uses every
 * member kind of section 10; each structural test edits one part of a fresh copy and asserts the clause that the
 * edit breaks. {@link #bundle(Map)} adds the envelope and the digest that BUNDLE-6 defines.
 */
final class BundleFixtures {

  private BundleFixtures() {
  }

  /**
   * Returns a fresh, valid {@code content}. The result is mutable, so each caller may edit it.
   *
   * @return the content
   */
  static Map<String, Object> richContent() {
    Map<String, Object> content = new LinkedHashMap<>();
    content.put("bundleFormatVersion", "1");
    content.put("specVersion", "0.1");
    content.put("hierarchy", obj(
        "levels", arr("global", "division", "account"),
        "axes", arr("seller"),
        "scopes", obj(
            "division", obj("values", "files", "nodes", arr("north", "south"), "onUnknownNode", "error"),
            "account", obj("values", "service", "nodes", "open"),
            "seller", obj("values", "files", "nodes", arr("acme"), "onUnknownNode", "error"))));
    content.put("entities", obj(
        "customer", obj("belongsTo", arr(), "hasMany", arr("order")),
        "order", obj("belongsTo", arr("customer"), "hasMany", arr())));
    content.put("facts", obj(
        "customer.active", obj("entity", "customer", "type", obj("type", "boolean")),
        "order.status", obj("entity", "order", "type", obj("type", "enum", "values", arr("PAID", "PENDING")))));
    content.put("keys", obj(
        "checkout.lead_hours", obj("type", obj("type", "integer", "min", 0, "max", 168), "combine", "override",
            "default", 4, "settableAt", arr("global", "division", "seller"), "combineAcrossAxes", "max"),
        "checkout.window", obj("type", obj("type", "object", "fields", obj(
                "open", obj("type", "string", "format", "time_hhmm"),
                "close", obj("type", "string", "format", "time_hhmm"),
                "slot", obj("type", "integer", "optional", true))),
            "combine", "deep_merge", "default", obj("open", "08:00", "close", "18:00"),
            "settableAt", arr("global", "division")),
        "checkout.allowed", obj("type", obj("type", "boolean"), "combine", "all",
            "settableAt", arr("global", "account"), "onSourceFailure", "deny", "kind", "capability"),
        "checkout.methods", obj("type", obj("type", "set", "universe", arr("GROUND", "PICKUP")), "combine", "union",
            "settableAt", arr("global")),
        "checkout.limit", obj("type", obj("type", "integer"), "combine", "min",
            "settableAt", arr("global", "division")),
        "checkout.rate", obj("type", obj("type", "decimal"), "combine", "override", "default", "1.5",
            "settableAt", arr("global"))));
    content.put("values", obj(
        "global", obj("", obj("checkout.lead_hours", 2, "checkout.window", obj("open", "09:00"),
            "checkout.allowed", true, "checkout.methods", arr("PICKUP"), "checkout.limit", 10,
            "checkout.rate", "2.5")),
        "division", obj("south", obj("checkout.lead_hours", 6, "checkout.window", obj("close", "17:00"),
            "checkout.limit", 5)),
        "seller", obj("acme", obj("checkout.lead_hours", 8))));
    content.put("features", obj(
        "checkout.submission", obj(
            "decidedPer", "customer",
            "returns", obj("enum", arr("ALLOW", "DENY")),
            "hitPolicy", "FIRST",
            "ifFactMissing", "DENY",
            "bindingSettableAt", arr("global", "division"),
            "policies", obj("credit", obj(
                "settings", obj(
                    "allowed_states", obj("default", arr("PAID"), "listOf", "order.status"),
                    "check_active", obj("default", true, "valueOf", "customer.active")),
                "default", "DENY",
                "requiredFacts", arr("customer.active", "order.status"),
                "rules", arr(
                    obj("id", "active", "name", "Active customer",
                        "when", arr(obj("fact", "customer.active", "op", "eq", "value", true)), "then", "ALLOW"),
                    obj("id", "states", "name", "Allowed states",
                        "when", arr(obj("fact", "order.status", "op", "in", "value", "$allowed_states")),
                        "then", "ALLOW"),
                    obj("id", "both", "name", "Active and paid",
                        "when", arr(obj("fact", "customer.active", "op", "eq", "value", true),
                            obj("fact", "order.status", "op", "eq", "value", "PAID")),
                        "then", "ALLOW")))),
            "bindings", obj(
                "global", obj("", obj("policy", "credit", "settings", obj())),
                "division", obj("south", obj("policy", "credit", "settings", obj("allowed_states", arr("PAID"))))))));
    return content;
  }

  /**
   * Builds the bytes of a bundle whose {@code bundleVersion} is the digest of the content.
   *
   * @param content the content
   * @return the canonical bundle bytes
   */
  static byte[] bundle(Map<String, Object> content) {
    return envelope("sha256:" + Sha256.hexDigest(CanonicalJson.toBytes(content)), content);
  }

  /**
   * Builds an envelope with any {@code bundleVersion} and {@code content} value.
   *
   * @param bundleVersion the value of {@code bundleVersion}
   * @param content the value of {@code content}
   * @return the canonical bytes of the envelope
   */
  static byte[] envelope(Object bundleVersion, Object content) {
    Map<String, Object> envelope = new LinkedHashMap<>();
    envelope.put("bundleVersion", bundleVersion);
    envelope.put("content", content);
    return CanonicalJson.toBytes(envelope);
  }

  /**
   * Asserts that the content loads.
   *
   * @param content the content
   */
  static void assertLoads(Map<String, Object> content) {
    assertDoesNotThrow(() -> BundleReader.read(bundle(content)));
  }

  /**
   * Asserts that loading the content fails with {@code E_BUNDLE_INTEGRITY}.
   *
   * @param content the content
   */
  static void assertFault(Map<String, Object> content) {
    assertCode(bundle(content), Code.E_BUNDLE_INTEGRITY);
  }

  /**
   * Asserts that loading the content fails with {@code E_BUNDLE_INTEGRITY} and a message that cites the clause.
   *
   * @param content the content
   * @param clause the clause ID that the failed check names, such as {@code BUNDLE-122}
   */
  static void assertFault(Map<String, Object> content, String clause) {
    assertIntegrity(bundle(content), clause);
  }

  /**
   * Asserts that the bytes fail with {@code E_BUNDLE_INTEGRITY} and a message that cites the clause.
   *
   * @param bytes the bundle bytes
   * @param clause the clause ID
   */
  static void assertIntegrity(byte[] bytes, String clause) {
    TierwiseException e = assertThrows(TierwiseException.class, () -> BundleReader.read(bytes));
    assertEquals(Code.E_BUNDLE_INTEGRITY, e.code(), e.getMessage());
    assertTrue(e.getMessage().contains(clause), e.getMessage());
  }

  /**
   * Asserts that the bytes fail with a given code.
   *
   * @param bytes the bundle bytes
   * @param code the expected code
   */
  static void assertCode(byte[] bytes, String code) {
    TierwiseException e = assertThrows(TierwiseException.class, () -> BundleReader.read(bytes));
    assertEquals(code, e.code(), e.getMessage());
  }

  /**
   * Returns the map at a path below a root map. Integer path elements index lists.
   *
   * @param root the root map
   * @param path the keys and indices
   * @return the map at the path
   */
  @SuppressWarnings("unchecked")
  static Map<String, Object> mapAt(Map<String, Object> root, Object... path) {
    return (Map<String, Object>) at(root, path);
  }

  /**
   * Returns the list at a path below a root map.
   *
   * @param root the root map
   * @param path the keys and indices
   * @return the list at the path
   */
  @SuppressWarnings("unchecked")
  static List<Object> listAt(Map<String, Object> root, Object... path) {
    return (List<Object>) at(root, path);
  }

  private static Object at(Object root, Object... path) {
    Object current = root;
    for (Object step : path) {
      current = step instanceof Integer index ? ((List<?>) current).get(index) : ((Map<?, ?>) current).get(step);
    }
    return current;
  }

  /**
   * Builds an object with the given member pairs, keeping their order.
   *
   * @param pairs name and value pairs
   * @return a mutable map
   */
  static Map<String, Object> obj(Object... pairs) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      out.put((String) pairs[i], pairs[i + 1]);
    }
    return out;
  }

  /**
   * Builds an array.
   *
   * @param items the elements
   * @return a mutable list
   */
  static List<Object> arr(Object... items) {
    List<Object> out = new ArrayList<>();
    for (Object item : items) {
      out.add(item);
    }
    return out;
  }

  /**
   * Returns the specification's {@code bundles/} directory. The build sets {@code tierwise.spec.dir}.
   *
   * @return an absolute path
   */
  static Path specBundles() {
    String property = System.getProperty("tierwise.spec.dir");
    Path spec = property != null && !property.isBlank()
        ? Paths.get(property).toAbsolutePath().normalize()
        : searchUpward(Paths.get("").toAbsolutePath());
    return spec.resolve("conformance").resolve("bundles");
  }

  private static Path searchUpward(Path start) {
    for (Path dir = start; dir != null; dir = dir.getParent()) {
      if (Files.isDirectory(dir.resolve("spec").resolve("conformance"))) {
        return dir.resolve("spec");
      }
    }
    throw new IllegalStateException("cannot locate spec/; set -Dtierwise.spec.dir");
  }

  /**
   * Reads a UTF-8 text file.
   *
   * @param file the file
   * @return the text
   */
  static String read(Path file) {
    try {
      return Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("cannot read " + file, e);
    }
  }
}
