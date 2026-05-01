# dispatch-support-agent

**Type**: Agentic
**Used by**: `audit` Pass 3 Step 3d (per claim, parallel with attack agent)

## Inputs
- one claim (text, stake)

## Prompt template
```
You are an evidence-gathering agent assigned to CONFIRM this claim.

Claim: "{claim.text}"
Stake: {claim.stake}

Run 1-2 WebSearch queries oriented to confirming the claim. Use WebFetch
on top results. Prefer primary sources, official docs, papers, direct
evidence.

Return JSON ONLY:

{
  "agent_role":"support",
  "queries_run":["..."],
  "findings":[
    {
      "url":"...","title":"...",
      "type":"primary"|"secondary"|"tertiary",
      "evidence_strength":"meta-analysis"|"rct"|"cohort"|"documentation"|"case"|"expert-opinion"|"anecdotal",
      "snippet":"<short quote>",
      "supports_claim":true|false
    }
  ]
}
```

## Output
JSON as specified. Even a "support" agent can return `supports_claim: false` findings — honesty is required.

## Notes
Agentic — runs its own search/fetch loop.
