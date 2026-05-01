# fetch-collection-item

**Type**: Tool
**Used by**: `ingest-collection` Step C4 (per item)

## Inputs
- one candidate from `to_ingest`
- adapter (`git` / `mediawiki-dump` / `mediawiki-api`)

## Prompt template
None.

## Procedure
Per adapter:
- **git**: read the blob at HEAD using the captured blob SHA.
- **mediawiki-dump**: read the latest revision text from the streamed XML iterator.
- **mediawiki-api**: batch fetch with `prop=revisions&rvslots=main&rvprop=ids|timestamp|user|comment|content`.

Preserve the full upstream text — tables, code blocks, proposal metadata, references. Do not summarize away normative requirements in specs.

## Output
```json
{
  "upstream_id": "...",
  "revision": "...",
  "content_markdown_or_wikitext": "...",
  "content_format": "markdown" | "wikitext" | "mediawiki" | "text",
  "authors": ["..."],
  "fetched_at": "<ISO 8601>"
}
```
