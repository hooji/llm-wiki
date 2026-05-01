# scope-existing-knowledge

**Type**: LLM
**Used by**: `research` Phase 1 (skipped in `--retardmax`)

## Inputs
- topic / question / thesis
- master index summary
- articles already touching the topic (from Grep)

## Prompt template
```
You are scoping a research run. The wiki currently contains:

Master index summary:
{master_index_table}

Articles already covering this topic (from grep):
[
  {"path":"...","title":"...","summary":"...","tags":[...]}
]

User's research topic: "{topic}"

Return JSON ONLY:

{
  "existing_coverage_summary": "<2-3 sentences on what's covered>",
  "gaps": [
    {"gap":"<specific gap>","why_matters":"<phrase>"}
  ],
  "search_angles": [
    "<angle 1>","<angle 2>",...
  ]
}
```

## Output
JSON as specified. Drives Phase 2 agent dispatch (gives every agent a "what's NOT covered" hint).
