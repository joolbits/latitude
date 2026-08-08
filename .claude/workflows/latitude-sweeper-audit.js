// "Sweeper" adversarial audit — standing/reusable practice (established 2026-07-05 per Peetsa's
// instruction after the live 18N classifyBase-fallthrough bug; see LESSONS L14 in the main worktree
// and docs/binder/biome-consumer-sweeper-fixes-20260705.md for the audit this template was cut from).
//
// WHAT THIS IS: fan out N independent finder agents (each given a different adversarial LENS over the
// same scope), then independently verify every raw finding with a skeptical agent that defaults to
// REFUTING unless it can directly confirm the defect by reading the real code. Only CONFIRMED/PLAUSIBLE
// findings survive. This exists because L14 showed that logical/aggregate reasoning about "the flag is
// off so it's fine" or "the test passed" is not enough — a silent fallthrough can pass every existing
// check and still be a live bug.
//
// HOW TO REUSE FOR A NEW SWEEP:
//   CONFIRMED 2026-07-05: Workflow({ name: 'latitude-sweeper-audit', args }) does NOT resolve this
//   repo-local file — the tool reported only the built-ins ("Available: deep-research, code-review")
//   even with this file committed at .claude/workflows/latitude-sweeper-audit.js. Do not spend time
//   retrying name+args resolution; it is not wired up in this environment as of this date. This is
//   consistent with an EARLIER, separate args failure this same day (an args-array field threw
//   "undefined is not an object (evaluating 'lenses.length')", 0 agents ran) for a bug never
//   root-caused — two independent signals pointing the same direction.
//   ALWAYS DO THIS INSTEAD: copy this whole file's body into a `Workflow({ script: ... })` call,
//   replacing the `const a = args || {}` block (and the lines derived from it) with literal
//   repoRoot/scopeLabel/scopeBrief/lenses values for your new sweep — exactly the pattern
//   latitude-sweeper-audit-biome-consumer-wf_d0c82c96-3d7.js and
//   latitude-sweeper-audit-phases-0-3-wf_9c9dbfa8-fec.js both used successfully. This file stays as
//   the canonical, indexed TEMPLATE (lens design, schemas, find->verify->synthesize shape) even though
//   it cannot be invoked by name — keep it in sync with whichever inline copy you last refined.
//
// SCOPING A GOOD SWEEP: scopeBrief should name exact files/methods in scope and, if there's a known bug,
// describe its precise mechanism so lenses can look for the SAME failure class elsewhere (not just the
// one instance). lenses should be genuinely different angles (exhaustiveness-of-a-cascade,
// test-validity, a specific recurring risk pattern in this codebase, flag-gating correctness,
// docs-vs-code contradiction, freeform adversarial-scenario construction) — 5-6 lenses has been the
// right size so far; more dilutes each agent's read time, fewer misses angles.
//
// MODEL: always Opus, high effort, for BOTH find and verify agents — this is exactly the kind of
// judgment-heavy, must-not-miss-or-hallucinate work that warrants top-tier model+effort (see
// model-effort-strategy-20260702.md). Do not downgrade to save cost; a missed live-worldgen bug costs
// far more than the extra tokens.

export const meta = {
  name: 'latitude-sweeper-audit',
  description: 'Adversarial "sweeper" audit: N-lens find + independent skeptical verify + severity-ranked synthesis',
  phases: [
    { title: 'Find', detail: 'multi-lens adversarial bug/hole hunt', model: 'opus' },
    { title: 'Verify', detail: 'independent skeptic pass per finding, refute-by-default', model: 'opus' },
    { title: 'Synthesize', detail: 'fix-ready report, ranked by severity' },
  ],
}

const a = args || {}
const repoRoot = a.repoRoot || '<checkout>'
const scopeLabel = a.scopeLabel || 'unspecified scope'
const scopeBrief = a.scopeBrief || ''
const lenses = Array.isArray(a.lenses) ? a.lenses : []

if (!scopeBrief || lenses.length === 0) {
  throw new Error(
    'latitude-sweeper-audit requires args.scopeBrief (string) and args.lenses (non-empty array of ' +
    '{key, prompt}). If args did not arrive correctly, copy this file and hardcode the literals instead ' +
    '(see the file header comment).'
  )
}

const FINDING_SCHEMA = {
  type: 'object', additionalProperties: false,
  required: ['findings'],
  properties: {
    findings: {
      type: 'array',
      items: {
        type: 'object', additionalProperties: false,
        required: ['title', 'file', 'lineHint', 'severity', 'description', 'failureScenario', 'confidence'],
        properties: {
          title: { type: 'string' },
          file: { type: 'string', description: 'repo-relative path' },
          lineHint: { type: 'string', description: 'line number or nearest anchor (method/field name) if exact line unknown' },
          severity: { type: 'string', enum: ['critical', 'high', 'medium', 'low'] },
          description: { type: 'string', description: 'what is wrong, concretely' },
          failureScenario: { type: 'string', description: 'concrete inputs/state -> wrong output, as specific as possible' },
          confidence: { type: 'string', enum: ['high', 'medium', 'low'] },
        },
      },
    },
  },
}

const VERDICT_SCHEMA = {
  type: 'object', additionalProperties: false,
  required: ['verdict', 'reasoning'],
  properties: {
    verdict: { type: 'string', enum: ['CONFIRMED', 'PLAUSIBLE', 'REFUTED'] },
    reasoning: { type: 'string' },
    correctedDescription: { type: 'string', description: 'if CONFIRMED/PLAUSIBLE, a corrected/sharpened description; empty if REFUTED' },
  },
}

phase('Find')
log(`sweeper scope: ${scopeLabel} (${lenses.length} lenses)`)
const findingSets = await parallel(lenses.map((lens) => () =>
  agent(
    `You are an adversarial code auditor ("sweeper") for the Latitude Minecraft mod, a from-scratch ` +
    `worldgen overhaul. Repo root: ${repoRoot}. SCOPE FOR THIS SWEEP: ${scopeBrief}\n\n` +
    `YOUR LENS: ${lens.prompt}\n\n` +
    `Read the actual current code (not memory, not docs alone -- verify against the real files). Find ` +
    `concrete bugs, holes, blind spots, ambiguities, or contradictions (between docs and code, between ` +
    `two code paths, between a design doc's claim and what the code actually does). For EACH finding, be ` +
    `concrete: exact file, a line/anchor, a specific failure scenario (concrete inputs -> wrong output), ` +
    `and your own confidence. Do NOT report vague code-smell opinions or style nits -- only things that are ` +
    `actually wrong or actually ambiguous/contradictory enough to cause a real mistake. It is fine to report ` +
    `zero findings if you genuinely find nothing after a real look. Default to skepticism about your own ` +
    `certainty -- mark confidence 'low' rather than overclaiming.`,
    { label: `find:${lens.key}`, phase: 'Find', schema: FINDING_SCHEMA, model: 'opus', effort: 'high' }
  )
))

const allFindings = findingSets.filter(Boolean).flatMap((r, i) =>
  (r.findings || []).map((f) => ({ ...f, lens: lenses[i]?.key || 'unknown' }))
)
log(`${allFindings.length} raw findings across ${lenses.length} lenses`)

phase('Verify')
const verified = await parallel(allFindings.map((f) => () =>
  agent(
    `You are an independent, skeptical verifier. Someone claims the following is a real bug/hole in the ` +
    `Latitude mod repo at ${repoRoot}. DEFAULT TO REFUTING unless you can confirm it by reading the actual ` +
    `current code yourself.\n\n` +
    `CLAIM:\nTitle: ${f.title}\nFile: ${f.file}\nLine/anchor: ${f.lineHint}\nSeverity claimed: ${f.severity}\n` +
    `Description: ${f.description}\nFailure scenario: ${f.failureScenario}\n\n` +
    `Read the actual file yourself. Reproduce or trace the failure scenario concretely (by hand or by writing ` +
    `a tiny throwaway check if that would settle it). Return CONFIRMED only if you have directly verified the ` +
    `defect exists as described; PLAUSIBLE if it looks likely but you could not fully verify; REFUTED if the ` +
    `code does not actually have this problem (explain why in reasoning). If CONFIRMED or PLAUSIBLE, sharpen ` +
    `the description with anything you learned (exact line numbers, more precise mechanism).`,
    { label: `verify:${f.lens}:${f.title.slice(0, 40)}`, phase: 'Verify', schema: VERDICT_SCHEMA, model: 'opus', effort: 'high' }
  ).then((v) => ({ ...f, ...v }))
))

const survivors = verified.filter(Boolean).filter((f) => f.verdict === 'CONFIRMED' || f.verdict === 'PLAUSIBLE')
log(`${survivors.length}/${allFindings.length} findings survived verification`)

phase('Synthesize')
const severityRank = { critical: 0, high: 1, medium: 2, low: 3 }
survivors.sort((a, b) => (severityRank[a.severity] ?? 9) - (severityRank[b.severity] ?? 9))

return {
  scopeLabel,
  rawFindingCount: allFindings.length,
  survivorCount: survivors.length,
  survivors,
}
