# render-thesis-verdict

**Type**: LLM
**Used by**: `research --mode thesis` Phase 5 (final round only, or single-round runs)

## Inputs
- thesis statement
- cumulative evidence after all rounds (Strong/Moderate/Weak grouped by direction)
- per-round evidence counts and verdict directions

## Prompt template
```
Render a thesis verdict.

Thesis: "{thesis_statement}"
Cumulative evidence after all rounds:
- Strong supporting: [...]
- Strong opposing:   [...]
- Moderate supporting: [...]
- Moderate opposing: [...]
- Weak supporting: [...]
- Weak opposing: [...]
- Nuances & caveats: [...]

Per-round counts:
[ {"round":1,"for":4,"against":2,"verdict_direction":"partially-supported"}, ... ]

Verdict rules:
- supported: clear preponderance of strong evidence in favor; opposing weak
- partially-supported: supportive overall but with meaningful caveats or moderating conditions
- contradicted: clear preponderance of strong evidence against
- mixed: roughly balanced strong evidence on both sides
- insufficient-evidence: no strong evidence either way

Never hide mixed evidence behind a binary label.

Return JSON ONLY:

{
  "verdict":"supported"|"partially-supported"|"contradicted"|"mixed"|"insufficient-evidence",
  "confidence":"high"|"medium"|"low",
  "summary_2_3_sentences":"...",
  "strongest_supporting_evidence":["<source title> — <one-liner>",...],
  "strongest_opposing_evidence":["..."],
  "key_caveats":["..."],
  "what_would_change_this_verdict":["<specific future findings>",...],
  "suggested_followup_theses":["<derived testable claim>",...]
}
```

## Output
JSON as specified. Drives `apply-verdict-edit`.
