# stale-check-index

**Type**: Tool
**Used by**: every read of any `_index.md`

## Inputs
- directory path

## Prompt template
None.

## Procedure
1. List `*.md` in directory, excluding `_index.md`.
2. Parse the Contents table in `_index.md` and count rows.
3. If file count ≠ row count → index is stale → trigger `rebuild-index`, then return rebuilt index.
4. Else → return current index as-is.

## Output
`{ "stale": true | false, "index_markdown": "..." }`

## Notes
Cornerstone of the Derived Index Protocol: indexes are caches; frontmatter is truth. Makes concurrent writes safe without locks.
