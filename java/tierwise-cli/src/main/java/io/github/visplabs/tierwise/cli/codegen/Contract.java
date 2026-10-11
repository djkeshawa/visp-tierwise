package io.github.visplabs.tierwise.cli.codegen;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The typed surface of one compiled project: its keys and features, in the order of the bundle (which
 * is code point order). Codegen reads only this; the emitters never see the bundle bytes.
 *
 * @param bundleVersion the digest of the bundle that the surface was read from
 * @param keys the declared keys
 * @param features the declared features
 */
record Contract(String bundleVersion, List<KeyDecl> keys, List<FeatureDecl> features) {

  /**
   * A declared key.
   *
   * @param name the key name
   * @param combine the merge strategy, as the source spells it
   * @param type the declared type
   */
  record KeyDecl(String name, String combine, TypeDecl type) {

    /** Validates the components. */
    public KeyDecl {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(combine, "combine");
      Objects.requireNonNull(type, "type");
    }

    /**
     * Tells whether the key's result can be absent: {@code min} and {@code max} have no identity, so
     * no contributing scope leaves the result absent (MERGE-6).
     *
     * @return {@code true} for {@code min} and {@code max}
     */
    boolean absentPossible() {
      return combine.equals("min") || combine.equals("max");
    }
  }

  /**
   * A declared feature.
   *
   * @param name the feature name
   * @param returns the form of its effects
   */
  record FeatureDecl(String name, Returns returns) {

    /** Validates the components. */
    public FeatureDecl {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(returns, "returns");
    }
  }

  /** The form of a feature's effects (FEAT-9, FEAT-11). */
  sealed interface Returns {

    /**
     * The enum form.
     *
     * @param effects the declared effects, in code point order
     * @param nullable whether the effect may be {@code null}
     */
    record EnumForm(List<String> effects, boolean nullable) implements Returns {

      /** Copies the effects. */
      public EnumForm {
        effects = List.copyOf(effects);
      }
    }

    /**
     * The object form.
     *
     * @param fields the fields of the effect, in code point order
     */
    record ObjectForm(Map<String, TypeDecl.Field> fields) implements Returns {

      /** Copies the fields, keeping their order. */
      public ObjectForm {
        fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
      }
    }
  }
}
