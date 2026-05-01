# rebuild-raw-indexes

**Type**: Tool
**Used by**: `ingest-collection` Step C7

## Inputs
- wiki root

## Prompt template
None.

## Procedure
After a collection batch (which may have written hundreds of files), do not hand-edit table rows. Instead, regenerate from frontmatter:
1. `rebuild-index` for each affected `raw/<type>/_index.md`.
2. `rebuild-index` for `raw/_index.md`.
3. Update master `_index.md` source count and Recent Changes.

## Output
Rebuilt index files on disk.

## Notes
Same idea as the Derived Index Protocol's normal stale-rebuild — done eagerly here because the batch size makes incremental row appends unattractive.
