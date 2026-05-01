# pick-candidate-articles

**Type**: LLM
**Used by**: `query` standard / deep Step 3

## Inputs
- user question
- relevant categories' index entries (path / title / summary / tags)
- depth (`standard` → 3-8 candidates, `deep` → 8-20)

## Prompt template
```
You are picking the most relevant wiki articles for a question.

Question: "{question}"

Articles available (from category indexes):
[
  {"path":"wiki/concepts/transformer-architecture.md",
   "title":"Transformer Architecture",
   "summary":"...",
   "tags":["transformer","attention"]},
  ...
]

Return JSON ONLY:

{
  "candidates": [
    {"path":"<path>","relevance":"primary"|"supporting","why":"<phrase>"}
  ]
}
```

(target count varies by depth)

## Output
JSON as specified. Articles are read in full afterwards.
