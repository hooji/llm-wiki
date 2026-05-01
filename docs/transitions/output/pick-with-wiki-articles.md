# pick-with-wiki-articles

**Type**: LLM
**Used by**: `output` Step 2 (per `--with <wiki>`); also `query` and `plan` when they accept `--with`

## Inputs
- output type
- subject summary (from primary wiki)
- supplementary wiki name + its index entries

## Prompt template
```
You are picking craft/skill articles to apply to a generated artifact.

Output type: {type}
Output topic (subject matter from primary wiki): "{topic_or_summary}"

Supplementary wiki: "{with_name}"
Supplementary wiki articles available:
[{"path":"...","title":"...","summary":"...","tags":[...]}, ...]

Return JSON ONLY:
{
  "articles":[
    {"path":"...","apply_to":"<which part of the output this technique applies to>"}
  ]
}
```

(target 3-7 entries)

## Output
JSON as specified. Selected articles are read in full and passed to `generate-output-artifact` as craft context.

## Notes
Primary wiki = subject matter (domain knowledge). `--with` wikis = craft/skill (writing techniques, frameworks, design patterns). When generating, apply supplementary techniques to primary content.
