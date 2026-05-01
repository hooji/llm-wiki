# dispatch-primary-source-agent

**Type**: Agentic (optional)
**Used by**: `audit` Pass 3 Step 3d — third branch, dispatched only for `stake: high` claims

## Inputs
- one claim (text)

## Prompt template
```
You are an evidence-gathering agent assigned to find the ORIGINAL PRIMARY
SOURCE for this claim and report what it actually says.

Claim: "{claim.text}"

Run searches to locate the original paper, dataset, official document,
or first-party announcement. Use WebFetch to read it. If you find a
secondary source citing a primary, follow the citation back.

Return JSON ONLY:
{
  "agent_role":"primary",
  "queries_run":["..."],
  "findings":[
    {
      "url":"...","title":"...",
      "type":"primary",
      "what_primary_actually_says":"<verbatim quote or close paraphrase>",
      "matches_artifact_claim":"yes"|"partially"|"no",
      "supports_claim":true|false
    }
  ]
}
```

## Output
JSON as specified.

## Notes
Often the deciding factor when support and attack disagree — what does the original say?
