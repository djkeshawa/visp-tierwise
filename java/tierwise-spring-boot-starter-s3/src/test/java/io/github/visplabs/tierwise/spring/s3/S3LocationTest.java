package io.github.visplabs.tierwise.spring.s3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class S3LocationTest {

  @Test
  void parsesBucketAndKeyAndKeepsSlashesInTheKey() {
    S3Location location = S3Location.parse("s3://bundles/prod/commerce/bundle.json");
    assertEquals("bundles", location.bucket());
    assertEquals("prod/commerce/bundle.json", location.key());
  }

  @Test
  void schemeIsCaseInsensitive() {
    assertEquals(new S3Location("bundles", "bundle.json"), S3Location.parse("S3://bundles/bundle.json"));
    assertTrue(S3Location.isS3Source("s3://bundles/bundle.json"));
    assertTrue(S3Location.isS3Source("S3://bundles/bundle.json"));
  }

  @Test
  void keyMayContainSpacesAndDots() {
    assertEquals("a b/c.d.json", S3Location.parse("s3://bucket/a b/c.d.json").key());
  }

  @Test
  void toStringIsTheConfiguredForm() {
    S3Location location = new S3Location("bundles", "commerce.json");
    assertEquals("s3://bundles/commerce.json", location.toString());
    assertEquals(location, S3Location.parse(location.toString()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "bundles/commerce.json", "file:///bundles/commerce.json",
      "https://bundles/commerce.json", "s3:bundles/commerce.json", "s3://", "s3:///commerce.json",
      "s3://bundles", "s3://bundles/"})
  void rejectsValuesThatAreNotAUsableLocation(String value) {
    assertThrows(IllegalArgumentException.class, () -> S3Location.parse(value));
  }

  @ParameterizedTest
  @ValueSource(strings = {"bad bucket", "bu#cket", "bu?cket"})
  void rejectsBucketNamesWithForbiddenCharacters(String bucket) {
    assertThrows(IllegalArgumentException.class, () -> new S3Location(bucket, "bundle.json"));
  }

  @Test
  void rejectsAKeyWithAControlCharacter() {
    assertThrows(IllegalArgumentException.class, () -> new S3Location("bundles", "a\u0001b"));
  }

  @Test
  void constructorRejectsNulls() {
    assertThrows(NullPointerException.class, () -> new S3Location(null, "k"));
    assertThrows(NullPointerException.class, () -> new S3Location("b", null));
    assertThrows(NullPointerException.class, () -> S3Location.parse(null));
  }

  @Test
  void isS3SourceIsFalseForNullAndOtherSchemes() {
    assertFalse(S3Location.isS3Source(null));
    assertFalse(S3Location.isS3Source("https://example.test/bundle.json"));
    assertFalse(S3Location.isS3Source("classpath:bundle.json"));
  }
}
