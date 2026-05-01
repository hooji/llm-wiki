# rank-search-results

**Type**: LLM
**Used by**: `query --list` L2

## Inputs
- user query
- raw hits from index scan + Grep, with match metadata

## Prompt template
```
Rank these wiki search results by relevance to the query.

Query: "{query}"

Results:
[
  {"path":"...","title":"...","summary":"...","tags":[...],
   "match_kind":"title"|"summary"|"body"|"tag",
   "match_count":3,
   "updated":"2026-04-04"},
  ...
]

Ranking rules: title match > summary match > body match. Multiple-term match
beats single. More recent beats older.

Return JSON ONLY:
{
  "ranked": [
    {"path":"...","rank":1,"reason":"<phrase>"},
    ...
  ]
}
```

## Output
JSON as specified. Rendered as a ranked list (not a synthesized answer).
