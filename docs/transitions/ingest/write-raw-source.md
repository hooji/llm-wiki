# write-raw-source

**Type**: Tool
**Used by**: `ingest` Step 6, `research` Phase 3, `lessons` Step 5 (variant), `ingest-collection` (child-source variant)

## Inputs
- wiki root
- target raw type (`articles` / `papers` / `repos` / `notes` / `data`)
- frontmatter object (title, source, type, ingested, tags, summary, optional collection-provenance fields)
- body markdown

## Prompt template
None.

## Procedure
1. Compute slug: `slugify(title)` → lowercase, hyphens, no special chars, max 60 chars.
2. Compute filename: `<YYYY-MM-DD>-<slug>.md`. On existence, bump `-2`, `-3`, … until unique.
3. Compose:
   ```markdown
   ---
   title: "..."
   source: "<URL/filepath/MANUAL>"
   type: <type>
   ingested: <date>
   tags: [...]
   summary: "..."
   ---

   # <title>

   <body markdown>
   ```
4. Atomically write to `<wiki>/raw/<type>/<filename>`.

## Output
Path of the written file.

## Notes
Sources are immutable once written. To handle upstream changes, write a new file rather than modifying the existing one.
