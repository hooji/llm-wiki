# dispatch-thesis-agent

**Type**: Agentic
**Used by**: `research --mode thesis` Phase 2 (parallel — 5 agents standard, +3 in `--deep`)

## Inputs
- thesis decomposition (`core_claim`, `key_variables`, `falsification_criteria`, `scope_boundary`)
- agent lens: `Supporting` / `Opposing` / `Mechanistic` / `Meta/Review` / `Adjacent` (+ `Historical` / `Quantitative` / `Confounders` in `--deep`)

## Prompt template
```
You are investigating: "{thesis_statement}"

Core claim: {core_claim}
Key variables: {key_variables}
Falsification criteria: {falsification_criteria}
Scope boundary (sources outside this scope MUST be skipped): {scope_boundary}

Your lens: {Agent Focus} — {Thesis Lens description}

Run 2-3 WebSearch queries varied by your lens. Use WebFetch on promising
results.

For each source you would consider, evaluate:
- Relevance: direct | indirect | tangential — SKIP tangential.
- Evidence strength: meta-analysis > RCT > cohort > case > expert opinion > anecdotal
- Direction relative to the thesis: supports | opposes | nuances
- Quality (1-5)

The thesis is the BLOAT FILTER: if the source does not bear on the
key_variables, do not return it.

Return JSON ONLY:

{
  "agent_role":"{role}",
  "queries_run":["..."],
  "sources":[
    {
      "title":"...","url":"...",
      "publication_date":"YYYY-MM-DD" | null,
      "authors":["..."],
      "relevance":"direct" | "indirect",
      "evidence_strength":"meta-analysis"|"rct"|"cohort"|"case"|"expert-opinion"|"anecdotal",
      "direction":"supports"|"opposes"|"nuances",
      "quality_score":1-5,
      "key_finding":"<1-2 sentence summary of what this source says about the thesis>",
      "content_markdown":"<extracted body>"
    }
  ],
  "skipped_tangential_count": <int>
}

Sort sources by (relevance × evidence_strength), strongest first.
```

## Output
JSON as specified. High `skipped_tangential_count` is good — the bloat filter is working.

## Notes
Like `dispatch-research-agent`, this is agentic (the agent runs its own search/fetch loop). The differences from the topic agent are: scope filter, evidence-strength rubric, direction tagging.
