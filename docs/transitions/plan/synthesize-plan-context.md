# synthesize-plan-context

**Type**: Tool
**Used by**: `plan` Stage 4

## Inputs
- Stage-1 wiki context
- Stage-2 interview answers (id → answer)
- Stage-3 gap fills

## Prompt template
None.

## Procedure
Merge into an internal context object (not shown to user; input to `generate-plan-document`):

```json
{
  "goal":"...",
  "wiki_evidence":{
    "directly_relevant":[...],
    "supporting":[...],
    "key_facts":[...]
  },
  "user_requirements":{
    "<question_id>":"<answer>"
  },
  "gap_fills":[
    {"gap":"...","findings":[...],"sources":["url1","url2"]}
  ],
  "constraints":[...],
  "risks":[...]
}
```

## Output
The merged context object.
