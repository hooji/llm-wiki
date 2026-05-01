# init-topic-wiki

**Type**: Tool
**Used by**: `wiki init <name>`, `--new-topic <name>` in `ingest`/`research`/`ingest-collection`

## Inputs
- `HUB` (or local project root if `--local`)
- topic slug (lowercase, hyphens, max 40 chars)
- description text from user (optional)

## Prompt template
None.

## Procedure
1. If hub does not exist yet, run `init-hub` first.
2. Create directory tree at `HUB/topics/<slug>/`:
   - `inbox/`, `inbox/.processed/`
   - `raw/{articles,papers,repos,notes,data}/`
   - `wiki/{concepts,topics,references}/`
   - `output/`
3. Write minimal `.obsidian/{app,appearance,graph}.json`.
4. Write empty `_index.md` in every directory.
5. Write `config.md` with `title`, `description`, `created`, `freshness_threshold: 70`.
6. Write `log.md` with `## [<today>] init | Wiki initialized`.
7. Register in `HUB/wikis.json` under `wikis[<slug>]`. Update hub `_index.md` topic table.
8. For `--local`: append `.wiki/` to project `.gitignore`.

## Output
Files on disk; the new wiki root path returned for downstream operations.
