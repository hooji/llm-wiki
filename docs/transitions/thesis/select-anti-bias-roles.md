# select-anti-bias-roles

**Type**: Tool
**Used by**: `research --mode thesis --min-time` (round 2+)

## Inputs
- prior rounds' evidence counts (`evidence_for`, `evidence_against`)
- current round number

## Prompt template
None.

## Procedure
```
weaker_side = (evidence_for >= evidence_against) ? "opposing" : "supporting"

if round == 1:
    return [Supporting, Opposing, Mechanistic, Meta, Adjacent]
elif round == 2:
    if weaker_side == "opposing":
        return [Opposing, Opposing, Opposing, Confounders, Meta]
    else:
        return [Supporting, Supporting, Supporting, Mechanistic, Meta]
else:  # round 3+
    return roles based on Phase 6 reflection's specific sub-questions
```

## Output
`{ "roles": [...], "weaker_side": "supporting" | "opposing", "rationale": "..." }`

## Notes
This is the methodological difference between thesis and standard research — Round 2 deliberately drills into the weaker side to fight confirmation bias. Without it, agents that found supporting evidence in Round 1 would tend to keep finding it.
