package io.github.visplabs.tierwise.core.internal.check;

import io.github.visplabs.tierwise.core.internal.model.Hierarchy;
import io.github.visplabs.tierwise.core.internal.model.KeyDef;
import io.github.visplabs.tierwise.core.internal.project.SourceDocument;
import io.github.visplabs.tierwise.core.internal.project.ValueGroup;
import io.github.visplabs.tierwise.core.internal.yaml.PMap;
import io.github.visplabs.tierwise.core.internal.yaml.PNode;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The key declaration stage (section 5, KEY, MERGE, AXIS and KIND areas; section 6, SVC declarations).
 *
 * <p>The stage reads the {@code keys} member of every schema document, presets and namespaced files
 * included, and checks each declaration with {@link KeyChecks}: the members and their shapes, the type
 * ({@link TypeDeclParser}), the strategy and the type it fits, the default, {@code settable_at},
 * {@code combine_across_axes}, the kind constraints and the source-failure rules. A key whose
 * {@code type} or {@code combine} is missing or invalid gets no further check (FIND-35). A repeated key
 * name is {@code E_DUPLICATE_DEFINITION} at the later declaration, which the loader reports, and the
 * later declaration is not checked again.
 *
 * <p>Produces: a {@link KeyTable} for {@link ValueStage} with {@link CheckContext#provide(Class, Object)}
 * (always), and {@link ModelParts#keys(java.util.Map)} (only when every declaration is complete and has no
 * fault, and the hierarchy is built).
 *
 * <p>Reads: the {@code keys} member of the schema documents, {@link ModelParts#hierarchy()}, the
 * {@link KindTable} of {@link KindStage}, and the root values of the values documents (MERGE-7).
 */
public final class KeyStage implements Stage {

  @Override
  public String name() {
    return "keys";
  }

  @Override
  public void run(CheckContext context) {
    Optional<Hierarchy> hierarchy = context.parts().hierarchy();
    KeyChecks checks = new KeyChecks(context.findings(), hierarchy, context.find(KindTable.class),
        rootValueKeys(context, hierarchy));
    Map<String, KeyInfo> infos = new LinkedHashMap<>();
    Map<String, KeyDef> defs = new LinkedHashMap<>();
    boolean complete = hierarchy.isPresent();
    for (SourceDocument document : context.sources().schemas()) {
      Optional<PNode> keys = document.root().get("keys");
      if (keys.isEmpty()) {
        continue;
      }
      if (!(keys.get() instanceof PMap declarations)) {
        complete = false;
        continue;
      }
      for (Map.Entry<String, PNode> entry : declarations.entries().entrySet()) {
        if (infos.containsKey(entry.getKey())) {
          complete = false;
          continue;
        }
        KeyChecks.Result result = checks.check(entry.getKey(), entry.getValue());
        infos.put(entry.getKey(), result.info());
        result.def().ifPresent(def -> defs.put(entry.getKey(), def));
        complete &= result.def().isPresent();
      }
    }
    context.provide(KeyTable.class, new KeyTable(infos));
    if (complete) {
      context.parts().keys(defs);
    }
  }

  /**
   * Collects the names of the keys that have a root value in some values document (MERGE-7). The result
   * is empty when the root level is unknown, so that the check is skipped.
   */
  private static Optional<Set<String>> rootValueKeys(CheckContext context, Optional<Hierarchy> hierarchy) {
    Optional<String> root = hierarchy.map(Hierarchy::rootLevel).or(() -> context.sources().rootLevel());
    if (root.isEmpty()) {
      return Optional.empty();
    }
    Set<String> names = new HashSet<>();
    for (SourceDocument document : context.sources().values()) {
      for (ValueGroup group : document.groups()) {
        if (group.scope().equals(root.get())) {
          names.addAll(group.entries().entries().keySet());
        }
      }
    }
    return Optional.of(names);
  }
}
