# tierwise-core

The Tierwise reference compiler and evaluator for Java 17+. It implements spec 0.1 and bundle format 1, and it passes every compile, evaluate and canonical conformance vector.

- `Tierwise.compile(Path)` validates a project and, when there is no error-severity finding, returns the canonical bundle.
- `Tierwise.loadBundle(byte[])` verifies a bundle, and `Tierwise.evaluator(bundle, options)` gives `resolve`, `resolveLocal` and `decide`, each returning results with provenance.

Runtime dependencies: snakeyaml-engine (YAML 1.2) and jackson-core (strict JSON). There is no Spring, no networking and no logging framework. See [docs/design/java-core.md](../../docs/design/java-core.md) and [docs/quickstart-java.md](../../docs/quickstart-java.md).
