# Quickstart: JavaScript

This page takes you from nothing to a Node script that reads a Tierwise bundle and resolves a key, in about five
minutes. You install the command line, write a small project from a preset, change one value, validate it,
compile it to a bundle, and load the bundle with `@tierwise/evaluator`.

The names in this page (`quota.max_items`, `group_a`, …) come from the template that `tierwise init` writes.
They are examples. Tierwise itself knows no such names; you declare your own levels, keys and decisions.

> **Browser use is for display only.** `@tierwise/evaluator` also runs in a browser, and a result that is computed
> in a browser can be changed by anyone who controls that browser. Use it there to show what a decision is likely
> to be, for example to hide a button. Enforce every decision on a server, with the same bundle. See the
> [security model](guides/security-model.md).

Compiling a project is done by the command line, which is a Java program; JavaScript only evaluates compiled
bundles. You need Java 17 or later on the machine that compiles, which is usually your CI machine or your own.
The Docker image and the GitHub Action run the same command line without a local Java.

The Java version of this page is the [Java quickstart](quickstart-java.md).

<!-- This page is executed by scripts/quickstart-check.sh. The HTML comments before the fenced blocks tell the
     script what to do with each block. They do not show in the rendered page. See "How this page is verified". -->

## Before you start

- Node 20 or later, and npm.
- Java 17 or later, for the command line.

<!-- qs:run -->
```sh
mkdir tierwise-quickstart && cd tierwise-quickstart
```

## 1. Install the command line

The command line is one JAR file with its dependencies inside. Download `tierwise-cli-0.1.0-all.jar` from the
release, check its SHA-256 sum against the `SHA256SUMS` file of the release, and call it `tierwise.jar`:

<!-- qs:skip no release exists yet, so there is nothing to download (open question O2) -->
```sh
curl -fsSL -o tierwise.jar "$TIERWISE_RELEASE_URL/tierwise-cli-0.1.0-all.jar"
```

*(OPEN)* Nothing is published yet. The download location depends on the GitHub organisation that the project
owner has not claimed (open question O2 in [open questions](open-questions.md)), so the page uses the variable
`TIERWISE_RELEASE_URL` and does not name a host.

To use a build from source instead (the path this page is checked on), build the JAR in a checkout of the
repository and point `TIERWISE_CLI_JAR` at it:

<!-- qs:skip builds the checkout; scripts/quickstart-check.sh builds it before it runs this page -->
```sh
export TIERWISE_REPO=/path/to/your/checkout
(cd "$TIERWISE_REPO/java" && ./mvnw -B -ntp -DskipTests package)
export TIERWISE_CLI_JAR="$(ls "$TIERWISE_REPO"/java/tierwise-cli/target/tierwise-cli-*-all.jar)"
```

<!-- qs:run -->
```sh
cp "$TIERWISE_CLI_JAR" tierwise.jar
java -jar tierwise.jar --version
```

<!-- qs:expect -->
```text
tierwise 0.1.0
```

## 2. Start a project from a preset

`tierwise init` writes a project that extends one of the built-in presets. The `entitlements` preset is for
plans and limits: numeric limits, on/off features and granted sets.

<!-- qs:run -->
```sh
java -jar tierwise.jar init --preset entitlements my-config
```

<!-- qs:expect -->
```text
created my-config/tierwise.yaml (preset: entitlements)
next: tierwise validate my-config
```

The project is one file, `my-config/tierwise.yaml`. Its top half declares two levels (`base`, then `group`) and
three keys, each with the kind of the preset: a `limit` (the lowest value on the path wins), a `feature` (any
`false` turns it off) and a `grant` (every level adds its members). Its bottom half holds the values:

<!-- qs:run -->
```sh
sed -n '/^values:/,$p' my-config/tierwise.yaml
```

<!-- qs:expect -->
```text
values:
  base:
    quota.max_items: 100
    search.granted_fields: [title]
  group:
    group_a:
      quota.max_items: 25
    group_b:
      search.has_suggestions: false
      search.granted_fields: [body]
```

Check that the project is valid. The command exits with 0 when nothing is an error:

<!-- qs:run -->
```sh
java -jar tierwise.jar validate my-config
```

<!-- qs:expect -->
```text
validate: no findings
```

## 3. Change one value

Group `group_a` has a limit of 25 items. Lower it to 10. Open `my-config/tierwise.yaml` in an editor and change
`quota.max_items: 25` to `quota.max_items: 10`, or run this command, which does the same:

<!-- qs:run -->
```sh
sed 's/quota\.max_items: 25$/quota.max_items: 10/' my-config/tierwise.yaml > my-config/tierwise.yaml.new
mv my-config/tierwise.yaml.new my-config/tierwise.yaml
java -jar tierwise.jar validate my-config
```

<!-- qs:expect -->
```text
validate: no findings
```

### See a typo fail

Tierwise rejects a name that is not declared, so a typo cannot silently drop a limit. Make a copy of the project
with the key misspelled, and validate the copy:

<!-- qs:run fail -->
```sh
cp -R my-config broken
sed 's/quota\.max_items: 10$/quota.max_itmes: 10/' my-config/tierwise.yaml > broken/tierwise.yaml
java -jar tierwise.jar validate broken
```

<!-- qs:expect -->
```text
tierwise.yaml:57: error E_UNKNOWN_KEY the key quota.max_itmes is not declared (KEY-10, SRC-35) (suggestion: quota.max_items)
validate: 1 error, 0 warnings, 0 infos
```

The command exited with 1. A finding names the file, the line, the severity, a stable code and, where it can, a
suggestion. `java -jar tierwise.jar explain E_UNKNOWN_KEY` explains the code. Your original project is unchanged.

## 4. Compile the bundle

`compile` validates the project again and writes the bundle: one canonical JSON file whose `bundleVersion` is the
SHA-256 digest of its content.

<!-- qs:run -->
```sh
java -jar tierwise.jar compile my-config -o bundle.json
```

<!-- qs:expect -->
```text
wrote bundle.json (916 bytes, sha256:8d914ebfd939011953b8dbe2b537b3e06a744b0bbc70ae8eab09997c143fddee)
```

The same project always compiles to the same bytes and the same digest, in every implementation. If you made the
same edit, you get the same digest.

## 5. Load the bundle from Node

Make a directory `app` for the script, with a `package.json` that makes `.js` files ES modules:

<!-- qs:run -->
```sh
mkdir app
```

<!-- qs:file app/package.json -->
```json
{
  "name": "tierwise-quickstart",
  "private": true,
  "type": "module"
}
```

Install the evaluator. It has no runtime dependencies:

<!-- qs:skip the package is private until the first release, so there is nothing to install from a registry -->
```sh
npm install @tierwise/evaluator
```

*(OPEN)* `@tierwise/evaluator` is private and not published. It will be installable from the npm registry after
the first release, once the project owner has claimed the `@tierwise` scope (open question O2). Until then, depend
on the package directory of a checkout. This is the path this page is checked on:

<!-- qs:run -->
```sh
(cd app && npm install "file:$TIERWISE_REPO/js/evaluator")
```

The checkout must have been built (`pnpm --dir js install --frozen-lockfile && pnpm --dir js run build`), because the
package ships its compiled files in `dist/`.

Create `app/resolve.mjs`. It loads the bundle, which checks the digest and the versions, creates an evaluator, and
resolves `quota.max_items` for each group:

<!-- qs:file app/resolve.mjs -->
```js
import { readFileSync } from 'node:fs';
import { createEvaluator, loadBundle } from '@tierwise/evaluator';

const bundle = loadBundle(readFileSync(process.argv[2]));
const evaluator = createEvaluator(bundle, {});

for (const group of ['group_a', 'group_b']) {
  const resolved = await evaluator.resolve({ levels: { group } }, 'quota.max_items');
  console.log(`${group} quota.max_items = ${resolved.value}`);
  for (const c of resolved.contributions) {
    const scope = c.name + (c.node === undefined ? '' : `=${c.node}`);
    console.log(`  ${scope}: ${c.value} ${c.effect}`);
  }
}
```

Run it:

<!-- qs:run -->
```sh
node app/resolve.mjs bundle.json
```

<!-- qs:expect -->
```text
group_a quota.max_items = 10
  base: 100 NARROWED
  group=group_a: 10 NARROWED
group_b quota.max_items = 100
  base: 100 NARROWED
```

That is the whole loop. The result for `group_a` is 10, the lowest of the two limits, and every contribution is
listed with the level and node it came from. `group_b` sets no limit, so the base limit stands. The script did no
parsing of YAML and no merging: it read the bundle and asked a question.

Things to know about the API:

- `loadBundle(bytesOrString)` is synchronous. It throws a `TierwiseError` with the code `E_BUNDLE_INTEGRITY` or
  `E_BUNDLE_UNSUPPORTED` when the input is not a valid bundle.
- `createEvaluator(bundle, options)` needs one provider for each service-sourced level of the bundle. This bundle
  has none, so the options are empty.
- An evaluator is immutable. Create it once and keep it. A result is a plain object that shares nothing with the
  bundle.
- `resolve` rejects with a `TierwiseError` and a stable `code` for a caller mistake: an unknown key
  (`E_UNKNOWN_KEY`), an unknown level (`E_UNKNOWN_SCOPE`) or an unlisted node (`E_UNKNOWN_NODE`).
- `resolveSync` and `decideSync` are the synchronous variants. They never call a provider.
- There is no way to read one level's raw value. You always get the resolved value with its provenance.

## 6. Going further: decisions

A decision answers "may this order be submitted?" from facts about the order. The `commerce` sample project in
the repository has two decisions. Compile it and decide from Node. The sample has two levels whose values come
from a service, so the evaluator needs a provider for each one; the providers here return nothing.

<!-- qs:run -->
```sh
java -jar tierwise.jar compile "$TIERWISE_REPO/samples/commerce" -o commerce.json
```

<!-- qs:file app/decide.mjs -->
```js
import { readFileSync } from 'node:fs';
import { createEvaluator, loadBundle } from '@tierwise/evaluator';

const bundle = loadBundle(readFileSync(process.argv[2]));
// Each service-sourced level needs a provider and a timeout.
const providers = {
  account: { fn: async () => ({}), timeoutMs: 200 },
  user: { fn: async () => ({}), timeoutMs: 200 },
};
const evaluator = createEvaluator(bundle, { providers });

const decision = await evaluator.decide({ levels: { division: 'south' } }, 'checkout.order_submission', {
  facts: { 'customer.active': true, 'customer.credit_hold': true },
  children: { item: [{ 'item.stock_type': 'STOCKED' }, { 'item.stock_type': 'DROP_SHIP' }] },
});
console.log(`effect  = ${decision.effect}`);
console.log(`reason  = ${decision.reason.type}`);
console.log(`binding = ${decision.binding.level}/${decision.binding.node ?? '-'}`);
```

<!-- qs:run -->
```sh
node app/decide.mjs commerce.json
```

<!-- qs:expect -->
```text
effect  = DENY
reason  = DEFAULT
binding = division/south
```

The customer is on credit hold and the basket mixes a stocked item with a drop-shipped one. Division `south` allows
only some stock types on credit hold, and "every item must be allowed" is not true for this basket, so no rule
matches and the policy default `DENY` applies. The reason says so. `decision.trace` lists the rules that were
checked. The [modeling guide](guides/modeling.md) explains `all_in` and `any_in`, and why a mixed basket is the
case worth testing.

## Where to go next

- [Modeling guide](guides/modeling.md): levels, axes, keys, strategies, kinds, decisions.
- [CLI reference](guides/cli-reference.md): every command and option.
- [Runtime and operations](guides/runtime-and-operations.md): `@tierwise/node` refreshes a bundle over HTTP, swaps
  it atomically and keeps the last good one.
- [OpenFeature guide](guides/openfeature.md): serve the same answers through OpenFeature.
- [Security model](guides/security-model.md): where to enforce a decision and what a bundle digest proves.

## How this page is verified

`scripts/quickstart-check.sh` runs every marked block of this page, in order, in an empty temporary directory with
an empty `HOME` and an empty npm cache, and compares the output of each block with the block that follows it. A
difference fails the check. It differs from your machine in three ways, and no more:

1. The release downloads are skipped, because nothing is released yet. The script builds the command line from
   the working tree (`./mvnw install`) and `@tierwise/evaluator` (`pnpm run build`), and sets `TIERWISE_REPO` and
   `TIERWISE_CLI_JAR` for the build-from-source path. npm installs the evaluator from its directory with a `file:`
   specifier.
2. The evaluator has no dependencies, so npm downloads nothing in this run.
3. A development build has a version such as `0.1.0-dev.0`; the script replaces the text `0.1.0` in this page by
   the built version.

### Measured time

Wall time of the steps of this page, from the first command to the last, after the artifacts are built. Building the
command line (about 30 to 40 seconds) and the evaluator (about 2 seconds) from source, which a release makes
unnecessary, is not included.

| Date | Machine | Runs | Time |
| --- | --- | --- | --- |
| 2026-10-09 | AMD Ryzen 9 5900HS, 30 GB, Linux 7.1.1; OpenJDK 21.0.12.1, Node v26.7.0 | 3 | 2.4 s median (2.3 to 2.8 s) |

Most of that time is the start of a JVM for each command line call. The five-minute promise includes the time to read.
The check fails if the steps take more than 300 seconds.

## Open points

- *(OPEN)* The location of the release download of the command line (open question O2).
- *(OPEN)* Installing `@tierwise/evaluator` from the npm registry. The package is private until the first release and the `@tierwise` scope is not claimed (open question O2).
