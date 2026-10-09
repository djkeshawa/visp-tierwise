package io.github.tierwise.cli.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The Java and TypeScript types of declared values. */
class TypeMappingTest {

  private static final TypeDecl.Field REQUIRED_INTEGER = new TypeDecl.Field(new TypeDecl.IntegerType(), false);

  @Test
  void booleanIsBoxedBoolean() {
    assertEquals("Boolean", JavaEmitter.javaType(new TypeDecl.BooleanType()));
    assertEquals("boolean", TypeScriptEmitter.tsType(new TypeDecl.BooleanType()));
  }

  @Test
  void integerIsLongAndNumber() {
    assertEquals("Long", JavaEmitter.javaType(new TypeDecl.IntegerType()));
    assertEquals("number", TypeScriptEmitter.tsType(new TypeDecl.IntegerType()));
  }

  @Test
  void decimalStringAndEnumAreStrings() {
    assertEquals("String", JavaEmitter.javaType(new TypeDecl.DecimalType()));
    assertEquals("String", JavaEmitter.javaType(new TypeDecl.StringType()));
    assertEquals("String", JavaEmitter.javaType(new TypeDecl.EnumType(List.of("A", "B"))));
    assertEquals("string", TypeScriptEmitter.tsType(new TypeDecl.DecimalType()));
    assertEquals("string", TypeScriptEmitter.tsType(new TypeDecl.StringType()));
  }

  @Test
  void enumIsAUnionOfItsMembers() {
    assertEquals("\"A\" | \"B\"", TypeScriptEmitter.tsType(new TypeDecl.EnumType(List.of("A", "B"))));
  }

  @Test
  void setIsAListOfStringsAndAReadonlyArrayOfTheUniverse() {
    TypeDecl set = new TypeDecl.SetType(List.of("GROUND", "PICKUP"));
    assertEquals("List<String>", JavaEmitter.javaType(set));
    assertEquals("ReadonlyArray<\"GROUND\" | \"PICKUP\">", TypeScriptEmitter.tsType(set));
  }

  @Test
  void listTakesTheTypeOfItsItemsInBothLanguages() {
    TypeDecl list = new TypeDecl.ListType(new TypeDecl.ListType(new TypeDecl.IntegerType()));
    assertEquals("List<List<Long>>", JavaEmitter.javaType(list));
    assertEquals("ReadonlyArray<ReadonlyArray<number>>", TypeScriptEmitter.tsType(list));
  }

  @Test
  void objectIsAMapInJavaAndAFieldTypeInTypeScript() {
    Map<String, TypeDecl.Field> fields = new LinkedHashMap<>();
    fields.put("start", REQUIRED_INTEGER);
    fields.put("note", new TypeDecl.Field(new TypeDecl.StringType(), true));
    TypeDecl object = new TypeDecl.ObjectType(fields);
    assertEquals("Map<String, Object>", JavaEmitter.javaType(object));
    assertEquals("{ readonly \"start\": number; readonly \"note\"?: string; }", TypeScriptEmitter.tsType(object));
  }

  @Test
  void objectFieldsKeepTheirDeclaredOrder() {
    Map<String, TypeDecl.Field> fields = new LinkedHashMap<>();
    fields.put("zeta", REQUIRED_INTEGER);
    fields.put("alpha", REQUIRED_INTEGER);
    assertEquals(List.of("zeta", "alpha"), List.copyOf(new TypeDecl.ObjectType(fields).fields().keySet()));
  }

  @Test
  void listOfObjectsIsAListOfMapsInJava() {
    TypeDecl list = new TypeDecl.ListType(new TypeDecl.ObjectType(Map.of("start", REQUIRED_INTEGER)));
    assertEquals("List<Map<String, Object>>", JavaEmitter.javaType(list));
  }
}
