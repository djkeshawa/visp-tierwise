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
        require(not model.findall("m:dependencies/m:dependency", NS), f"Unapproved dependency: {name}")
    require(not parent.findall("m:dependencies/m:dependency", NS), "Unapproved parent dependency")


def check_ci():
    for workflow in (ROOT / ".github/workflows").glob("*.yml"):
        text = workflow.read_text()
        for ref in re.findall(r"^\s*- uses: ([^\s#]+)", text, re.MULTILINE):
            require(re.fullmatch(r"[^@]+@[0-9a-f]{40}", ref), f"Unpinned action in {workflow.name}: {ref}")
    ci = (ROOT / ".github/workflows/ci.yml").read_text()
    require("mvn -B -ntp -f java/pom.xml validate" in ci, "Missing Maven descriptor CI")
    require("pnpm --dir js run check" in ci, "Missing JS workspace CI")


def main():
    metadata = json.loads((ROOT / "project-metadata.json").read_text())
    require(metadata["phase"] == 0, "Update this foundation check after the next reviewed phase")
    require(metadata["supportedSpecVersions"] == [], "Phase 0 cannot claim spec conformance")
    check_documents()
    check_java()
    check_ci()
    print("Foundation files, Maven reactor metadata and CI action pins verified.")
    print("This check does not compile Java/TypeScript or verify runtime semantics or hosted CI.")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, ET.ParseError) as error:
        print(f"Foundation check failed: {error}", file=sys.stderr)
        sys.exit(1)
