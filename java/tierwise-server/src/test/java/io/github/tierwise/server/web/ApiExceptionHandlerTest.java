package io.github.tierwise.server.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.TierwiseException;
import io.github.tierwise.server.ApiException;
import io.github.tierwise.server.store.BundleStoreException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ApiExceptionHandlerTest {

  private final ApiExceptionHandler handler = new ApiExceptionHandler();

  @ParameterizedTest
  @ValueSource(strings = {Code.E_UNKNOWN_KEY, Code.E_UNKNOWN_FEATURE})
  void unknownKeysAndFeaturesAre404(String code) {
    ResponseEntity<ErrorBody> response = handler.core(new TierwiseException(code, "the name is not declared"));
    assertEquals(404, response.getStatusCode().value());
    assertEquals(code, response.getBody().code());
    assertEquals("the name is not declared", response.getBody().message());
    assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
  }

  @ParameterizedTest
  @ValueSource(strings = {Code.E_CONTEXT_INVALID, Code.E_UNKNOWN_SCOPE, Code.E_UNKNOWN_NODE, Code.E_FACTS_INVALID})
  void callerErrorsAre400(String code) {
    ResponseEntity<ErrorBody> response = handler.core(new TierwiseException(code, "bad input"));
    assertEquals(400, response.getStatusCode().value());
    assertEquals(code, response.getBody().code());
  }

  @ParameterizedTest
  @ValueSource(strings = {Code.E_PROVIDER_MISSING, Code.E_EVALUATOR_OPTIONS, Code.E_BUNDLE_INTEGRITY,
      Code.E_PROVIDER_REQUIRED})
  void otherCoreErrorsAreFaultsOfTheServerAndHideTheirDetail(String code) {
    ResponseEntity<ErrorBody> response = handler.core(new TierwiseException(code, "secret detail"));
    assertEquals(500, response.getStatusCode().value());
    assertEquals("E_INTERNAL", response.getBody().code());
    assertFalse(response.getBody().message().contains("secret"));
  }

  @Test
  void anApiExceptionKeepsItsStatusAndCode() {
    ResponseEntity<ErrorBody> response = handler.api(new ApiException(409, "E_BUNDLE_CONFLICT", "stored bundles never change"));
    assertEquals(409, response.getStatusCode().value());
    assertEquals(new ErrorBody("E_BUNDLE_CONFLICT", "stored bundles never change"), response.getBody());
  }

  @Test
  void aStoreFailureIs503WithoutTheCause() {
    ResponseEntity<ErrorBody> response = handler.store(new BundleStoreException("s3://bucket/key is unreachable"));
    assertEquals(503, response.getStatusCode().value());
    assertEquals("E_STORE_UNAVAILABLE", response.getBody().code());
    assertFalse(response.getBody().message().contains("bucket"));
  }

  @Test
  void anyOtherFailureIs500WithoutDetail() {
    ResponseEntity<ErrorBody> response = handler.other(new IllegalStateException("password=hunter2"));
    assertEquals(500, response.getStatusCode().value());
    assertEquals("E_INTERNAL", response.getBody().code());
    assertTrue(!response.getBody().message().contains("hunter2"));
  }

  @Test
  void ifNoneMatchComparesWeaklyAndAcceptsLists() {
    String version = "sha256:" + "a".repeat(64);
    assertTrue(BundleController.matches("\"" + version + "\"", version));
    assertTrue(BundleController.matches("W/\"" + version + "\"", version));
    assertTrue(BundleController.matches(version, version));
    assertTrue(BundleController.matches("\"x\" , \"" + version + "\"", version));
    assertTrue(BundleController.matches("*", version));
    assertFalse(BundleController.matches(null, version));
    assertFalse(BundleController.matches("", version));
    assertFalse(BundleController.matches("\"sha256:" + "b".repeat(64) + "\"", version));
    assertFalse(BundleController.matches("\"" + version + "x\"", version));
  }
}
