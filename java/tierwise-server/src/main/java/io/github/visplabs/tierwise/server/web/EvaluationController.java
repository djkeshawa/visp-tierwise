package io.github.visplabs.tierwise.server.web;

import io.github.visplabs.tierwise.server.service.EvaluationService;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The evaluation endpoints of requirements section 9: resolve, decide and schema. */
@RestController
@RequestMapping("/v1")
public class EvaluationController {

  private final EvaluationService evaluation;

  /**
   * Creates the controller.
   *
   * @param evaluation the evaluation service
   */
  public EvaluationController(EvaluationService evaluation) {
    this.evaluation = evaluation;
  }

  /**
   * {@code POST /v1/resolve}: resolves keys for a context.
   *
   * @param request {@code {context, keys[]}}
   * @return {@code {bundleVersion, results[]}} with the core's provenance for every key
   */
  @PostMapping(path = "/resolve", consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> resolve(@RequestBody Map<String, Object> request) {
    return evaluation.resolve(request);
  }

  /**
   * {@code POST /v1/decide}: decides a feature for a context and a FactSet.
   *
   * @param request {@code {context, feature, input}}
   * @return the decision with its trace
   */
  @PostMapping(path = "/decide", consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> decide(@RequestBody Map<String, Object> request) {
    return evaluation.decide(request);
  }

  /**
   * {@code GET /v1/schema}: the keys, features, facts and policies of the latest bundle.
   *
   * @return the schema view
   */
  @GetMapping(path = "/schema", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> schema() {
    return evaluation.schema();
  }
}
