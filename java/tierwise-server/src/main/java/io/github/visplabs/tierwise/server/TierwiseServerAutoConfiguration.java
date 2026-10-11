package io.github.visplabs.tierwise.server;

import io.github.visplabs.tierwise.core.EvaluationListener;
import io.github.visplabs.tierwise.core.LevelValueProvider;
import io.github.visplabs.tierwise.server.service.BundleService;
import io.github.visplabs.tierwise.server.service.EvaluationService;
import io.github.visplabs.tierwise.server.store.BundleStore;
import io.github.visplabs.tierwise.server.store.FilesystemBundleStore;
import io.github.visplabs.tierwise.server.web.ApiExceptionHandler;
import io.github.visplabs.tierwise.server.web.BundleController;
import io.github.visplabs.tierwise.server.web.EvaluationController;
import io.github.visplabs.tierwise.server.web.OpenApiController;
import jakarta.servlet.MultipartConfigElement;
import java.util.Map;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.util.unit.DataSize;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Registers the beans of the server: the bundle store (the filesystem store, unless the application defines
 * a {@link BundleStore}), the services, the controllers and the JSON error mapping.
 *
 * <p>Provider beans: every {@link LevelValueProvider} bean serves the service-sourced level whose name is
 * the bean name. {@link EvaluationListener} beans are optional.
 */
@AutoConfiguration(
    afterName = "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration",
    beforeName = "org.springframework.boot.servlet.autoconfigure.MultipartAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(TierwiseServerProperties.class)
public class TierwiseServerAutoConfiguration {

  /** The room that a multipart request needs beyond the bundle itself: the record part and the framing. */
  private static final DataSize MULTIPART_OVERHEAD = DataSize.ofKilobytes(64);

  /**
   * Limits multipart requests to the configured bundle size plus a small allowance, so
   * {@code tierwise.server.max-bundle-size} is the one setting that bounds a publish.
   *
   * @param properties the server configuration
   * @return the multipart limits
   */
  @Bean
  @ConditionalOnMissingBean
  public MultipartConfigElement tierwiseMultipartConfig(TierwiseServerProperties properties) {
    DataSize limit = DataSize.ofBytes(properties.maxBundleSize().toBytes() + MULTIPART_OVERHEAD.toBytes());
    MultipartConfigFactory factory = new MultipartConfigFactory();
    factory.setMaxFileSize(limit);
    factory.setMaxRequestSize(limit);
    return factory.createMultipartConfig();
  }

  /**
   * The default store, on the filesystem. It is registered when {@code tierwise.server.store} is
   * {@code filesystem} (or unset) and the application defines no {@link BundleStore}.
   *
   * @param properties the server configuration
   * @return the store
   */
  @Bean
  @ConditionalOnMissingBean(BundleStore.class)
  @ConditionalOnProperty(prefix = "tierwise.server", name = "store", havingValue = "filesystem", matchIfMissing = true)
  public BundleStore tierwiseBundleStore(TierwiseServerProperties properties) {
    return new FilesystemBundleStore(properties.filesystem().root());
  }

  /**
   * The publish, promote and read logic.
   *
   * @param store the store
   * @param properties the server configuration
   * @return the service
   */
  @Bean
  @ConditionalOnMissingBean
  public BundleService tierwiseBundleService(BundleStore store, TierwiseServerProperties properties) {
    JsonMapper duplicateChecking = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    return new BundleService(store, duplicateChecking, properties.maxBundleSize().toBytes(), properties.latestCacheTtl());
  }

  /**
   * The resolve, decide and schema logic.
   *
   * @param bundles the bundle service
   * @param json the application's JSON mapper
   * @param beans the bean factory, to find the provider beans by name
   * @param listeners the optional listener for evaluation events
   * @param properties the server configuration
   * @return the service
   */
  @Bean
  @ConditionalOnMissingBean
  public EvaluationService tierwiseEvaluationService(BundleService bundles, JsonMapper json, ListableBeanFactory beans,
      ObjectProvider<EvaluationListener> listeners, TierwiseServerProperties properties) {
    Map<String, LevelValueProvider> providers =
        BeanFactoryUtils.beansOfTypeIncludingAncestors(beans, LevelValueProvider.class);
    return new EvaluationService(bundles, json, providers, properties.providerTimeout(),
        listeners.getIfAvailable(), properties.runtimeCacheSize(), properties.maxKeysPerRequest());
  }

  /**
   * The bundle endpoints.
   *
   * @param bundles the bundle service
   * @return the controller
   */
  @Bean
  public BundleController tierwiseBundleController(BundleService bundles) {
    return new BundleController(bundles);
  }

  /**
   * The resolve, decide and schema endpoints.
   *
   * @param evaluation the evaluation service
   * @return the controller
   */
  @Bean
  public EvaluationController tierwiseEvaluationController(EvaluationService evaluation) {
    return new EvaluationController(evaluation);
  }

  /**
   * The {@code /openapi.yaml} endpoint.
   *
   * @return the controller
   */
  @Bean
  public OpenApiController tierwiseOpenApiController() {
    return new OpenApiController();
  }

  /**
   * The mapping of failures to JSON errors.
   *
   * @return the advice
   */
  @Bean
  public ApiExceptionHandler tierwiseExceptionHandler() {
    return new ApiExceptionHandler();
  }
}
