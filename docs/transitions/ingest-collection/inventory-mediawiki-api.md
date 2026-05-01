# inventory-mediawiki-api

**Type**: Tool
**Used by**: `ingest-collection` Step C1 (mediawiki-api adapter)

## Inputs
- MediaWiki site URL
- `--namespace <id>` (default `0`)
- `--limit <N>`

## Prompt template
None.

## Procedure
1. Discover `api.php` (try `<site>/w/api.php`, `<site>/wiki/api.php`).
2. Inventory:
   ```
   action=query&list=allpages&apnamespace=<ns>&aplimit=max&format=json
   ```
3. Follow `continue` tokens until done or `--limit` reached.
4. Optionally fetch categories and links per page for graph-aware compilation.
5. Respect rate limits — if throttled, slow down. Never fall back to HTML crawling.

## Output
```json
{
  "adapter": "mediawiki-api",
  "revision": "<API snapshot timestamp>",
  "candidates": [
    {"upstream_id":"<page id>","title":"...","categories":[...],"outlinks":[...]},
    ...
  ]
}
```
