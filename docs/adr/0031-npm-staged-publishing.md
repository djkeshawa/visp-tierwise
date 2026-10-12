# ADR 0031: npm packages are staged by CI and published by a maintainer with 2FA

- Status: accepted (owner request: "it requires 2FA approval"; delegated decision on the mechanism)
- Date: 2026-10-12

## Context

Release run 38175857276 reached `npm publish` with provenance and npm refused it:

```text
npm error code EOTP
npm error This operation requires a one-time password from your authenticator.
```

The `NPM_TOKEN` of the `release` environment is a granular token without "Bypass two-factor authentication", so npm
asks for a one-time password that a workflow cannot give. Nothing was published.

npm's documentation (docs.npmjs.com, "npm stage", CLI v11) describes staged publishing for this case: "Staged
publishing allows users to have their automated workflows stage a package without a 2FA prompt, deferring the act of
2FA." `npm stage publish` takes the same options as `npm publish`, including `--provenance`, works with a granular token
without bypass, and "will allow you to create a new package if it does not exist". A maintainer publishes a staged
version with `npm stage approve <stage-id>`, which asks for the one-time password. `npm stage` first shipped in npm
11.15.0 (checked in the published tarballs: 11.13.0 has no `lib/commands/stage`, 11.15.0 has it).

## Decision

- The `publish-npm` job stages the three packages with `npm stage publish --provenance --access public`, in dependency
  order, and writes the `npm stage approve` command of each one to the run summary.
- The job installs npm 11.21.0 (released 2026-09-30) after it has rebuilt and compared the tarballs, because the Node 22
  runner ships an npm without `npm stage`. The Node version of the build does not change.
- `NPM_TOKEN` is a granular token, "Read and write (stage only)", on `@visporg`, without bypass. A maintainer publishes
  each package with `npm stage approve` and 2FA: evaluator first, then node and openfeature.
- `scripts/check-foundation.py` requires `npm stage publish --provenance` and rejects a plain `npm publish` in the
  workflow.

## Alternatives considered

- **Staged publishing (chosen).** The workflow keeps provenance and builds exactly the tested tarballs, and every
  publish still needs a person with 2FA, which is what the owner asked for.
- **A token with "Bypass two-factor authentication".** It would work today, but a leaked token could publish without
  2FA, and npm recommends against it.
- **Trusted publishing (OIDC) with `npm publish`.** No token at all, but npm configures it in the settings of an
  existing package, so it cannot do the first release. It also publishes without a person's 2FA.
- **Publish from the maintainer's machine with `--otp`.** Loses the GitHub Actions provenance and the comparison with
  the tested build.

## Consequences

- A release ends with three `npm stage approve` commands run by a maintainer. The draft GitHub release does not wait
  for them.
- After 0.1.0, replace the token with a trusted publisher on each package that allows only `npm stage publish`, as
  npm's trusted publishing page suggests, then set Publishing access to "Require two-factor authentication and disallow
  tokens".
- npm 11.21.0 is a pinned tool of the release workflow, like pnpm 11.3.0. It is not a dependency of any package.
