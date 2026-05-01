# rebuild-index

**Type**: Tool
**Used by**: `stale-check-index` when stale; `compile`, `ingest`, `research` after writes (best-effort)

## Inputs
- directory path

## Prompt template
None.

## Procedure
1. Glob `*.md` in directory excluding `_index.md`.
2. For each file, read YAML frontmatter (`title`, `summary`, `tags`, `updated`).
3. Render fresh `_index.md` from a template:
   ```
   # <Directory> Index

   > <description>

   Last updated: <today>

   ## Contents
   | File | Summary | Tags | Updated |
   |------|---------|------|---------|
   | [<file>](<file>) | <summary> | <tags> | <updated> |
   ...

   ## Recent Changes
   - <today>: Index rebuilt
   ```
4. For master `_index.md`, additionally include Statistics (counts) and Quick Navigation sections.
5. Write atomically.

## Output
Updated `_index.md` on disk.
