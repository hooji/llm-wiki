# classify-topic-wiki

**Type**: LLM
**Used by**: `ingest` Step 5 (single item, hub-level resolution, no `--wiki`)

## Inputs
- extracted source metadata (title, summary, tags)
- list of registered topic wikis with descriptions from each one's `config.md`

## Prompt template
```
You are routing a new source into the best-matching topic wiki.

Source:
  title: {title}
  summary: {summary}
  tags: {tags}

Topic wikis (slug → description):
{
  "ai-security": "AI security, agent safety, prompt injection",
  "geo": "GEO & AI Search Optimization",
  ...
}

Return JSON:

{
  "best_match": "<slug or null>",
  "match_strength": "strong" | "weak" | "none",
  "reasoning": "<one sentence>",
  "alternatives": ["<slug>", ...]
}
```

## Output
JSON as specified. The orchestrator presents the menu (best match + alternatives + "New wiki" + "Skip") and waits for the user to pick.
