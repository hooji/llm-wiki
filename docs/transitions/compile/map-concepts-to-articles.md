# map-concepts-to-articles

**Type**: Tool
**Used by**: `compile` Step 4

## Inputs
- per-source signals from `extract-source-signals`
- existing articles list (from survey)

## Prompt template
None.

## Procedure
Build `Map<slug, ArticleStatus>`:
```
for each (source, signals):
    for c in signals.key_concepts:
        if c.salience >= 3:
            if existsArticle(c.slug):
                map[c.slug] = (EXISTING_UPDATE, c.kind, sources_to_add)
            else:
                map[c.slug] = (NEW_CREATE, c.kind, sources_to_add)
        else:
            map[c.slug] = MENTION_ONLY
```

Salience filter (≥3) prevents one-line mentions from spawning stub articles.

## Output
```json
{
  "to_create": [{"slug":"...","kind":"concept","contributing_sources":[...]}, ...],
  "to_update": [{"slug":"...","existing_path":"...","contributing_sources":[...]}, ...],
  "mentions_only": ["slug-1", ...]
}
```
