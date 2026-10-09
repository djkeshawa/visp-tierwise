#!/usr/bin/env python3
"""Validate foundation metadata without installing dependencies or claiming conformance."""

import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
MODULES = {
    "tierwise-core": 17,
    "tierwise-cli": 17,
    "tierwise-spring-boot-starter": 21,
    "tierwise-server": 21,
    "tierwise-openfeature": 17,
}
# ADR 0012. Later phases extend these sets when their dependencies are approved.
APPROVED_DEPENDENCIES = {
    "tierwise-core": {"org.snakeyaml:snakeyaml-engine", "com.fasterxml.jackson.core:jackson-core"},
    "tierwise-cli": {"io.github.tierwise:tierwise-core", "info.picocli:picocli"},
}
APPROVED_TEST_DEPENDENCIES = {"org.junit.jupiter:junit-jupiter", "net.jqwik:jqwik"}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def check_documents():
    required = [
        "LICENSE", "NOTICE", "README.md", "CONTRIBUTING.md", "CODE_OF_CONDUCT.md",
        "SECURITY.md", "GOVERNANCE.md", "MAINTAINERS.md", "CHANGELOG.md", "AGENTS.md",
        "docs/vision.md", "docs/requirements.md", "docs/roadmap.md",
        "docs/implementation-plan.md", "docs/open-questions.md", "mkdocs.yml",
        ".github/PULL_REQUEST_TEMPLATE.md", ".github/ISSUE_TEMPLATE/bug_report.yml",
        ".github/ISSUE_TEMPLATE/feature_request.yml", ".github/dependabot.yml",
    ]
    for name in required:
        require((ROOT / name).is_file(), f"Missing required foundation file: {name}")
    require("Apache License" in (ROOT / "LICENSE").read_text(), "Missing Apache license text")


def check_java():
    parent = ET.parse(ROOT / "java/pom.xml").getroot()
    actual = [item.text for item in parent.findall("m:modules/m:module", NS)]
    require(actual == list(MODULES), "Maven reactor must contain the five declared modules")
    version = parent.findtext("m:version", namespaces=NS)
    for name, baseline in MODULES.items():
        model = ET.parse(ROOT / "java" / name / "pom.xml").getroot()
        require(model.findtext("m:artifactId", namespaces=NS) == name, f"Wrong artifact: {name}")
        require(model.findtext("m:parent/m:version", namespaces=NS) == version, f"Version drift: {name}")
        release = model.findtext("m:properties/m:maven.compiler.release", namespaces=NS)
        require(int(release or "17") == baseline, f"Wrong Java baseline: {name}")
        for dependency in model.findall("m:dependencies/m:dependency", NS):
            coordinate = ":".join(dependency.findtext(f"m:{part}", namespaces=NS) for part in ("groupId", "artifactId"))
            require(coordinate in APPROVED_DEPENDENCIES.get(name, set()) | APPROVED_TEST_DEPENDENCIES,
                    f"Dependency not approved in ADR 0012 for {name}: {coordinate}")
    require(not parent.findall("m:dependencies/m:dependency", NS), "Declare dependencies in modules, not the parent")
    wrapper = (ROOT / "java/.mvn/wrapper/maven-wrapper.properties").read_text()
    require("distributionSha256Sum=" in wrapper, "The Maven Wrapper must verify its distribution checksum")
    require((ROOT / "java/mvnw").stat().st_mode & 0o111, "java/mvnw must be executable")


def check_ci():
    for workflow in (ROOT / ".github/workflows").glob("*.yml"):
        text = workflow.read_text()
        for ref in re.findall(r"^\s*- uses: ([^\s#]+)", text, re.MULTILINE):
            require(re.fullmatch(r"[^@]+@[0-9a-f]{40}", ref), f"Unpinned action in {workflow.name}: {ref}")
    ci = (ROOT / ".github/workflows/ci.yml").read_text()
    for command in ("./mvnw -B -ntp verify", "pnpm --dir js install --frozen-lockfile",
                    "pnpm --dir js run genericity", "pnpm --dir js run check",
                    "pnpm --dir js run build", "pnpm --dir js test"):
        require(command in ci, f"CI is missing: {command}")


def main():
    metadata = json.loads((ROOT / "project-metadata.json").read_text())
    require(metadata["phase"] >= 0, "Phase must be a number")
    require(metadata["supportedSpecVersions"] == [], "Phase 0 cannot claim spec conformance")
    check_documents()
    check_java()
    check_ci()
    print("Foundation files, Maven reactor metadata and CI action pins verified.")
    print("This check does not compile anything; run ./mvnw verify and pnpm build/test for that.")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, ET.ParseError) as error:
        print(f"Foundation check failed: {error}", file=sys.stderr)
        sys.exit(1)
