# Security policy

## Supported versions

No version has been released, so no version is supported yet. Tierwise 0.1.0 is the first planned release, and the 0.1.x line is the one this policy will cover once 0.1.0 is published. The packages are private (`@tierwise/*`, `private: true`) and the Maven artifacts are not yet published, so there is no published artifact to patch.

| Line | Specification | Bundle format | Status |
| --- | --- | --- | --- |
| 0.1.x | `specVersion` 0.1 | `bundleFormatVersion` 1 | Not released. Release steps are pending owner approval ([release checklist](docs/release-0.1.0-checklist.md)). |

*(OPEN)* The support window, the fixes policy for older 0.1.x releases and the response targets are owner decisions that are not yet approved. This section will be updated when they are.

## Private vulnerability reporting

**Not configured yet.** A GitHub home and security maintainers have not been assigned. No reporting address is claimed by this repository.

Before publishing the repository, its administrator must enable GitHub private vulnerability reporting, assign responders in `MAINTAINERS.md`, and verify they receive notifications. See [GitHub's configuration instructions](https://docs.github.com/en/code-security/how-tos/report-and-fix-vulnerabilities/configure-vulnerability-reporting/configure-for-a-repository).

Once enabled, report privately:

1. Open this project's GitHub repository.
2. Select **Security and quality**, then **Report a vulnerability** on the security/advisories page.
3. Supply the affected version, reproduction steps, expected and actual behavior, impact and a minimal proof of concept. Remove secrets and personal data.
4. Select **Submit report** and continue the discussion in that private advisory.

This flow depends on the repository setting; creating this file does not enable it. See [GitHub's private reporting guide](https://docs.github.com/en/code-security/how-tos/report-and-fix-vulnerabilities/report-privately).

If the button is missing, ask for a private security contact without including vulnerability details. Until a private channel is verified, retain sensitive details rather than putting them in an issue, pull request or discussion.

## Response and disclosure

Assigned security maintainers will triage reports privately, coordinate fixes and disclosure with the reporter, and publish affected and fixed versions when an advisory is ready. Response targets and the support window remain Phase 0 review questions; this foundation makes no service-level commitment.

Browser evaluation must never enforce access or other restrictions. Applications must enforce decisions on the server.
