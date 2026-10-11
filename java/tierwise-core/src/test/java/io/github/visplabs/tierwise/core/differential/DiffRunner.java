package io.github.visplabs.tierwise.core.differential;

import io.github.visplabs.tierwise.core.Context;
import io.github.visplabs.tierwise.core.Evaluator;
import io.github.visplabs.tierwise.core.EvaluationEvent;
import io.github.visplabs.tierwise.core.EvaluatorOptions;
import io.github.visplabs.tierwise.core.FactSet;
import io.github.visplabs.tierwise.core.LevelValueProvider;
import io.github.visplabs.tierwise.core.LoadedBundle;
import io.github.visplabs.tierwise.core.Tierwise;
import io.github.visplabs.tierwise.core.TierwiseException;
import io.github.visplabs.tierwise.core.internal.json.CanonicalJson;
import io.github.visplabs.tierwise.core.internal.json.StrictJson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Runs one differential case in Java and records what it returns.
 *
 * <p>The input is first written in its canonical JSON form and read back with the strict JSON parser, so the Java
 * evaluation sees exactly the values that the JSON Lines file holds. The expectation is the {@code toJson} form of the
 * result, or the code of the {@link TierwiseException} that the call raised. The listener events are recorded too,
 * sorted by their canonical text, because the order in which an evaluator reports them is not part of the contract.
 */
final class DiffRunner {

  private DiffRunner() {
  }

  /**
   * Returns the input as it is written to and read from the JSON Lines file.
   *
   * @param input the input map, with plain values
   * @return the same input, read back from its canonical JSON form
   */
  static Map<String, Object> wire(Map<String, Object> input) {
    return map(StrictJson.parse(CanonicalJson.toBytes(input)));
  }

  /**
   * Evaluates one case and returns the whole record of the JSON Lines file: the case identifier, the model seed, the
   * input and the expectation.
   *
   * @param id the case identifier
   * @param modelSeed the seed that made the model
   * @param input the input from {@link DiffInputs#draw}
   * @return the record
   */
  static Map<String, Object> record(String id, long modelSeed, Map<String, Object> input) {
    Map<String, Object> wire = wire(input);
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("id", id);
    record.put("modelSeed", Long.toHexString(modelSeed));
    record.putAll(wire);
    record.putAll(evaluate(wire));
    return record;
  }

  /**
   * Evaluates the input of one case.
   *
   * @param wire the input as read back from its canonical JSON form
   * @return the members {@code expect} (with {@code result} or {@code error}) and {@code events}
   * @throws IllegalStateException if the evaluator raises an exception that is not a {@link TierwiseException}, which
   *     is a defect of the core and not an expected outcome
   */
  static Map<String, Object> evaluate(Map<String, Object> wire) {
    byte[] bundle = Base64.getDecoder().decode((String) wire.get("bundle"));
    List<Object> events = new ArrayList<>();
    EvaluatorOptions.Builder options = EvaluatorOptions.builder().listener(event -> recordEvent(events, event));
    for (Map.Entry<String, Object> level : map(wire.get("providers")).entrySet()) {
      Map<String, Object> registration = map(level.getValue());
      Duration timeout = Duration.ofMillis(((Long) registration.get("timeoutMs")).longValue());
      options.provider(level.getKey(), stubProvider(map(registration.get("stub"))), timeout);
    }
    Map<String, Object> expect = new LinkedHashMap<>();
    try {
      LoadedBundle loaded = Tierwise.loadBundle(bundle);
      Evaluator evaluator = Tierwise.evaluator(loaded, options.build());
      Context context = wire.containsKey("context") ? Context.fromJson(wire.get("context")) : null;
      FactSet facts = wire.containsKey("facts") ? FactSet.fromJson(wire.get("facts")) : null;
      expect.put("result", call(evaluator, wire, context, facts));
    } catch (TierwiseException e) {
      expect.put("error", e.code());
    } catch (RuntimeException e) {
      throw new IllegalStateException("the core raised an unexpected " + e.getClass().getName() + " for "
          + wire.get("call") + ": " + e.getMessage(), e);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("expect", expect);
    out.put("events", sortedByText(events));
    return out;
  }

  private static Object call(Evaluator evaluator, Map<String, Object> wire, Context context, FactSet facts) {
    String call = (String) wire.get("call");
    return switch (call) {
      case "resolve" -> evaluator.resolve(context, (String) wire.get("key")).toJson();
      case "resolveLocal" -> evaluator.resolveLocal(context, (String) wire.get("key")).toJson();
      case "decide" -> evaluator.decide(context, (String) wire.get("feature"), facts).toJson();
      default -> throw new IllegalStateException("unknown call " + call);
    };
  }

  /**
   * Returns the provider of one stub. A node that the stub lists returns its values, or fails as the outcome says. A
   * node that it does not list fails as an unavailable source, as the stub of the conformance runner does (VEC-43,
   * VEC-45).
   */
  private static LevelValueProvider stubProvider(Map<String, Object> stub) {
    return nodeId -> {
      Object raw = stub.get(nodeId);
      if (!(raw instanceof Map<?, ?> outcome)) {
        throw new IOException("the stub does not list node " + nodeId);
      }
      if (outcome.containsKey("fail")) {
        if ("timeout".equals(outcome.get("fail"))) {
          throw new TimeoutException("the stub times out");
        }
        throw new IOException("the stub is unavailable");
      }
      return map(outcome.get("values"));
    };
  }

  private static void recordEvent(List<Object> events, EvaluationEvent event) {
    Map<String, Object> json = new LinkedHashMap<>();
    json.put("type", event.type().name());
    json.put("bundleVersion", event.bundleVersion());
    event.level().ifPresent(level -> json.put("level", level));
    event.node().ifPresent(node -> json.put("node", node));
    event.key().ifPresent(key -> json.put("key", key));
    synchronized (events) {
      events.add(json);
    }
  }

  private static List<Object> sortedByText(List<Object> events) {
    List<Object> sorted;
    synchronized (events) {
      sorted = new ArrayList<>(events);
    }
    sorted.sort(Comparator.comparing(DiffRunner::canonicalText));
    return sorted;
  }

  /**
   * Returns the canonical JSON text of a value.
   *
   * @param value a plain value
   * @return the text, which is UTF-8 and has no whitespace
   */
  static String canonicalText(Object value) {
    return new String(CanonicalJson.toBytes(value), StandardCharsets.UTF_8);
  }

  /**
   * Views a value as a JSON object.
   *
   * @param value the value
   * @return the object
   * @throws IllegalStateException if the value is not an object
   */
  @SuppressWarnings("unchecked")
  static Map<String, Object> map(Object value) {
    if (!(value instanceof Map<?, ?>)) {
      throw new IllegalStateException("not a JSON object: " + value);
    }
    return (Map<String, Object>) value;
  }
}
