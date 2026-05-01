# inventory-mediawiki-dump

**Type**: Tool
**Used by**: `ingest-collection` Step C1 (mediawiki-dump adapter)

## Inputs
- dump file path/URL (`.xml`, `.xml.bz2`, `.xml.gz`)
- `--namespace <id>` (default `0`)
- `--include`, `--exclude` filters

## Prompt template
None.

## Procedure
1. Decompress if needed: `bunzip2 -c <file>` or `gunzip -c <file>`.
2. Stream-parse XML with `xml.etree.ElementTree.iterparse` to avoid loading the full dump into memory.
3. Default namespace `0`. Skip redirects, talk/user/file/special pages, and titles containing `:` unless explicitly included.
4. For each page, capture: `upstream_id` (page id or title), `revision` (revision id + timestamp), `canonical_url` (site URL + normalized title when derivable).

## Output
```json
{
  "adapter": "mediawiki-dump",
  "revision": "<dump filename or rev id>",
  "candidates": [
    {"upstream_id":"<page id>","title":"...","revision":"...","timestamp":"...","content_format":"wikitext"},
    ...
  ]
}
```
