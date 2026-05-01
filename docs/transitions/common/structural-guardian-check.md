# structural-guardian-check

**Type**: Tool
**Used by**: ambient — runs after every write operation, on skill activation if not linted in 7+ days, when content lands in the wrong place, when user mentions wiki problems

## Inputs
- wiki root

## Prompt template
None.

## Procedure
Lightweight inline checks (full lint is `/wiki:lint`):
1. **Hub integrity**: HUB contains only `wikis.json`, `_index.md`, `log.md`, `topics/`. If `raw/`, `wiki/`, `output/`, `inbox/`, or `config.md` exist at hub level → warn (do not delete).
2. **Index freshness**: file counts in `wiki/concepts/`, `wiki/topics/`, `wiki/references/` vs rows in their `_index.md`. If mismatched → auto-fix via `rebuild-index`.
3. **Orphan detection**: `.md` files in wiki dirs not listed in any `_index.md` → add via `rebuild-index`.
4. **Missing directories**: required subdirs exist (`raw/articles/` etc). If missing → create with empty `_index.md`.
5. **wikis.json sync**: every `HUB/topics/<slug>/` is registered. If not → add. If registered but missing → remove the entry.
6. **log.md existence**: hub and active wiki both have `log.md`. If missing → create.

## Output
- Silent when clean.
- Auto-fix trivial issues; note in log.
- Warn on real structural problems (content in wrong place, etc).
- Never block the user's request — fix what you can, report, continue.
