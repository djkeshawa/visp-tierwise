package rich.gen;

import io.github.tierwise.core.Context;
import io.github.tierwise.core.EvaluatorOptions;
import io.github.tierwise.core.FactSet;
import io.github.tierwise.core.LoadedBundle;
import io.github.tierwise.core.Tierwise;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Calls the generated handles on a real evaluator. The codegen tests compile this file together with the
 * generated package of the rich project, then call {@code run}; any mismatch throws an
 * {@link AssertionError}.
 */
public final class RichUsage {

  private RichUsage() {
  }

  /**
   * Runs the checks against the rich project.
   *
   * @param project the directory of the rich project
   * @return {@code ok} when every check passes
   */
  public static String run(Path project) {
    LoadedBundle bundle = Tierwise.loadBundle(Tierwise.compile(project).bundle().orElseThrow());
    TypedEvaluator typed = new TypedEvaluator(Tierwise.evaluator(bundle, EvaluatorOptions.none()));
    Context context = Context.builder().level("division", "south").axis("seller", "acme").build();

    String fee = typed.resolveLocal(context, Keys.RICH_FEE_RATE).value();
    require("0.5".equals(fee), "a decimal is a canonical string: " + fee);

    TypedResolved<String> tier = typed.resolve(context, Keys.RICH_TIER_NAME);
    require("BASIC".equals(tier.value()), "the default enum value applies");

    TypedResolved<Long> limit = typed.resolveLocal(context, Keys.RICH_LIMIT_COUNT);
    require(!limit.hasValue() && limit.value() == null, "a max key with no contributing scope is absent");

    List<Map<String, Object>> windows = typed.resolveLocal(context, Keys.RICH_RULES_WINDOWS).value();
    require(windows.isEmpty(), "a list of objects has its default");

    TypedDecision<Features.RichGateCheckEffect> gate =
        typed.decide(context, Features.RICH_GATE_CHECK, FactSet.of(Map.<String, Object>of("order.paid", Boolean.TRUE), Map.of()));
    require(gate.effect() == Features.RichGateCheckEffect.ALLOW, "a matching rule gives its enum constant");

    TypedDecision<Features.RichGateCheckEffect> missing =
        typed.decide(context, Features.RICH_GATE_CHECK, FactSet.empty());
    require(missing.effect() == Features.RichGateCheckEffect.DENY, "a missing fact gives if_fact_missing");

    require(Features.RichGateCheckEffect.fromWire(null) == null, "a nullable enum decodes null to null");

    TypedDecision<Map<String, Object>> quote = typed.decide(context, Features.RICH_QUOTE_SHAPE, FactSet.empty());
    require(Long.valueOf(5).equals(quote.effect().get("days")), "an integer field is a Long");
    require("GROUND".equals(quote.effect().get("method")), "an object effect keeps its enum field");

    return "ok";
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(message);
    }
  }
}
