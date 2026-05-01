# refetch-primary-sources

**Type**: Tool (`web-fetch`)
**Used by**: `audit` Pass 3 Step 3c (per claim, when raw sources have live URLs)

## Inputs
- raw source path + its `source:` URL

## Prompt template (passed to web-fetch)
```
Re-fetch the original primary source. Return: title, current published
date, full article text. Format as clean markdown.
```

## Procedure
1. WebFetch the URL.
2. Compare to the captured body in the raw file (textual diff or content hash).
3. Note any divergence as "upstream changed since ingest".

## Output
```json
{
  "claim_id":1,
  "primaries":[
    {
      "raw_path":"raw/.../...md",
      "url":"...",
      "fetched_ok":true,
      "upstream_changed":true|false,
      "current_excerpt":"..."
    }
  ]
}
```

## Notes
Skip this transition under `--quick` unless the user explicitly demanded fresh verification.
