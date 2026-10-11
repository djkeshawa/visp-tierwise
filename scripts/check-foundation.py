#!/usr/bin/env python3
"""Validate foundation metadata without installing dependencies or claiming conformance."""

import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
ET.register_namespace("", NS["m"])  # so that ET.tostring writes plain element names
MODULES = {
    "tierwise-core": 17,
    "tierwise-cli": 17,
    "tierwise-spring-boot-starter": 21,
    "tierwise-spring-boot-starter-s3": 21,
    "tierwise-server": 21,
    "tierwise-server-s3": 21,
    "tierwise-openfeature": 17,
    # Test-only: the end-to-end test. It is part of the reactor and is never published (see PUBLISHED below).
    "tierwise-e2e": 21,
}
# The modules that go to Maven Central. Every other module of the reactor must set maven.deploy.skip to true,
# which the release profile of java/pom.xml turns into "no sources or Javadoc JAR, left out of the Central bundle".
PUBLISHED = set(MODULES) - {"tierwise-e2e"}
# The Maven plugins of the release profile (ADR 0022). Each is pinned by a property in java/pom.xml.
RELEASE_PLUGINS = {
    "org.apache.maven.plugins:maven-source-plugin": "version.source",
    "org.apache.maven.plugins:maven-javadoc-plugin": "version.javadoc",
    "org.apache.maven.plugins:maven-gpg-plugin": "version.gpg",
    "dev.sigstore:sigstore-maven-plugin": "version.sigstore",
    "org.sonatype.central:central-publishing-maven-plugin": "version.central-publishing",
    "org.cyclonedx:cyclonedx-maven-plugin": None,
}
# ADR 0012. Later phases extend these sets when their dependencies are approved.
APPROVED_DEPENDENCIES = {
    "tierwise-core": {"org.snakeyaml:snakeyaml-engine", "com.fasterxml.jackson.core:jackson-core"},
    # The CLI reads rules files for `tierwise test` with snakeyaml-engine, which core already brings in.
    "tierwise-spring-boot-starter": {"io.github.visplabs:tierwise-core", "org.springframework.boot:*", "io.micrometer:*"},
    "tierwise-spring-boot-starter-s3": {"io.github.visplabs:tierwise-spring-boot-starter", "software.amazon.awssdk:*", "org.springframework.boot:*"},
    "tierwise-server": {"io.github.visplabs:tierwise-core", "org.springframework.boot:*"},
    "tierwise-server-s3": {"io.github.visplabs:tierwise-server", "software.amazon.awssdk:*", "org.springframework.boot:*"},
    "tierwise-openfeature": {"io.github.visplabs:tierwise-core", "dev.openfeature:sdk"},
    "tierwise-cli": {"io.github.visplabs:tierwise-core", "info.picocli:picocli", "org.snakeyaml:snakeyaml-engine"},
    # The end-to-end test runs the server and the starter in one JVM, all in test scope.
    "tierwise-e2e": {"io.github.visplabs:tierwise-core", "io.github.visplabs:tierwise-server",
                     "io.github.visplabs:tierwise-spring-boot-starter", "org.springframework.boot:*"},
}
APPROVED_TEST_DEPENDENCIES = {"org.junit.jupiter:junit-jupiter", "net.jqwik:jqwik", "org.springframework.security:spring-security-test"}
# BOMs the parent may import in dependencyManagement. They only pin versions; a module still has to
# declare (and have approved) every dependency it uses.
APPROVED_IMPORTS = {"org.junit:junit-bom"}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def check_documents():
    required = [
        "LICENSE", "NOTICE", "README.md", "CONTRIBUTING.md", "CODE_OF_CONDUCT.md",
        "SECURITY.md", "GOVERNANCE.md", "MAINTAINERS.md", "CHANGELOG.md", "AGENTS.md",
        "docs/vision.md", "docs/requirements.md", "docs/roadmap.md",
        "docs/open-questions.md", "mkdocs.yml",
        ".github/PULL_REQUEST_TEMPLATE.md", ".github/ISSUE_TEMPLATE/bug_report.yml",
        ".github/ISSUE_TEMPLATE/feature_request.yml", ".github/dependabot.yml",
    ]
    for name in required:
        require((ROOT / name).is_file(), f"Missing required foundation file: {name}")
    require("Apache License" in (ROOT / "LICENSE").read_text(), "Missing Apache license text")


def approved_in(coordinate, patterns):
    """True when a coordinate matches an approved entry; "group:*" approves a whole group."""
    group = coordinate.split(":")[0]
    return coordinate in patterns or f"{group}:*" in patterns


def coordinate_of(dependency):
    return ":".join(dependency.findtext(f"m:{part}", namespaces=NS) for part in ("groupId", "artifactId"))


def check_managed_dependencies(parent):
    """The parent may pin approved versions and import approved BOMs, and nothing else."""
    approved = set().union(*APPROVED_DEPENDENCIES.values()) | APPROVED_TEST_DEPENDENCIES
    for dependency in parent.findall("m:dependencyManagement/m:dependencies/m:dependency", NS):
        coordinate = coordinate_of(dependency)
        require(dependency.findtext("m:version", namespaces=NS), f"Managed dependency has no version: {coordinate}")
        if dependency.findtext("m:scope", namespaces=NS) == "import":
            require(dependency.findtext("m:type", namespaces=NS) == "pom" and coordinate in APPROVED_IMPORTS,
                    f"Import not approved in ADR 0012: {coordinate}")
        else:
            require(approved_in(coordinate, approved), f"Managed dependency not approved in ADR 0012: {coordinate}")


def check_java():
    parent = ET.parse(ROOT / "java/pom.xml").getroot()
    actual = [item.text for item in parent.findall("m:modules/m:module", NS)]
    require(actual == list(MODULES), "Maven reactor modules must match MODULES in this script")
    version = parent.findtext("m:version", namespaces=NS)
    for name, baseline in MODULES.items():
        model = ET.parse(ROOT / "java" / name / "pom.xml").getroot()
        require(model.findtext("m:artifactId", namespaces=NS) == name, f"Wrong artifact: {name}")
        require(model.findtext("m:parent/m:version", namespaces=NS) == version, f"Version drift: {name}")
        release = model.findtext("m:properties/m:maven.compiler.release", namespaces=NS)
        require(int(release or "17") == baseline, f"Wrong Java baseline: {name}")
        for dependency in model.findall("m:dependencies/m:dependency", NS):
            coordinate = coordinate_of(dependency)
            require(approved_in(coordinate, APPROVED_DEPENDENCIES.get(name, set()) | APPROVED_TEST_DEPENDENCIES),
                    f"Dependency not approved in ADR 0012 for {name}: {coordinate}")
    require(not parent.findall("m:dependencies/m:dependency", NS), "Declare dependencies in modules, not the parent")
    check_managed_dependencies(parent)
    wrapper = (ROOT / "java/.mvn/wrapper/maven-wrapper.properties").read_text()
    require("distributionSha256Sum=" in wrapper, "The Maven Wrapper must verify its distribution checksum")
    require((ROOT / "java/mvnw").stat().st_mode & 0o111, "java/mvnw must be executable")


def check_ci():
    for workflow in [*(ROOT / ".github/workflows").glob("*.yml"), ROOT / "action.yml"]:
        text = workflow.read_text()
        for ref in re.findall(r"^\s*(?:- )?uses: ([^\s#]+)", text, re.MULTILINE):
            # A local action (./) runs the code of the checked-out commit, so the checkout pins it.
            require(ref.startswith("./") or re.fullmatch(r"[^@]+@[0-9a-f]{40}", ref),
                    f"Unpinned action in {workflow.name}: {ref}")
    ci = (ROOT / ".github/workflows/ci.yml").read_text()
    for command in ("./mvnw -B -ntp verify -Pconformance",
                    './mvnw -B -ntp -pl tierwise-core,tierwise-cli -am verify -Pconformance -Djvm="$JAVA_HOME_17_X64/bin/java"',
                    "pnpm --dir js install --frozen-lockfile",
                    "pnpm --dir js run genericity", "pnpm --dir js run check",
                    "pnpm --dir js run build", "pnpm --dir js test"):
        require(command in ci, f"CI is missing: {command}")


def pom_properties(model):
    properties = model.find("m:properties", NS)
    return {} if properties is None else {child.tag.split("}")[1]: (child.text or "") for child in properties}


def check_release_poms():
    """The release profile of java/pom.xml and the modules that must not be published (ADR 0022)."""
    parent = ET.parse(ROOT / "java/pom.xml").getroot()
    properties = pom_properties(parent)
    for tag in ("name", "description", "url"):
        require(parent.findtext(f"m:{tag}", namespaces=NS), f"java/pom.xml needs <{tag}> for Maven Central")
    require(parent.findall("m:licenses/m:license", NS), "java/pom.xml needs a license")
    for tag in ("url", "connection", "developerConnection"):
        require(parent.findtext(f"m:scm/m:{tag}", namespaces=NS), f"java/pom.xml needs <scm><{tag}>")
    # Every plugin that is configured anywhere has a version; the release plugins are the ones of ADR 0022.
    pinned = {"version.source": "3.4.0", "version.javadoc": "3.12.0", "version.gpg": "3.2.8",
              "version.sigstore": "2.3.0", "version.central-publishing": "0.11.0"}
    for name, version in pinned.items():
        require(properties.get(name) == version, f"java/pom.xml: {name} must be {version} (ADR 0022)")
    managed = {coordinate_of(plugin): plugin.findtext("m:version", namespaces=NS)
               for plugin in parent.findall("m:build/m:pluginManagement/m:plugins/m:plugin", NS)}
    for coordinate, version in managed.items():
        require(version, f"Plugin without a version in pluginManagement: {coordinate}")
    for coordinate, prop in RELEASE_PLUGINS.items():
        require(coordinate in managed, f"Release plugin missing from pluginManagement: {coordinate}")
        if prop:
            require(managed[coordinate] == "${" + prop + "}", f"{coordinate} must use ${{{prop}}}")
    require(managed.get("org.springframework.boot:spring-boot-maven-plugin") == "${version.spring-boot}",
            "spring-boot-maven-plugin must use the Spring Boot version")
    # Only the release profile may bind publishing, signing or SBOM plugins for every module.
    always = {coordinate_of(plugin) for plugin in parent.findall("m:build/m:plugins/m:plugin", NS)}
    for coordinate in ("org.apache.maven.plugins:maven-gpg-plugin", "dev.sigstore:sigstore-maven-plugin",
                       "org.sonatype.central:central-publishing-maven-plugin"):
        require(coordinate not in always, f"{coordinate} may run only in the release profile")
    profiles = {profile.findtext("m:id", namespaces=NS): profile for profile in parent.findall("m:profiles/m:profile", NS)}
    require("release" in profiles, "java/pom.xml needs a release profile")
    release = ET.tostring(profiles["release"], encoding="unicode")
    require("<autoPublish>false</autoPublish>" in release, "The release profile must set autoPublish=false")
    require("<extensions>true</extensions>" in release, "central-publishing-maven-plugin needs <extensions>true</extensions>")
    for name in ("tierwise.gpg.skip", "tierwise.sigstore.skip"):
        require(properties.get(name) == "true", f"{name} must default to true: signing needs an explicit key")
    require(properties.get("maven.deploy.skip") == "false", "java/pom.xml must define maven.deploy.skip=false")
    for coordinate in RELEASE_PLUGINS:
        require(f"<artifactId>{coordinate.split(':')[1]}</artifactId>" in release,
                f"The release profile does not use {coordinate}")
    # Test-only modules publish nothing; published modules do not set the switch.
    for name in MODULES:
        model = ET.parse(ROOT / "java" / name / "pom.xml").getroot()
        skip = pom_properties(model).get("maven.deploy.skip")
        if name in PUBLISHED:
            require(skip != "true", f"{name} is published and must not set maven.deploy.skip")
            require(model.findtext("m:name", namespaces=NS) and model.findtext("m:description", namespaces=NS),
                    f"{name} needs a <name> and a <description> for Maven Central")
        else:
            require(skip == "true", f"{name} is not published and must set maven.deploy.skip to true")
    server = ET.parse(ROOT / "java/tierwise-server/pom.xml").getroot()
    repackage = [goal.text for goal in server.findall(
        "m:build/m:plugins/m:plugin/m:executions/m:execution/m:goals/m:goal", NS)]
    require("repackage" in repackage, "tierwise-server must repackage with the spring-boot-maven-plugin")


def split_jobs(text):
    """Returns {job id: text of the job} for a workflow file, without a YAML parser."""
    head, _, body = text.partition("\njobs:\n")
    require(body, "Workflow has no jobs")
    parts = re.split(r"^  ([a-z][a-z0-9-]*):\s*$", body, flags=re.MULTILINE)
    return dict(zip(parts[1::2], parts[2::2]))


def strip_comments(text):
    return "\n".join(line for line in text.splitlines() if not line.lstrip().startswith("#"))


def check_release_workflow():
    """release.yml: a dry run by default, and every publishing step guarded (ADR 0022, requirements 1.2)."""
    text = (ROOT / ".github/workflows/release.yml").read_text()
    code = strip_comments(text)
    require("workflow_dispatch:" in code and "\n  push:" not in code and "schedule:" not in code,
            "release.yml must run only on workflow_dispatch")
    require(re.search(r"^      version:\n(?:        .*\n)*?        required: true", code, re.MULTILINE),
            "release.yml needs a required version input")
    require(re.search(r"^      dry_run:\n(?:        .*\n)*?        default: true", code, re.MULTILINE),
            "release.yml: the dry_run input must default to true")
    require(re.search(r"^permissions:\n  contents: read$", code, re.MULTILINE),
            "release.yml must default to read-only permissions")
    publishing = re.compile(r"mvnw[^\n]*\sdeploy\s|npm publish|gh release create|docker push|central-publishing")
    published_jobs = 0
    for job, body in split_jobs(code).items():
        steps = re.split(r"\n      - ", body)
        has_publishing = any(publishing.search(step) for step in steps)
        if has_publishing:
            published_jobs += 1
            require("if: needs.prepare.outputs.publish == 'true'" in body, f"release.yml: job {job} publishes without the publish guard")
            require("environment: release" in body, f"release.yml: job {job} must run in the release environment")
            for step in steps:
                if publishing.search(step):
                    require("env.DRY_RUN == 'false'" in step, f"release.yml: a publishing step of {job} lacks the DRY_RUN guard")
                    if "npm publish" in step or "mvnw" in step:
                        require("env.HAVE_" in step, f"release.yml: a publishing step of {job} does not check its secrets")
        else:
            require("secrets." not in body, f"release.yml: job {job} does not publish and must not use secrets")
            require("id-token: write" not in body and "contents: write" not in body,
                    f"release.yml: job {job} does not publish and must stay read-only")
    require(published_jobs == 3, "release.yml: expected exactly three publishing jobs (Central, npm, GitHub release)")
    require("npm publish" in code and "--provenance" in code, "release.yml must publish npm packages with --provenance")
    require("-Dtierwise.gpg.skip=false" in code, "release.yml must turn signing on explicitly in the publishing job")


def check_release_files():
    for name in ("scripts/reproducibility-check.sh", "scripts/set-version.sh"):
        path = ROOT / name
        require(path.is_file() and path.stat().st_mode & 0o111, f"{name} must exist and be executable")
    require((ROOT / "docs/release-process.md").is_file(), "Missing docs/release-process.md")
    text = (ROOT / "docs/release-process.md").read_text()
    for question in ("O2", "O3", "O4"):
        require(question in text, f"docs/release-process.md must reference open question {question}")
    # The server image uses the same base images, pinned by the same digests, as the CLI image, and a numeric non-root user.
    cli = (ROOT / "docker/Dockerfile").read_text()
    server = (ROOT / "docker/server.Dockerfile").read_text()
    digests = lambda text: set(re.findall(r"^FROM (\S+@sha256:[0-9a-f]{64})", text, re.MULTILINE))
    require(digests(server) and digests(server) == digests(cli),
            "docker/server.Dockerfile must pin the same base images by digest as docker/Dockerfile")
    require(re.search(r"^USER 10001:10001$", server, re.MULTILINE), "docker/server.Dockerfile must run as USER 10001:10001")


def main():
    metadata = json.loads((ROOT / "project-metadata.json").read_text())
    require(metadata["phase"] >= 0, "Phase must be a number")
    if metadata["phase"] == 0:
        require(metadata["supportedSpecVersions"] == [], "Phase 0 cannot claim spec conformance")
    check_documents()
    check_java()
    check_release_poms()
    check_ci()
    check_release_workflow()
    check_release_files()
    print("Foundation files, Maven reactor metadata, release profile, CI action pins and the release workflow verified.")
    print("This check does not compile anything; run ./mvnw verify and pnpm build/test for that.")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, ET.ParseError) as error:
        print(f"Foundation check failed: {error}", file=sys.stderr)
        sys.exit(1)
