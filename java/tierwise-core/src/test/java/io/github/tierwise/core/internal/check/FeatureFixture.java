package io.github.tierwise.core.internal.check;

import io.github.tierwise.core.CompileOptions;
import io.github.tierwise.core.Finding;
import io.github.tierwise.core.internal.model.EntityDef;
import io.github.tierwise.core.internal.model.FactDef;
import io.github.tierwise.core.internal.model.Hierarchy;
import io.github.tierwise.core.internal.model.ScopeDef;
import io.github.tierwise.core.internal.model.TypeDecl;
import io.github.tierwise.core.internal.project.LintSettings;
import io.github.tierwise.core.internal.project.Origin;
import io.github.tierwise.core.internal.project.ProjectLoad;
import io.github.tierwise.core.internal.project.SourceDocument;
import io.github.tierwise.core.internal.project.SourceRole;
import io.github.tierwise.core.internal.project.SourceSet;
import io.github.tierwise.core.internal.yaml.YamlLoadResult;
import io.github.tierwise.core.internal.yaml.YamlLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A small project for the tests of the feature stage. The hierarchy, the entities and the facts are built
 * directly, as the earlier stages would publish them, so that these tests exercise the feature stage alone.
 *
 * <p>The model is a hierarchy with the root level {@code global}, a closed level {@code division} with the
 * nodes {@code north} and {@code south}, a service-sourced level {@code account}, and an axis
 * {@code seller}. The entities are {@code customer}, {@code invoice} (which has {@code order}),
 * {@code order} (which belongs to {@code customer} and has {@code item}), {@code item} and
 * {@code warehouse}. A decision about {@code order} therefore reads {@code customer.*} and {@code order.*}
 * as single-value facts and {@code item.*} as child facts, while {@code invoice.*} and {@code warehouse.*}
 * are out of scope for it, and {@code item.*} lies two {@code has_many} edges below {@code invoice}.
 */
final class FeatureFixture {

  /** A feature that decides per order, with the enum effects ALLOW and DENY. */
  static final String GATE = """
      features:
        gate:
          decided_per: order
          returns: { enum: [ALLOW, DENY] }
          hit_policy: FIRST
          binding_settable_at: [global, division]
          if_fact_missing: DENY
      """;

  /** A clean rules file for {@link #GATE}. */
  static final String GATE_RULES = """
      feature: gate
      policies:
        strict:
          settings:
            allowed: { list_of: item.stock_type, default: [] }
            held: { value_of: customer.credit_hold, default: true }
          default: DENY
          rules:
            - id: good
              name: Good credit
              when:
                customer.credit_hold: false
                customer.active: true
              then: ALLOW
            - id: stock
              name: Allowed stock
              when:
                item.stock_type: { all_in: $allowed }
                customer.credit_hold: $held
              then: ALLOW
        open:
          default: ALLOW
          rules: []
      bindings:
        global: { policy: strict }
        division:
          north: { policy: open }
          south: { policy: strict, allowed: [STOCKED, SPECIAL_ORDER] }
      """;

  /** The outcome of running the stage. */
  record Outcome(Findings findings, ModelParts parts) {

    /** The findings as {@code CODE [path]} lines, sorted. */
    List<String> found() {
      List<String> list = new ArrayList<>();
      for (Finding finding : findings.sorted()) {
        list.add(finding.code() + " " + finding.path());
      }
      return list;
    }

    /** The findings as {@code CODE line [path]} lines, in report order. */
    List<String> located() {
      List<String> list = new ArrayList<>();
      for (Finding finding : findings.sorted()) {
        list.add(finding.code() + " " + finding.line() + " " + finding.path());
      }
      return list;
    }
  }

  private FeatureFixture() {
  }

  /** Runs the stage over a schema document with {@code features} and the rules documents. */
  static Outcome run(String schema, String... rules) {
    List<SourceDocument> documents = new ArrayList<>();
    documents.add(document("schema/core.yaml", SourceRole.SCHEMA, schema));
    for (int i = 0; i < rules.length; i++) {
      documents.add(document("rules/r" + i + ".yaml", SourceRole.RULES, rules[i]));
    }
    SourceSet sources = new SourceSet(null, documents, LintSettings.none(), Optional.of("global"));
    CheckContext context = new CheckContext(new ProjectLoad(sources, List.of()), CompileOptions.defaults());
    Hierarchy hierarchy = hierarchy();
    context.parts().hierarchy(hierarchy);
    context.provide(ScopeIndex.class, new ScopeIndex(hierarchy));
    context.parts().entities(entities());
    context.parts().facts(facts());
    new FeatureStage().run(context);
    return new Outcome(context.findings(), context.parts());
  }

  /** Runs the stage over {@link #GATE} and one rules file. */
  static Outcome gate(String rules) {
    return run(GATE, rules);
  }

  /** The clean rules of {@link #GATE_RULES} with the first occurrence of one text replaced. */
  static Outcome gateWith(String from, String to) {
    if (!GATE_RULES.contains(from)) {
      throw new IllegalArgumentException("the clean rules do not contain " + from);
    }
    return gate(GATE_RULES.replace(from, to));
  }

  /** The clean {@link #GATE} declaration with one text replaced. */
  static Outcome declWith(String from, String to) {
    if (!GATE.contains(from)) {
      throw new IllegalArgumentException("the clean declaration does not contain " + from);
    }
    return run(GATE.replace(from, to), GATE_RULES);
  }

  private static SourceDocument document(String file, SourceRole role, String yaml) {
    YamlLoadResult loaded = YamlLoader.load(yaml.getBytes(StandardCharsets.UTF_8), file, false);
    if (!loaded.findings().isEmpty()) {
      throw new IllegalStateException("the fixture does not parse: " + loaded.findings());
    }
    return new SourceDocument(file, Origin.FILE, 0, role, Optional.empty(), Optional.empty(),
        loaded.root().orElseThrow(), List.of());
  }

  private static Hierarchy hierarchy() {
    Map<String, ScopeDef> scopes = new LinkedHashMap<>();
    scopes.put("division", ScopeDef.closedCatalog(ScopeDef.ValuesSource.FILES, List.of("north", "south"),
        ScopeDef.OnUnknownNode.ERROR));
    scopes.put("account", ScopeDef.openCatalog(ScopeDef.ValuesSource.SERVICE));
    scopes.put("seller", ScopeDef.closedCatalog(ScopeDef.ValuesSource.FILES, List.of("acme"),
        ScopeDef.OnUnknownNode.ERROR));
    return new Hierarchy(List.of("global", "division", "account"), List.of("seller"), scopes);
  }

  private static Map<String, EntityDef> entities() {
    Map<String, EntityDef> entities = new LinkedHashMap<>();
    entities.put("customer", new EntityDef(List.of(), List.of()));
    entities.put("invoice", new EntityDef(List.of(), List.of("order")));
    entities.put("order", new EntityDef(List.of("customer"), List.of("item")));
    entities.put("item", new EntityDef(List.of(), List.of()));
    entities.put("warehouse", new EntityDef(List.of(), List.of()));
    return entities;
  }

  private static Map<String, FactDef> facts() {
    Map<String, FactDef> facts = new LinkedHashMap<>();
    facts.put("customer.active", new FactDef("customer", TypeDecl.simple("boolean")));
    facts.put("customer.credit_hold", new FactDef("customer", TypeDecl.simple("boolean")));
    facts.put("customer.rate", new FactDef("customer", TypeDecl.simple("decimal")));
    facts.put("customer.tier", new FactDef("customer", TypeDecl.simple("string")));
    facts.put("warehouse.open", new FactDef("warehouse", TypeDecl.simple("boolean")));
    facts.put("invoice.locked", new FactDef("invoice", TypeDecl.simple("boolean")));
    facts.put("order.lines", new FactDef("order", TypeDecl.integer(1L, 500L)));
    facts.put("item.stock_type",
        new FactDef("item", TypeDecl.enumeration(List.of("DROP_SHIP", "SPECIAL_ORDER", "STOCKED"))));
    facts.put("item.unit_price", new FactDef("item", TypeDecl.simple("decimal")));
    return facts;
  }
}
