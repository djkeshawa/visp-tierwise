package io.github.visplabs.tierwise.core.conformance;

import io.github.visplabs.tierwise.core.Code;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * A parsed evaluate vector (section 12.3). {@link #parse} reports every structural defect that makes a
 * vector a runner failure (VEC-19, VEC-22, VEC-23, VEC-28, VEC-29 to VEC-32, VEC-36 to VEC-41, VEC-48,
 * VEC-49, VEC-51), so that the runner never compares the outcome of a vector it cannot execute.
 *
 * @param id the vector identifier
 * @param call {@code resolve} or {@code decide}
 * @param sync whether to invoke the synchronous variant
 * @param bundle the resolved bundle file
 * @param context the context mapping as written, with {@code levels} and {@code axes} added when missing
 * @param key the key of a {@code resolve} vector
 * @param feature the feature of a {@code decide} vector
 * @param input the FactSet of a {@code decide} vector, unconverted
 * @param providers the {@code providers} section: level name to the marker {@code none} or a stub
 * @param exact whether the vector sets {@code exact: true}
 * @param expectError the expected error code, or {@code null} for a result expectation
 * @param expectResult the expected result fields (without {@code error} and {@code providerCalls})
 * @param providerCalls the expected invocation counts: level, then node ID, then count; {@code null}
 *     when the vector has no {@code providerCalls}
 */
record EvaluateVector(
    String id,
    String call,
    boolean sync,
    Path bundle,
    Map<String, Object> context,
    String key,
    String feature,
    Object input,
    Map<String, Object> providers,
    boolean exact,
    String expectError,
    Map<String, Object> expectResult,
    Map<String, Map<String, Integer>> providerCalls) {

  private static final Set<String> TOP_LEVEL = Set.of("id", "specVersion", "clauses", "description", "call",
      "sync", "bundle", "context", "key", "feature", "input", "providers", "exact", "expect");

  private static final Set<String> RESOLVE_FIELDS = Set.of("key", "strategy", "kind", "value", "bundleVersion",
      "skippedScopes", "contributions", "source");

  private static final Set<String> DECIDE_FIELDS = Set.of("feature", "effect", "bundleVersion", "skippedScopes",
      "binding", "policy", "reason", "trace");

  /**
   * Parses and validates a vector.
   *
   * @param doc the YAML document of the vector file
   * @param file the vector file; the bundle path resolves against its directory (VEC-22)
   * @param bundlesDir the {@code bundles/} directory that the bundle must lie inside (VEC-23)
   * @return the vector
   * @throws RunnerFailure if the vector cannot be executed as written
   */
  static EvaluateVector parse(Map<String, Object> doc, Path file, Path bundlesDir) {
    for (String name : doc.keySet()) {
      if (!TOP_LEVEL.contains(name)) {
        throw new RunnerFailure("unknown field " + name + " (VEC-19)");
      }
    }
    String id = string(doc, "id");
    if (!"0.1".equals(doc.get("specVersion"))) {
      throw new RunnerFailure("unsupported specVersion " + doc.get("specVersion") + " (VEC-15)");
    }
    String call = string(doc, "call");
    if (!call.equals("resolve") && !call.equals("decide")) {
      throw new RunnerFailure("call must be resolve or decide (VEC-21)");
    }
    boolean sync = bool(doc, "sync");
    boolean exact = bool(doc, "exact");
    Path bundle = file.getParent().resolve(string(doc, "bundle")).normalize();
    if (!bundle.startsWith(bundlesDir.normalize())) {
      throw new RunnerFailure("the bundle lies outside bundles/ (VEC-23)");
    }

    String key = null;
    String feature = null;
    Object input = null;
    if (call.equals("resolve")) {
      key = string(doc, "key");
      if (doc.containsKey("feature") || doc.containsKey("input")) {
        throw new RunnerFailure("a resolve vector has neither feature nor input (VEC-29, VEC-32)");
      }
    } else {
      feature = string(doc, "feature");
      if (doc.containsKey("key")) {
        throw new RunnerFailure("a decide vector has no key (VEC-29)");
      }
      if (!doc.containsKey("input")) {
        throw new RunnerFailure("a decide vector has input (VEC-30)");
      }
      input = doc.get("input");
    }

    Map<String, Object> context = new LinkedHashMap<>();
    if (doc.containsKey("context")) {
      if (!(doc.get("context") instanceof Map)) {
        throw new RunnerFailure("context must be a mapping (VEC-28)");
      }
      context.putAll(VectorFiles.asStringMap(doc.get("context"), "context"));
    }
    for (String member : List.of("levels", "axes")) {
      if (context.get(member) instanceof Map) {
        VectorFiles.asStringMap(context.get(member), "context." + member);
      }
      if (!context.containsKey(member)) {
        context.put(member, new LinkedHashMap<String, Object>());
      }
    }

    Map<String, Object> providers = new LinkedHashMap<>();
    if (doc.containsKey("providers")) {
      providers.putAll(VectorFiles.asStringMap(doc.get("providers"), "providers"));
      for (Map.Entry<String, Object> entry : providers.entrySet()) {
        if (!"none".equals(entry.getValue())) {
          VectorFiles.asStringMap(entry.getValue(), "the stub of " + entry.getKey());
        }
      }
    }

    if (!(doc.get("expect") instanceof Map)) {
      throw new RunnerFailure("expect must be a mapping (VEC-48)");
    }
    Map<String, Object> expect = VectorFiles.asStringMap(doc.get("expect"), "expect");
    Set<String> resultFields = call.equals("resolve") ? RESOLVE_FIELDS : DECIDE_FIELDS;
    Map<String, Object> expectResult = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : expect.entrySet()) {
      String name = entry.getKey();
      if (resultFields.contains(name)) {
        expectResult.put(name, entry.getValue());
      } else if (!name.equals("error") && !name.equals("providerCalls")) {
        throw new RunnerFailure("unknown field expect." + name + " (VEC-19)");
      }
    }
    String expectError = null;
    if (expect.containsKey("error")) {
      if (!expectResult.isEmpty()) {
        throw new RunnerFailure("expect has both error and a result field (VEC-48)");
      }
      if (!(expect.get("error") instanceof String code) || !Code.evaluation().contains(code)) {
        throw new RunnerFailure("expect.error is not a code an evaluation operation may raise (VEC-51)");
      }
      expectError = code;
    } else if (expectResult.isEmpty()) {
      throw new RunnerFailure("expect has neither error nor a result field (VEC-49)");
    }

    Map<String, Map<String, Integer>> providerCalls = null;
    if (expect.containsKey("providerCalls")) {
      providerCalls = new TreeMap<>();
      Map<String, Object> levels = VectorFiles.asStringMap(expect.get("providerCalls"), "expect.providerCalls");
      for (Map.Entry<String, Object> level : levels.entrySet()) {
        Map<String, Object> nodes = VectorFiles.asStringMap(level.getValue(), "providerCalls." + level.getKey());
        Map<String, Integer> counts = new TreeMap<>();
        for (Map.Entry<String, Object> node : nodes.entrySet()) {
          if (!(node.getValue() instanceof Integer count) || count < 0) {
            throw new RunnerFailure("providerCalls counts are non-negative integers (VEC-65)");
          }
          counts.put(node.getKey(), count);
        }
        providerCalls.put(level.getKey(), counts);
      }
    }
    return new EvaluateVector(id, call, sync, bundle, context, key, feature, input, providers, exact,
        expectError, expectResult, providerCalls);
  }

  private static String string(Map<String, Object> doc, String name) {
    if (!(doc.get(name) instanceof String value)) {
      throw new RunnerFailure(name + " must be a string");
    }
    return value;
  }

  private static boolean bool(Map<String, Object> doc, String name) {
    Object value = doc.get(name);
    if (value == null && !doc.containsKey(name)) {
      return false;
    }
    if (!(value instanceof Boolean flag)) {
      throw new RunnerFailure(name + " must be a boolean");
    }
    return flag;
  }
}
