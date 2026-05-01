# reflect-across-rounds

**Type**: LLM
**Used by**: `research --min-time` Phase 6 (between rounds, holistic)

## Inputs
- summary of all prior rounds (not just the most recent)
- this round's full output

## Prompt template
```
Reflect across all prior rounds. Priorities (in order):
1. Draw connections between this round's findings and ALL prior rounds.
2. Update cross-references — list See-Also additions to make.
3. Re-evaluate earlier gaps — which are now filled, which still open.
4. Score remaining gaps: impact (1-5) × feasibility (1-5) × specificity (1-5) = composite (1-125).
5. Adjust direction — only if findings clearly indicate a shift (rare).

All prior rounds:
[
  {"round":1,"summary":"...","gaps":[...],"progress_score":65,"articles":[...]},
  ...
]

This round:
{...}

Return JSON ONLY:

{
  "cross_round_connections": [
    "<round-1 finding about X> ↔ <round-2 finding about Y> -> new gap '<C>'"
  ],
  "see_also_additions": [
    {"from":"wiki/.../a.md","to":"wiki/.../b.md","relationship":"<phrase>"}
  ],
  "gap_reevaluation": {
    "filled": ["<gap text>"],
    "still_open_upgraded": ["<gap text>"],
    "new": ["<gap text>"]
  },
  "scored_next_gaps": [
    {"gap":"...","impact":5,"feasibility":4,"specificity":5,"composite":100}
  ],
  "direction_shift": null | "<one sentence>",
  "early_completion_recommended": false | true
}
```

## Output
JSON as specified. The orchestrator deterministically applies `see_also_additions` (each becomes an `enforce-bidirectional-link` invocation).

## Notes
The primary value of reflection is cross-round connection discovery (~34% improvement in cross-references), not redirecting research direction.
