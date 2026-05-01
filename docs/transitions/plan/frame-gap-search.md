# frame-gap-search

**Type**: LLM
**Used by**: `plan` Stage 3a (per gap; skipped if `--no-research` or `--quick`)

## Inputs
- one knowledge gap (text, why_blocking)
- the goal

## Prompt template
```
You are framing a tight web search to fill a knowledge gap.

Gap: "{gap}"
Why it blocks the plan: "{why_blocking}"
Goal: "{goal}"

Return JSON ONLY:

{
  "queries":["<query 1>","<query 2>"],
  "what_to_extract":"<3-5 word description of the answer shape>"
}
```

## Output
JSON as specified. Drives the WebSearch + per-hit `extract-gap-finding` loop.
