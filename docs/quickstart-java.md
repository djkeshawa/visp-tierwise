# Quickstart: Java

This page takes you from nothing to a Java program that reads a Tierwise bundle and resolves a key, in about
five minutes. You install the command line, write a small project from a preset, change one value, validate it,
compile it to a bundle, and load the bundle from Java.

The names in this page (`quota.max_items`, `group_a`, …) come from the template that `tierwise init` writes.
They are examples. Tierwise itself knows no such names; you declare your own levels, keys and decisions.

The JavaScript version of this page is the [JavaScript quickstart](quickstart-js.md).

<!-- This page is executed by scripts/quickstart-check.sh. The HTML comments before the fenced blocks tell the
     script what to do with each block. They do not show in the rendered page. See "How this page is verified". -->

## Before you start

- Java 17 or later. The command line and `tierwise-core` run on Java 17. The Spring Boot starter and the server
  need Java 21.
- Maven 3.9 or later, for the example program. Any build tool that resolves Maven coordinates works.

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
`TIERWISE_RELEASE_URL` and does not name a host. The Docker image and the GitHub Action are the other two ways to
run the same command line; see the [CLI reference](guides/cli-reference.md).

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

The same project always compiles to the same bytes and the same digest. If you made the same edit, you get the
same digest.

## 5. Load the bundle from Java

Add `tierwise-core` to a Maven project. In a new directory `app`, create `pom.xml`:

<!-- qs:run -->
```sh
mkdir app
```

<!-- qs:file app/pom.xml -->
```xml
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>example</groupId>
  <artifactId>tierwise-quickstart</artifactId>
  <version>1.0</version>
  <dependencies>
    <dependency>
      <groupId>io.github.tierwise</groupId>
      <artifactId>tierwise-core</artifactId>
      <version>0.1.0</version>
    </dependency>
  </dependencies>
</project>
```

With Gradle, the dependency is `implementation("io.github.tierwise:tierwise-core:0.1.0")`.

Create `app/Quickstart.java`. It loads the bundle, which checks the digest and the versions, creates an
evaluator, and resolves `quota.max_items` for each group:

<!-- qs:file app/Quickstart.java -->
```java
import io.github.tierwise.core.Context;
import io.github.tierwise.core.Evaluator;
import io.github.tierwise.core.EvaluatorOptions;
import io.github.tierwise.core.Resolved;
import io.github.tierwise.core.Tierwise;
import java.nio.file.Files;
import java.nio.file.Path;

public class Quickstart {
  public static void main(String[] args) throws Exception {
    byte[] bytes = Files.readAllBytes(Path.of(args[0]));
    Evaluator evaluator = Tierwise.evaluator(Tierwise.loadBundle(bytes), EvaluatorOptions.none());

    for (String group : new String[] {"group_a", "group_b"}) {
      Context context = Context.builder().level("group", group).build();
      Resolved resolved = evaluator.resolve(context, "quota.max_items");
      System.out.println(group + " quota.max_items = " + resolved.value());
      for (Resolved.Contribution c : resolved.contributions()) {
        String scope = c.name() + c.node().map(node -> "=" + node).orElse("");
        System.out.println("  " + scope + ": " + c.value().orElseThrow() + " " + c.effect());
      }
    }
  }
}
```

Run it. Maven prints the class path of the dependencies, and `java` runs the source file directly:

<!-- qs:run -->
```sh
cd app
mvn -q dependency:build-classpath -Dmdep.outputFile=classpath.txt
java -cp "$(cat classpath.txt)" Quickstart.java ../bundle.json
cd ..
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
listed with the level and node it came from. `group_b` sets no limit, so the base limit stands. The program did
no parsing of YAML and no merging: it read the bundle and asked a question.

Things to know about the API:

- `Tierwise.loadBundle(byte[])` throws a `TierwiseException` with the code `E_BUNDLE_INTEGRITY` or
  `E_BUNDLE_UNSUPPORTED` when the bytes are not a valid bundle.
- An `Evaluator` is immutable and safe to share between threads. Create it once and keep it.
- `resolve` throws a `TierwiseException` with a stable `code()` for a caller mistake: an unknown key
  (`E_UNKNOWN_KEY`), an unknown level (`E_UNKNOWN_SCOPE`) or an unlisted node (`E_UNKNOWN_NODE`).
- There is no way to read one level's raw value. You always get the resolved value with its provenance.

## 6. Going further: decisions

A decision answers "may this order be submitted?" from facts about the order. The `commerce` sample project in
the repository has two decisions. Compile it and decide from Java. The sample has two levels whose values come
from a service, so the evaluator needs a provider for each one; the providers here return nothing.

<!-- qs:run -->
```sh
java -jar tierwise.jar compile "$TIERWISE_REPO/samples/commerce" -o commerce.json
```

<!-- qs:file app/Decide.java -->
```java
import io.github.tierwise.core.Context;
import io.github.tierwise.core.Decision;
import io.github.tierwise.core.Evaluator;
import io.github.tierwise.core.EvaluatorOptions;
import io.github.tierwise.core.FactSet;
import io.github.tierwise.core.Tierwise;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

public class Decide {
  public static void main(String[] args) throws Exception {
    byte[] bytes = Files.readAllBytes(Path.of(args[0]));
    // Each service-sourced level needs a provider and a timeout.
    EvaluatorOptions options = EvaluatorOptions.builder()
        .provider("account", nodeId -> Map.of(), Duration.ofMillis(200))
        .provider("user", nodeId -> Map.of(), Duration.ofMillis(200))
        .build();
    Evaluator evaluator = Tierwise.evaluator(Tierwise.loadBundle(bytes), options);

    Context context = Context.builder().level("division", "south").build();
    FactSet facts = FactSet.builder()
        .fact("customer.active", true)
        .fact("customer.credit_hold", true)
        .child("item", Map.of("item.stock_type", "STOCKED"))
        .child("item", Map.of("item.stock_type", "DROP_SHIP"))
        .build();

    Decision decision = evaluator.decide(context, "checkout.order_submission", facts);
    System.out.println("effect  = " + decision.effect());
    System.out.println("reason  = " + decision.reason().type());
    System.out.println("binding = " + decision.binding().level() + "/" + decision.binding().node().orElse("-"));
  }
}
```

<!-- qs:run -->
```sh
(cd app && java -cp "$(cat classpath.txt)" Decide.java ../commerce.json)
```

<!-- qs:expect -->
```text
effect  = DENY
reason  = DEFAULT
binding = division/south
```

The customer is on credit hold and the basket mixes a stocked item with a drop-shipped one. Division `south` allows
only some stock types on credit hold, and "every item must be allowed" is not true for this basket, so no rule
matches and the policy default `DENY` applies. The reason says so. `decision.trace()` lists the rules that were
checked. The [modeling guide](guides/modeling.md) explains `all_in` and `any_in`, and why a mixed basket is the
case worth testing.

## Where to go next

- [Modeling guide](guides/modeling.md): levels, axes, keys, strategies, kinds, decisions.
- [CLI reference](guides/cli-reference.md): every command and option.
- [Runtime and operations](guides/runtime-and-operations.md): the Spring Boot starter, refreshing a bundle from a
  server, last-known-good, metrics and providers.
- [Security model](guides/security-model.md): where to enforce a decision and what a bundle digest proves.

## How this page is verified

`scripts/quickstart-check.sh` runs every marked block of this page, in order, in an empty temporary directory with
an empty `HOME`, and compares the output of each block with the block that follows it. A difference fails the
check. It differs from your machine in four ways, and no more:

1. The release download is skipped, because nothing is released yet. The script builds the command line and
   `tierwise-core` from the working tree (`./mvnw install` into a temporary local Maven repository) and sets
   `TIERWISE_REPO` and `TIERWISE_CLI_JAR` for the build-from-source path.
2. Maven reads the temporary repository instead of Maven Central for the Tierwise artifacts, through the
   `MAVEN_ARGS` environment variable. Everything else is downloaded from Maven Central as on your machine.
3. `mvn` is the Maven distribution that the Maven Wrapper (`java/mvnw`) downloads, so that a machine without
   Maven can run the check.
4. A development build has a version such as `0.1.0-dev.0`; the script replaces the text `0.1.0` in this page by
   the built version.

### Measured time

Wall time of the steps of this page, from the first command to the last, after the artifacts are built. The Maven
plugin and its dependencies for `dependency:build-classpath` are downloaded during the run, into an empty local
repository. Building `tierwise-core` and the command line from source, which a release makes unnecessary, took about
30 to 40 seconds and is not included.

| Date | Machine | Runs | Time |
| --- | --- | --- | --- |
| 2026-10-09 | AMD Ryzen 9 5900HS, 30 GB, Linux 7.1.1; OpenJDK 21.0.12.1, Maven 3.9.16 | 3 | 11.2 s median (11.1 to 13.2 s) |

The five-minute promise has to hold on a machine with a cold Maven cache and a network, and it includes the time to
read. The check fails if the steps take more than 300 seconds.

## Open points

- *(OPEN)* The location of the release download of the command line. It depends on the GitHub organisation that the project owner has not claimed (open question O2).
