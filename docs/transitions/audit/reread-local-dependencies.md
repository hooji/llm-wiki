# reread-local-dependencies

**Type**: Tool
**Used by**: `audit` Pass 3 Step 3b (per claim)

## Inputs
- claim's `supporting_sources_in_artifact`

## Prompt template
None.

## Procedure
For each cited dep:
1. Verify it still exists.
2. Read the file.
3. Note what it actually says about the claim now (not what the artifact paraphrased it as saying).

## Output
```json
{
  "claim_id":1,
  "deps":[
    {"path":"raw/papers/...md","still_exists":true,"snippet":"...","now_says":"..."},
    {"path":"wiki/...md","still_exists":true,"snippet":"...","now_says":"..."}
  ]
}
```
