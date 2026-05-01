# summarize-collection-item

**Type**: LLM (optional, batched)
**Used by**: `ingest-collection` Step C4 (when upstream is wikitext or unfamiliar markup)

## Inputs
- one fetched collection item

## Prompt template
```
You are writing a 2-3 sentence factual summary for a raw source captured
from an upstream collection.

Upstream type: {git-file | mediawiki-page}
Upstream content format: {markdown | wikitext | mediawiki | text}
Upstream title: {title}
Upstream content (between fences):
```
{content}
```

Rules:
- Factual only, no editorializing.
- Preserve normative language for spec/proposal sources.
- Mention status fields (e.g. BIP "Status: Draft", "Status: Final") when present.

Return JSON ONLY:
{
  "summary": "<2-3 sentences>",
  "tags": ["<lowercase-hyphenated-tag>", ...]   // 3-7 tags
}
```

## Output
JSON as specified. For BIPs, deterministic header parsing usually replaces this transition.
