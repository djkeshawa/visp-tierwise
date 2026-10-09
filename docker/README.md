# Tierwise CLI Docker image

The image runs the Tierwise command line (`tierwise`) from the fat JAR of `java/tierwise-cli`. It has a JRE 21
base, a fixed non-root user (UID 10001) and `tierwise` as its entrypoint, so the arguments you pass are the
CLI's arguments:

```sh
docker run --rm tierwise:local --help
```

The image is not published. The release dry run builds it without pushing (`.github/workflows/release-dry-run.yml`).

## Build

Run from the repository root. The build needs `java/`, `presets/` and `spec/data/tz-names.txt`, because
`tierwise-core` reads those files at build time:

```sh
docker build --file docker/Dockerfile --tag tierwise:local .
```

The first stage runs the Maven Wrapper (`java/mvnw`) on a JDK 21 image. It builds `tierwise-cli` and its
dependencies with `clean package -DskipTests`; tests run in CI. The second stage copies only the fat JAR
(`tierwise-cli-<version>-all.jar`) onto the JRE 21 image.

## Use

Mount the project into `/work`, which is the working directory of the image. Give the project path relative to
that directory:

```sh
# Validate the sample project in the current directory.
docker run --rm --user "$(id -u):$(id -g)" --volume "$PWD:/work" tierwise:local validate samples/commerce

# Compile it to a bundle file in the project directory.
docker run --rm --user "$(id -u):$(id -g)" --volume "$PWD:/work" tierwise:local \
  compile samples/commerce --output samples/commerce/bundle.json
```

`--user "$(id -u):$(id -g)"` runs the process as your user, so files the CLI writes into the mounted directory
belong to you. Without it, the process runs as UID 10001, and it can write to the mount only if the host
directory allows that UID. The GitHub Action in the repository root passes the same option.

## Base images

Both `FROM` lines name a tag and a digest. Docker uses the digest, so the build cannot pick up a moved tag.

| Stage | Image reference | Digest (manifest list, read from the registry on 2026-10-09) |
| --- | --- | --- |
| build | `eclipse-temurin:21-jdk-noble` | `sha256:b468c3fc688b14450571494f588bd939378e7fd542ed5a73f8efc13f17872a87` |
| runtime | `eclipse-temurin:21-jre-noble` | `sha256:000fd431958bc81a24abe1e8e5f0f0fd3ae365a594bd50aadb20696805f9408c` |

To update a digest, read the new one from the registry, check the release notes, and edit both `FROM` lines
together:

```sh
docker buildx imagetools inspect eclipse-temurin:21-jre-noble
```

The Ubuntu `noble` variant is used because it provides `groupadd` and `useradd` for the fixed user.

## Notes

- The image contains only the JVM and the fat JAR. It is not a native binary; the native build is in the
  `native` Maven profile of `tierwise-cli`.
- The entrypoint is a small shell launcher at `/usr/local/bin/tierwise` that runs
  `java -jar /opt/tierwise/tierwise.jar`.
- The default command is `--help`.
