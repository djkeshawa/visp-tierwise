export const meta = {
  name: 'tierwise-work-items',
  description: 'Haiku 5.5 (high) drafts and cross-verifies Tierwise work items; Sonnet 5.5 (high) reviews each item; the main chat model does the final review',
  whenToUse: 'Run one wave of Tierwise roadmap work items, where each item owns distinct files. Pass { root, wave, context, items: [{ id, title, files, sources, brief }], verifiers? (0-2, default 2), consistency? } as args.',
  phases: [
    { title: 'Draft', detail: 'one Haiku 5.5 agent per work item', model: 'haiku' },
    { title: 'Verify', detail: 'two Haiku 5.5 verifiers per draft: faithfulness and precision', model: 'haiku' },
    { title: 'Item review', detail: 'Sonnet 5.5 approves, fixes small issues, or requests revision', model: 'sonnet' },
    { title: 'Revise', detail: 'Haiku 5.5 applies the reviewer notes (max 2 rounds)', model: 'haiku' },
    { title: 'Final review', detail: 'main chat model checks the whole wave together' },
  ],
}

const HAIKU = { model: 'haiku', effort: 'high' }
const SONNET = { model: 'sonnet', effort: 'high' }
const MAX_REVISIONS = 2

const DRAFT = {
  type: 'object',
  properties: {
    files: { type: 'array', items: { type: 'string' } },
    summary: { type: 'string' },
    openPoints: { type: 'array', items: { type: 'string' } },
  },
  required: ['files', 'summary', 'openPoints'],
}

const VERIFY = {
  type: 'object',
  properties: {
    issues: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          severity: { type: 'string', enum: ['blocker', 'major', 'minor'] },
          file: { type: 'string' },
          location: { type: 'string' },
          problem: { type: 'string' },
          suggestion: { type: 'string' },
        },
        required: ['severity', 'file', 'problem'],
      },
    },
  },
  required: ['issues'],
}

const REVIEW = {
  type: 'object',
  properties: {
    verdict: { type: 'string', enum: ['approve', 'revise'] },
    notes: { type: 'array', items: { type: 'string' } },
    directFixes: { type: 'array', items: { type: 'string' } },
    rejectedIssues: { type: 'array', items: { type: 'string' } },
    openPoints: { type: 'array', items: { type: 'string' } },
  },
  required: ['verdict', 'notes', 'directFixes', 'openPoints'],
}

const CONSISTENCY = {
  type: 'object',
  properties: {
    fixed: { type: 'array', items: { type: 'string' } },
    remaining: { type: 'array', items: { type: 'string' } },
    summary: { type: 'string' },
  },
  required: ['fixed', 'remaining', 'summary'],
}

const LENSES = [
  {
    key: 'faithfulness',
    focus: `FAITHFULNESS. Compare the draft against every listed source.
Report: (1) contradictions with an ADR or the requirements; (2) semantics the draft invents that no source supports;
(3) anything the brief requires that is missing or only partly covered; (4) wrong, missing or invented error codes;
(5) rules that belong to another work item and are restated here instead of referenced.`,
  },
  {
    key: 'precision',
    focus: `PRECISION. Read as two independent implementers who must produce identical behaviour.
Report: (1) any statement they could implement differently; (2) undefined or inconsistently used terms;
(3) MUST statements that are not testable or lack a clause ID; (4) missing edge cases (empty, absent, null,
duplicates, ordering, limits, unknown names); (5) examples that contradict the clauses; (6) clause ID problems
(gaps, duplicates, wrong area prefix, compound clauses).`,
  },
]

const list = (xs) => xs.map((x) => `- ${x}`).join('\n')

function itemHeader(item) {
  return `${args.context}

WORK ITEM ${item.id}: ${item.title}
Project root: ${args.root}
Files you own (absolute paths are under the project root):
${list(item.files)}
Sources to read in full before working:
${list(item.sources)}

Brief:
${item.brief}`
}

function draftPrompt(item) {
  return `${itemHeader(item)}

Write the owned files now. Read every source first. Follow spec/AUTHORING.md exactly.
Do not edit any file you do not own. Do not run git commands.
Return the files you wrote, a short summary, and every point you marked (OPEN).`
}

function verifyPrompt(item, draft, lens) {
  return `${itemHeader(item)}

You are an independent verifier. The drafter reports:
${draft.summary}

Read the owned files and the sources in full. This is a READ-ONLY review: do not edit any file.
Your lens: ${lens.focus}

Report only real problems, each with the file, the location (clause ID or heading) and a concrete suggestion.
Severity: blocker = wrong or contradicts a source; major = missing or ambiguous required content; minor = wording or format.
Return an empty list if you find nothing.`
}

function reviewPrompt(item, draft, issues, round) {
  const last = round >= MAX_REVISIONS
  return `${itemHeader(item)}

You are the item reviewer for the Tierwise project. Review round ${round}${last ? ' (FINAL round: no further revisions are possible)' : ''}.
Drafter summary: ${draft.summary}
Drafter open points: ${draft.openPoints.length ? draft.openPoints.join('; ') : '(none)'}
Verifier issues (verifiers can be wrong; judge each one yourself):
${issues.length ? issues.map((i) => `- [${i.severity}] ${i.file} ${i.location || ''}: ${i.problem}${i.suggestion ? ` (suggests: ${i.suggestion})` : ''}`).join('\n') : '- (none this round)'}

Read the owned files and the sources in full, then:
1. Apply small, unambiguous fixes yourself, directly in the owned files: wording, clause IDs, error codes, references, formatting.
2. ${last ? 'Fix any remaining substantive problem yourself if you can do so faithfully to the sources; otherwise list it in notes.' : 'If substantive content is missing or wrong, return verdict "revise" with concrete, actionable notes for the drafter.'}
3. Return verdict "approve" when the work item is correct and complete for spec v0.1.
4. Never decide new semantics silently. Resolve a question only if the ADRs or the requirements clearly imply the answer;
   otherwise keep it marked (OPEN) and list it in openPoints, with your recommended resolution.
Only edit the files this item owns. Do not run git commands.`
}

function revisePrompt(item, review) {
  return `${itemHeader(item)}

The item reviewer requested a revision. Apply every note below to the owned files, keeping everything else intact:
${list(review.notes)}
${review.directFixes.length ? `The reviewer already made these fixes; keep them:\n${list(review.directFixes)}` : ''}

Do not edit any file you do not own. Do not run git commands.
Return the files you changed, a short summary of what you changed, and any remaining (OPEN) points.`
}

async function verifyAndReview(draft, item) {
  if (!draft) return { id: item.id, title: item.title, verdict: 'draft-failed' }
  const lenses = LENSES.slice(0, args.verifiers ?? LENSES.length)
  const verdicts = await parallel(lenses.map((lens) => () =>
    agent(verifyPrompt(item, draft, lens), { ...HAIKU, phase: 'Verify', label: `verify:${item.id}:${lens.key}`, schema: VERIFY })))
  const issues = verdicts.filter(Boolean).flatMap((v) => v.issues)
  log(`${item.id}: ${issues.length} verifier issue(s)`)

  let current = draft
  let pending = issues
  let round = 0
  let review = null
  for (;;) {
    review = await agent(reviewPrompt(item, current, pending, round), { ...SONNET, phase: 'Item review', label: `review:${item.id}#${round}`, schema: REVIEW })
    if (!review || review.verdict === 'approve' || round >= MAX_REVISIONS) break
    round += 1
    const revised = await agent(revisePrompt(item, review), { ...HAIKU, phase: 'Revise', label: `revise:${item.id}#${round}`, schema: DRAFT })
    if (revised) current = revised
    pending = []
  }

  return {
    id: item.id,
    title: item.title,
    files: item.files,
    verdict: review ? review.verdict : 'review-failed',
    revisions: round,
    verifierIssues: issues.length,
    blockers: issues.filter((i) => i.severity === 'blocker').length,
    notes: review ? review.notes : [],
    directFixes: review ? review.directFixes : [],
    openPoints: review ? review.openPoints : current.openPoints,
  }
}

const items = args.items
log(`Wave ${args.wave}: ${items.length} work item(s)`)

const results = await pipeline(
  items,
  (item) => agent(draftPrompt(item), { ...HAIKU, phase: 'Draft', label: `draft:${item.id}`, schema: DRAFT }),
  (draft, item) => verifyAndReview(draft, item),
)

const done = results.filter(Boolean)
const unresolved = done.filter((r) => r.verdict !== 'approve')
if (unresolved.length) log(`Not approved: ${unresolved.map((r) => `${r.id} (${r.verdict})`).join(', ')}`)

let consistency = null
if (args.consistency) {
  phase('Final review')
  consistency = await agent(`${args.context}

You are the final reviewer and lead author of the Tierwise project. Every work item in wave ${args.wave} has been reviewed individually.
Now check them TOGETHER. Project root: ${args.root}
Files in this wave:
${list(done.flatMap((r) => r.files || []))}
Item outcomes:
${done.map((r) => `- ${r.id}: ${r.verdict}; open points: ${(r.openPoints || []).join('; ') || 'none'}`).join('\n')}

${args.consistency}

Fix problems directly in the wave's files. Do not run git commands. Report what you fixed and what remains.`,
    { phase: 'Final review', label: 'final-review', schema: CONSISTENCY })
}

return { wave: args.wave, items: done, missing: items.length - done.length, consistency }
