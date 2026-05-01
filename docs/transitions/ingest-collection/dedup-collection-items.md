# dedup-collection-items

**Type**: Tool
**Used by**: `ingest-collection` Step C3

## Inputs
- candidate list from inventory
- existing raw sources in `<wiki>/raw/articles/` and `<wiki>/raw/repos/`

## Prompt template
None.

## Procedure
For each candidate, compute the dedup key:
```
key = (collection_slug, upstream_id, revision_or_sha)
```

Match against frontmatter of existing raw files in the collection's namespace. Decision matrix:
- exact key match → skip (already ingested)
- same `(collection, upstream_id)` but different `revision`/`sha` → write a new immutable source; keep the old one
- no match → ingest

## Output
```json
{
  "to_ingest": [<candidate>, ...],
  "skipped_duplicates": [<candidate>, ...],
  "skipped_already_at_revision": [<candidate>, ...]
}
```

## Notes
Raw is immutable. Upstream changes always produce new files; the old version is preserved as historical provenance.
