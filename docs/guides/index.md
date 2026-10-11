# Guides

The guides explain how to use Tierwise. Start with a [quickstart](../quickstart-java.md) ([JavaScript](../quickstart-js.md))
if you want to see it work, and come back here when you want to know why and how.

| Guide | Read it to learn |
| --- | --- |
| [Modeling guide](modeling.md) | How to describe your configuration: levels, axes, keys and merge strategies, kinds and presets, and decisions with quantifiers. Uses the `commerce` and `saas-entitlements` samples |
| [CLI reference](cli-reference.md) | Every command and option of `tierwise`, with its output and exit codes |
| [Runtime and operations](runtime-and-operations.md) | Running services on bundles: the Spring Boot starter, `@visporg/node`, refresh, last-known-good, metrics and providers |
| [Server guide](server.md) | The optional control plane: endpoints, authentication, storage, deployment and S3 |
| [OpenFeature guide](openfeature.md) | Reading Tierwise keys and decisions through OpenFeature, in Java and JavaScript |
| [Security model](security-model.md) | Browser display only, who may publish, and what a bundle digest proves |
| [API reference](api-reference.md) | Where the Javadoc and the TypeDoc output are |

The specification itself is `spec/spec.md` in the repository, with the error catalogue in `spec/error-codes.md`.
`tierwise explain <code>` prints the catalogue entry of any code from the command line. The [requirements](../requirements.md)
and the [decision records](../adr/0001-phase-boundaries.md) say why Tierwise is the way it is.

## The samples

| Sample | Shows | Used in |
| --- | --- | --- |
| `samples/commerce` | Six levels, an axis, service-sourced levels, five merge strategies, entity-aware decisions with `all_in` and `any_in` | The [modeling guide](modeling.md), the server and OpenFeature guides, the quickstarts |
| `samples/retail-pos` | A second, unrelated domain with its own kinds and no preset; `union`, `any` and `max` | The [modeling guide](modeling.md) |
| `samples/saas-entitlements` | A one-file project with the `entitlements` preset: limits, features, grants and fail-closed providers | The [modeling guide](modeling.md) |

Every name in a guide comes from one of these samples or from the template of `tierwise init`. They are examples:
Tierwise has no built-in level, key or decision names.
