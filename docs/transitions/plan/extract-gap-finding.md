# extract-gap-finding

**Type**: LLM
**Used by**: `plan` Stage 3c (per WebFetch'd hit)

## Inputs
- one gap
- one fetched page's URL + content markdown

## Prompt template
```
Extract the answer to this gap from the page.

Gap: "{gap}"
What to extract: "{what_to_extract}"

Page URL: {url}
Page content (between fences):
```
{page_markdown}
```

Return JSON ONLY:

{
  "relevant":true|false,
  "extract_bullets":["<finding>",...],
  "credibility":1-5,
  "ingest_into_wiki":true|false
}
```

## Output
JSON as specified. If `ingest_into_wiki: true`, the orchestrator runs the Ingest pipeline so the source persists in `raw/` for next time.
