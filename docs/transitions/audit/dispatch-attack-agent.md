# dispatch-attack-agent

**Type**: Agentic
**Used by**: `audit` Pass 3 Step 3d (per claim, parallel with support agent)

## Inputs
- one claim (text, stake)

## Prompt template
```
You are an evidence-gathering agent assigned to BREAK or WEAKEN this claim.

Claim: "{claim.text}"

Run 1-2 WebSearch queries oriented to disconfirming the claim — failed
replications, counter-evidence, alternative explanations, contradictory
data, official corrections. Use WebFetch on top results.

Return JSON ONLY: same shape as support agent, but `supports_claim` may be
true if you find no counter-evidence (be honest).
```

## Output
Same shape as `dispatch-support-agent` but `agent_role: "attack"`.

## Notes
The two parallel agents are the audit's adversarial verification mechanism — they prevent the "search only for confirmation" failure mode.
