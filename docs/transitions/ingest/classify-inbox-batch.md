# classify-inbox-batch

**Type**: LLM
**Used by**: `ingest --inbox` Step C (when no `--wiki` is set)

## Inputs
- metadata for every inbox item (title, summary, tags) — fetched in parallel beforehand
- list of registered topic wikis with descriptions

## Prompt template
```
You are routing a batch of new sources to topic wikis.

Topic wikis:
{wikis_json_descriptions}

Items:
[
  {"index": 1, "title": "...", "summary": "...", "tags": [...]},
  ...
]

Return JSON:
{
  "routes": [
    {"index": 1, "wiki": "ai-basics", "match": "strong"},
    {"index": 2, "wiki": "ai-security", "match": "strong"},
    {"index": 3, "wiki": null, "suggested_new": "cloud-infra", "match": "weak"},
    {"index": 4, "wiki": null, "match": "none"}
  ]
}
```

## Output
JSON as specified. Orchestrator presents the table, gets `y/edit/abort`, then processes items grouped by target wiki.
