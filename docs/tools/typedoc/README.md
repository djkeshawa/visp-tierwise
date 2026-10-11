# TypeDoc project

Builds the TypeScript API reference of `@visplabs/evaluator`, `@visplabs/node` and `@visplabs/openfeature`. It is a
separate project, outside the `js/` workspace, because TypeDoc needs the TypeScript compiler API, which the native
TypeScript 7 does not provide (ADR 0022). The packages themselves still build with TypeScript 7. This project pins
`typedoc` 0.28.20 and `typescript` 6.0.3, and has its own lockfile.

It is private and publishes nothing.

```sh
pnpm --dir js install --frozen-lockfile && pnpm --dir js run build   # TypeDoc reads the package.json files and declarations
pnpm --dir docs/tools/typedoc install --frozen-lockfile
pnpm --dir docs/tools/typedoc run build                              # writes docs/tools/typedoc/dist
```

`.github/workflows/docs.yml` runs the same commands and copies `dist` to `api/js` in the documentation artifact.
The configuration is `typedoc.json`; `treatWarningsAsErrors` makes a missing declaration or an invalid link fail the
build. This directory is excluded from the MkDocs site (`exclude_docs` in `mkdocs.yml`).
