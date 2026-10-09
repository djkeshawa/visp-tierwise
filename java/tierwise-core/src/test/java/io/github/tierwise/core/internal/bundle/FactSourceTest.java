package io.github.tierwise.core.internal.bundle;

import static io.github.tierwise.core.internal.bundle.BundleFixtures.assertFault;
import static io.github.tierwise.core.internal.bundle.BundleFixtures.bundle;
import static io.github.tierwise.core.internal.bundle.BundleFixtures.mapAt;
import static io.github.tierwise.core.internal.bundle.BundleFixtures.richContent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.Context;
import io.github.tierwise.core.Evaluator;
import io.github.tierwise.core.EvaluatorOptions;
import io.github.tierwise.core.FactSet;
import io.github.tierwise.core.Tierwise;
import io.github.tierwise.core.TierwiseException;
import io.github.tierwise.core.internal.model.FactDef;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * FACT-10: the {@code source} member of a fact declaration is documentation only. A bundle cannot carry it
 * (BUNDLE-49, BUNDLE-109), so the evaluator has nothing but the fact's type to validate a value with
 * (spec/conformance/enforcers.d/E6.yaml). The compile side is observed by compile cases (VEC-94).
 */
class FactSourceTest {

  @Test
  void FACT_10_aBundleThatCarriesAFactSourceIsRejectedWhateverItsText() {
    for (String source : List.of("doc", "boolean or the text yes", "")) {
      Map<String, Object> content = richContent();
      mapAt(content, "facts", "customer.active").put("source", source);
      assertFault(content);
    }
  }

  @Test
  void FACT_10_theEvaluatorValidatesAFactByItsTypeAlone() {
    assertEquals(List.of("entity", "type"),
        Arrays.stream(FactDef.class.getRecordComponents()).map(component -> component.getName()).toList());
    Evaluator evaluator = Tierwise.evaluator(Tierwise.loadBundle(bundle(richContent())),
        EvaluatorOptions.builder().provider("account", node -> Map.of(), Duration.ofSeconds(1)).build());
    evaluator.decide(Context.empty(), "checkout.submission", FactSet.builder().fact("customer.active", true).build());
    TierwiseException e = assertThrows(TierwiseException.class, () -> evaluator.decide(Context.empty(),
        "checkout.submission", FactSet.builder().fact("customer.active", "yes").build()));
    assertEquals(Code.E_FACTS_INVALID, e.code());
  }
}
