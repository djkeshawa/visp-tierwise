package io.github.visplabs.tierwise.core.internal.check;

/**
 * The kind and naming declaration stage (KIND-1 to KIND-12, LINT-14 to LINT-18, SVC-4).
 *
 * <p>The stage reads the {@code naming} and {@code kinds} members of the schema documents, presets
 * included, in layer order, reports every fault of a declaration, and publishes two tables of
 * compile-time data with {@link CheckContext#provide(Class, Object)}: a {@link NamingTable} and a
 * {@link KindTable}. A later layer replaces an earlier definition of the same name entirely
 * (MANIFEST-32, MANIFEST-33). The tables are always published: a faulty member is left out of a
 * definition, so that it imposes no constraint and one fault gives one finding (FIND-35).
 *
 * <p>The naming declarations are checked whatever other findings exist. A naming pattern is
 * {@code E_PRESET} when a preset declares it badly and {@code E_INVALID_DECLARATION} when a shared
 * schema file does (LINT-14, LINT-17). A kind definition is {@code E_KIND_DEFINITION}: an unknown
 * member, an empty or invalid {@code strategies} list, a {@code narrowing_only} definition that allows a
 * strategy that is not narrowing, a {@code require_default} definition that allows a commutative strategy,
 * an {@code on_source_failure} other than {@code skip} or {@code deny}, and a {@code naming} member that
 * does not name a declared pattern of category {@code key}.
 */
public final class KindStage implements Stage {

  @Override
  public String name() {
    return "kinds";
  }

  @Override
  public void run(CheckContext context) {
    NamingTable naming = NamingDeclarations.read(context);
    context.provide(NamingTable.class, naming);
    context.provide(KindTable.class, KindDeclarations.read(context, naming));
  }
}
