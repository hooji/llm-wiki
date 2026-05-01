# render-claim-verdict

**Type**: LLM
**Used by**: `audit` Pass 3 Step 3e (per claim)

## Inputs
- claim text
- support agent findings
- attack agent findings
- primary-source agent findings (when present)
- local evidence summary (artifact's existing citations)

## Prompt template
```
You are reaching a truth verdict for a claim audited via parallel
support+attack research.

Claim: "{claim.text}"

Agent findings:
{
  "support": [...],
  "attack":  [...],
  "primary": [...]
}

Local evidence summary (the artifact's existing citations):
{...}

Reach a verdict from this rubric:
- supported:    clear preponderance of strong evidence in favor; opposing weak
- weakened:     supportive evidence still exists but is weaker than the artifact implies
- contradicted: clear preponderance of strong evidence against
- unresolved:   evidence does not converge; honest "we don't know"

Never hide mixed evidence behind a binary label.

Return JSON ONLY:

{
  "verdict":"supported"|"weakened"|"contradicted"|"unresolved",
  "confidence":"high"|"medium"|"low",
  "rationale_2_3_sentences":"...",
  "key_supporting_evidence":["<url> — <one-liner>"],
  "key_opposing_evidence":["<url> — <one-liner>"],
  "what_would_change_this":"<specific future evidence>",
  "recommended_action":"<one of: refresh source, retract claim, add caveat, no action, escalate to /wiki:research>"
}
```

## Output
JSON as specified. Drives the artifact's final verdict in `classify-output-verdict` and the user's recommended next step.
