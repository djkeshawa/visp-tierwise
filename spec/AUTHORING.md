# Spec authoring conventions

These rules apply to everything under `spec/`. They exist so that sections written
separately read as one spec, and so that conformance vectors can cite clauses
precisely.

## Sources of truth while drafting

1. `docs/adr/0005`, `0007`–`0012`: settled decisions. They win over everything else.
2. `docs/requirements.md` §4–§6: the normative draft.
3. The JSON Schemas in `spec/schemas/`: the bundle `content` layout and the vector
   formats.

Never invent semantics. If the sources don't decide something a section needs,
don't guess. Write the clause as `**[AREA-n]** *(OPEN)* <the question>` and list it
in the section's "Open points" list. The reviewer resolves it with an ADR.

## Language

- Use RFC 2119/8174 keywords (**MUST**, **MUST NOT**, **SHOULD**, **MAY**) in
  capitals, and only in normative clauses.
- Write in neutral terms: "an evaluator", "a compiler", "a project". Never say
  "Java" or "JS" in normative text; language-specific notes go in a
  `> Implementation note` block.
- Use the domain names from the samples (`division`, `order`, …) only inside
  examples, and label every example **Example (non-normative)**.
- Prefer short declarative sentences. Define every term once, in `spec.md`
  §Terminology, and reuse it exactly.

## Clause IDs

- Every normative requirement has a stable ID in bold at the start of its
  paragraph or list item: `**[MERGE-4]** An evaluator MUST …`.
- Each section owns one or more area prefixes (see `spec.md`). Number them from 1,
  in reading order, with no gaps.
- One clause states one testable requirement. Split compound requirements.
- IDs are never reused or renumbered once the spec is accepted. Before then,
  renumber freely within your own areas only.
- Refer to clauses in other sections by ID (`see [SCOPE-7]`). Never restate
  another section's rule; reference it.

## Section file shape

```markdown
# N. Title

<one-paragraph summary of what this section covers and what it does not>

## N.1 Subsection
**[AREA-1]** …

## Open points
- (none) | list of *(OPEN)* clause IDs with one line each
```

## Error codes

Use only codes from `docs/requirements.md` §6.1–6.2. A clause that makes something
an error names the code: "…, the compiler MUST report `E_SCOPE_DECLARATION`." If a
needed code doesn't exist, mark the clause *(OPEN)*.

## Files

Each work item owns its listed files. Don't create or edit other files under
`spec/` or elsewhere, and don't commit.
