package io.github.visplabs.tierwise.openfeature;

import io.github.visplabs.tierwise.core.Evaluator;
import io.github.visplabs.tierwise.core.EvaluatorOptions;
import io.github.visplabs.tierwise.core.LevelValueProvider;
import io.github.visplabs.tierwise.core.LoadedBundle;
import io.github.visplabs.tierwise.core.Tierwise;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/**
 * Builds a small canonical bundle for the tests. The content is written as JSON below, parsed, written back
 * in canonical form (sorted members, no whitespace) and sealed with its SHA-256 digest (BUNDLE-1 to BUNDLE-6).
 * The names are arbitrary: the provider knows no domain.
 */
final class TestBundles {

  /** The hierarchy has the levels top, mid and low (service-sourced) and the axis lane. */
  private static final String CONTENT =
      """
      {
        "bundleFormatVersion": "1",
        "specVersion": "0.1",
        "hierarchy": {
          "levels": ["top", "mid", "low"],
          "axes": ["lane"],
          "scopes": {
            "mid": {"nodes": ["m1", "m2"], "onUnknownNode": "error", "values": "files"},
            "low": {"nodes": "open", "values": "service"},
            "lane": {"nodes": ["l1", "l2"], "onUnknownNode": "ignore", "values": "files"}
          }
        },
        "entities": {
          "parent": {"belongsTo": [], "hasMany": []},
          "part": {"belongsTo": [], "hasMany": []},
          "whole": {"belongsTo": ["parent"], "hasMany": ["part"]}
        },
        "facts": {
          "parent.flag": {"entity": "parent", "type": {"type": "boolean"}},
          "part.qty": {"entity": "part", "type": {"type": "integer", "min": 0, "max": 1000}},
          "part.tag": {"entity": "part", "type": {"type": "enum", "values": ["x", "y", "z"]}},
          "whole.count": {"entity": "whole", "type": {"type": "integer"}},
          "whole.kind": {"entity": "whole", "type": {"type": "enum", "values": ["a", "b"]}},
          "whole.price": {"entity": "whole", "type": {"type": "decimal"}},
          "whole.note": {"entity": "whole", "type": {"type": "string"}}
        },
        "keys": {
          "k.all": {"type": {"type": "boolean"}, "combine": "all", "settableAt": ["top", "mid", "low", "lane"], "onSourceFailure": "deny"},
          "k.any": {"type": {"type": "boolean"}, "combine": "any", "settableAt": ["top", "mid", "lane"]},
          "k.inter": {"type": {"type": "set", "universe": ["u3", "u1", "u2"]}, "combine": "intersect", "settableAt": ["top", "mid", "low"], "onSourceFailure": "deny"},
          "k.min": {"type": {"type": "integer", "min": 0, "max": 100}, "combine": "min", "settableAt": ["top", "mid", "low"], "onSourceFailure": "deny"},
          "k.max": {"type": {"type": "integer"}, "combine": "max", "settableAt": ["top", "mid", "lane"]},
          "k.over": {"type": {"type": "integer"}, "combine": "override", "default": 5, "settableAt": ["top", "mid", "lane"], "combineAcrossAxes": "max", "kind": "conf"},
          "k.big": {"type": {"type": "integer"}, "combine": "override", "default": 3000000000, "settableAt": ["top"]},
          "k.neg": {"type": {"type": "integer"}, "combine": "override", "default": -2147483648, "settableAt": ["top"]},
          "k.text": {"type": {"type": "string"}, "combine": "override", "default": "d", "settableAt": ["top", "low"], "onSourceFailure": "skip"},
          "k.root": {"type": {"type": "string"}, "combine": "override", "default": "d", "settableAt": ["top", "mid"]},
          "k.dec": {"type": {"type": "decimal"}, "combine": "override", "default": "1.5", "settableAt": ["top"]},
          "k.list": {"type": {"type": "list", "items": {"type": "string"}}, "combine": "override", "default": ["a", "b"], "settableAt": ["top"]},
          "k.deep": {
            "type": {"type": "object", "fields": {"a": {"type": "integer"}, "b": {"type": "object", "fields": {"c": {"type": "string"}, "d": {"type": "string", "optional": true}}}}},
            "combine": "deep_merge",
            "default": {"a": 1, "b": {"c": "c0"}},
            "settableAt": ["top", "mid", "low", "lane"],
            "combineAcrossAxes": "axis_wins",
            "onSourceFailure": "skip"
          }
        },
        "values": {
          "top": {"": {"k.all": true, "k.inter": ["u3", "u1", "u2"], "k.min": 50, "k.over": 1, "k.root": "root", "k.deep": {"a": 10, "b": {"c": "top"}}}},
          "mid": {
            "m1": {"k.all": true, "k.min": 30, "k.over": 6, "k.deep": {"b": {"d": "mid"}}},
            "m2": {"k.all": false, "k.inter": ["u3", "u1"], "k.any": true, "k.root": "mid"}
          },
          "lane": {"l1": {"k.over": 8, "k.max": 3}}
        },
        "features": {
          "f.one": {
            "decidedPer": "whole",
            "returns": {"enum": ["ALLOW", "DENY"]},
            "hitPolicy": "FIRST",
            "ifFactMissing": "DENY",
            "bindingSettableAt": ["top", "mid"],
            "policies": {
              "p1": {
                "settings": {},
                "default": "DENY",
                "requiredFacts": ["parent.flag", "whole.kind"],
                "rules": [
                  {"id": "r1", "name": "first rule", "when": [{"fact": "parent.flag", "op": "eq", "value": true}, {"fact": "whole.kind", "op": "eq", "value": "a"}], "then": "ALLOW"}
                ]
              }
            },
            "bindings": {"top": {"": {"policy": "p1", "settings": {}}}}
          },
          "f.obj": {
            "decidedPer": "parent",
            "returns": {"object": {"level": {"type": "integer"}, "note": {"type": "string", "optional": true}}},
            "hitPolicy": "FIRST",
            "ifFactMissing": {"level": 0},
            "bindingSettableAt": ["top"],
            "policies": {
              "q": {
                "settings": {},
                "default": {"level": 1},
                "requiredFacts": ["parent.flag"],
                "rules": [{"id": "on", "name": "flag on", "when": [{"fact": "parent.flag", "op": "eq", "value": true}], "then": {"level": 2, "note": "n"}}]
              }
            },
            "bindings": {"top": {"": {"policy": "q", "settings": {}}}}
          },
          "f.null": {
            "decidedPer": "parent",
            "returns": {"enum": ["YES"], "nullable": true},
            "hitPolicy": "FIRST",
            "ifFactMissing": null,
            "bindingSettableAt": ["top"],
            "policies": {
              "n": {
                "settings": {},
                "default": null,
                "requiredFacts": ["parent.flag"],
                "rules": [{"id": "y", "name": "yes", "when": [{"fact": "parent.flag", "op": "eq", "value": true}], "then": "YES"}]
              }
            },
            "bindings": {"top": {"": {"policy": "n", "settings": {}}}}
          }
        }
      }
      """;

  private TestBundles() {
  }

  /** The canonical bytes of the test bundle. */
  static byte[] bytes() {
    Object content = new Load(LoadSettings.builder().build()).loadFromString(CONTENT);
    String version = "sha256:" + sha256Hex(canonical(content).getBytes(StandardCharsets.UTF_8));
    Map<String, Object> envelope = new TreeMap<>();
    envelope.put("bundleVersion", version);
    envelope.put("content", content);
    return canonical(envelope).getBytes(StandardCharsets.UTF_8);
  }

  /** The loaded test bundle. */
  static LoadedBundle bundle() {
    return Tierwise.loadBundle(bytes());
  }

  /** An evaluator whose service-sourced level {@code low} is served by the given provider. */
  static Evaluator evaluator(LevelValueProvider low) {
    return Tierwise.evaluator(
        bundle(), EvaluatorOptions.builder().provider("low", low, Duration.ofMillis(500)).build());
  }

  /** An evaluator whose {@code low} provider returns nothing for every node. */
  static Evaluator evaluator() {
    return evaluator(node -> Map.of());
  }

  private static String sha256Hex(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Writes canonical JSON for the data this fixture holds: sorted members, ASCII text, integers. */
  @SuppressWarnings("unchecked")
  private static String canonical(Object value) {
    if (value == null) {
      return "null";
    }
    if (value instanceof Map<?, ?> map) {
      StringBuilder out = new StringBuilder("{");
      boolean first = true;
      for (Map.Entry<String, Object> entry : new TreeMap<>((Map<String, Object>) map).entrySet()) {
        out.append(first ? "" : ",").append(quote(entry.getKey())).append(':').append(canonical(entry.getValue()));
        first = false;
      }
      return out.append('}').toString();
    }
    if (value instanceof List<?> list) {
      StringBuilder out = new StringBuilder("[");
      for (int i = 0; i < list.size(); i++) {
        out.append(i == 0 ? "" : ",").append(canonical(list.get(i)));
      }
      return out.append(']').toString();
    }
    if (value instanceof String text) {
      return quote(text);
    }
    return value.toString();
  }

  private static String quote(String text) {
    return '"' + text.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
  }
}
