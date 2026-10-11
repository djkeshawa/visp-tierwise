# Security policy

## Supported versions

| Line | Specification | Bundle format | Status |
| --- | --- | --- | --- |
| 0.1.x | `specVersion` 0.1 | `bundleFormatVersion` 1 | Supported |

Until 1.0, security fixes go into the latest 0.x minor line only.

## Reporting a vulnerability

Report vulnerabilities privately through GitHub's private vulnerability reporting.
Never report them in a public issue, pull request or discussion.

1. Open the repository's **Security** tab.
2. Select **Report a vulnerability**.
3. Give the affected version, reproduction steps, expected and actual behavior,
   impact and a minimal proof of concept. Remove secrets and personal data.
4. Submit the report and continue the discussion in that private advisory.

See [GitHub's private reporting guide](https://docs.github.com/en/code-security/how-tos/report-and-fix-vulnerabilities/report-privately).
If the button is missing, ask [@djkeshawa](https://github.com/djkeshawa) for a
private channel without including any vulnerability details.

## Response and disclosure

The security maintainer listed in [MAINTAINERS.md](MAINTAINERS.md) aims to
acknowledge a report within 7 days and to agree a disclosure timeline with the
reporter, normally no more than 90 days. Fixes are published with a GitHub security
advisory that names the affected and fixed versions. Tierwise is maintained on a
best-effort basis and makes no service-level commitment.

## Scope notes

Browser evaluation must never enforce access or other restrictions. Client-side
inputs and outputs can be altered, so applications must enforce decisions on the
server.
