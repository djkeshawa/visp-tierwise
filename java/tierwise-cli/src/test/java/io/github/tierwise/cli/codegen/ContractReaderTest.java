package io.github.tierwise.cli.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.CompileResult;
import io.github.tierwise.core.Tierwise;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Reads the typed surface of compiled bundles. */
class ContractReaderTest {

  private static Contract contractOf(Path project) {
    CompileResult result = Tierwise.compile(project);
    assertFalse(result.hasErrors(), () -> "the test project must compile: " + result.findings());
    return ContractReader.read(result.bundle().orElseThrow());
  }

  @Test
  void listsKeysInTheOrderOfTheBundle() throws IOException {
    Contract contract = contractOf(CodegenTestSupport.sample("commerce"));
    assertEquals(List.of(
        "checkout.container_order.allowed",
        "checkout.order.allowed_max_lines",
        "checkout.pickup.lead_hours",
        "checkout.pickup.window",
        "checkout.shipping.allowed_methods"),
        contract.keys().stream().map(Contract.KeyDecl::name).toList());
  }

  @Test
  void aKeyMergedWithMinOrMaxMayBeAbsent() {
    Contract contract = contractOf(CodegenTestSupport.sample("commerce"));
    Contract.KeyDecl minimum = contract.keys().get(1);
    Contract.KeyDecl override = contract.keys().get(2);
    assertEquals("min", minimum.combine());
    assertTrue(minimum.absentPossible());
    assertEquals("override", override.combine());
    assertFalse(override.absentPossible());
  }

  @Test
  void readsEveryDeclaredValueFormOfTheRichProject() {
    Contract contract = contractOf(CodegenTestSupport.resourceProject("rich"));
    TypeDecl byKey = null;
    for (Contract.KeyDecl key : contract.keys()) {
      if (key.name().equals("rich.rules.windows")) {
        byKey = key.type();
      }
    }
    TypeDecl.ListType windows = assertInstanceOf(TypeDecl.ListType.class, byKey);
    TypeDecl.ObjectType item = assertInstanceOf(TypeDecl.ObjectType.class, windows.items());
    assertTrue(item.fields().get("note").optional());
    assertFalse(item.fields().get("start").optional());
  }

  @Test
  void readsAnEnumFormWithItsNullableFlag() {
    Contract contract = contractOf(CodegenTestSupport.resourceProject("rich"));
    Contract.FeatureDecl gate = contract.features().get(0);
    Contract.Returns.EnumForm form = assertInstanceOf(Contract.Returns.EnumForm.class, gate.returns());
    assertEquals(List.of("ALLOW", "DENY"), form.effects());
    assertTrue(form.nullable());
  }

  @Test
  void readsAnObjectFormWithItsFields() {
    Contract contract = contractOf(CodegenTestSupport.resourceProject("rich"));
    Contract.FeatureDecl quote = contract.features().get(1);
    Contract.Returns.ObjectForm form = assertInstanceOf(Contract.Returns.ObjectForm.class, quote.returns());
    assertEquals(List.of("days", "method", "note"), List.copyOf(form.fields().keySet()));
    assertTrue(form.fields().get("note").optional());
  }
}
