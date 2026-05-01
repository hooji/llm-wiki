# choose-target-wiki

**Type**: LLM
**Used by**: `lessons` Step 3 (only when `--wiki` was not specified)

## Inputs
- session keywords + lesson titles + lesson tags
- `HUB/wikis.json` descriptions

## Prompt template
```
Pick the best-matching topic wiki for these lessons.

Topic wikis (slug → description):
{wikis_json_descriptions}

Session keywords: {topic_keywords}
Lesson titles: [...]
Lesson tags:   [...]

Return JSON ONLY:

{
  "best_match":"<slug or null>",
  "match_strength":"strong"|"weak"|"none",
  "alternatives":["<slug>",...],
  "suggest_new_wiki":null|"<proposed slug>",
  "reasoning":"<one sentence>"
}
```

## Output
JSON as specified. Logic on the result:
- `--wiki` set → override.
- `match_strength == "strong"` → use it.
- otherwise → numbered choice menu including alternatives + `suggest_new_wiki` + "ask which".
