# identify-claims-under-scrutiny

**Type**: LLM
**Used by**: `audit` Pass 3 Step 3a (per escalated artifact)

## Escalation triggers
Run only when **any** is true:
- user explicitly asked whether they can trust the artifact
- output is `drifted` or has `provenance-gap`
- a cited wiki article is stale, weak, or contradictory
- source chain is thin AND claim matters
- topic is `volatility: hot`
- conflicting local claims need external resolution

(skip Pass 3 entirely on `--quick` unless explicitly demanded)

## Inputs
- artifact path + relevant excerpt
- triggers fired

## Prompt template
```
You are identifying which specific claims need fresh verification.

Artifact: {artifact_path}
Artifact body (relevant excerpt):
"{excerpt}"

Triggers fired: ["drifted-dependency","stale-upstream",...]

Return JSON ONLY:

{
  "claims":[
    {
      "id":1,
      "text":"<the specific claim, quoted or paraphrased>",
      "type":"factual"|"causal"|"predictive"|"comparative",
      "supporting_sources_in_artifact":["raw/...","wiki/..."],
      "stake":"low"|"medium"|"high"
    },
    ...
  ]
}
```

## Output
JSON as specified. Orchestrator processes `stake: high` first.
