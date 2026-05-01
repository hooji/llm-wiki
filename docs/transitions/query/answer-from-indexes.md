# answer-from-indexes

**Type**: LLM
**Used by**: `query --quick` Q3

## Inputs
- user question
- master + selected category index entries (no article bodies)

## Prompt template
```
Answer this question from index summaries alone. Do NOT request additional
articles. If the indexes do not contain enough to answer, say so.

Question: "{question}"

Index entries (from master + selected categories):
[
  {"path":"...","title":"...","summary":"...","tags":[...]},
  ...
]

Return JSON ONLY:
{
  "answer_markdown": "<short answer or 'insufficient information in indexes'>",
  "sources_used": [{"path":"...","what_drawn":"..."}],
  "knowledge_gaps": ["..."],
  "suggest_rerun_without_quick": true | false
}
```

## Output
JSON as specified. Rendered to user, logged.
