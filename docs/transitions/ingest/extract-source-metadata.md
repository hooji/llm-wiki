# extract-source-metadata

**Type**: LLM
**Used by**: `ingest` Step 3 (every source kind)

## Inputs
- `content_markdown` from the fetch transition
- optional pre-known title (URL ingest may already have one)
- optional user overrides (`--title`, `--type`)

## Prompt template
```
You are extracting structured metadata for a knowledge-base raw source.

Content (between fences):
```
{content_markdown}
```

Return JSON ONLY with this exact shape:

{
  "title": "<short descriptive title, max 80 chars>",
  "summary": "<2-3 sentence factual summary; no marketing language>",
  "tags": ["<lowercase-hyphenated-tag>", ...],
  "type_suggestion": "articles" | "papers" | "repos" | "notes" | "data",
  "authors": ["..."]
}

Tag rules:
- lowercase, hyphen-separated
- specific over general (good: "self-attention"; bad: "ml")
- 3-7 tags total
- no near-duplicates ("nlp" vs "natural-language-processing")
```

## Output
JSON as specified. The orchestrator merges user overrides on top.
