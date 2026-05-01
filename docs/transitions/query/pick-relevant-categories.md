# pick-relevant-categories

**Type**: LLM
**Used by**: `query` standard depth Step 2

## Inputs
- user question
- master `_index.md` content
- per-category `_index.md` summaries (concepts / topics / references)

## Prompt template
```
You are routing a question to the right corner of a wiki.

Question: "{question}"

Master index Contents (categories at top level):
{master_index_table_as_markdown}

Quick Navigation:
- concepts:   {concepts_index_summary_or_count}
- topics:     {topics_index_summary_or_count}
- references: {references_index_summary_or_count}

Return JSON ONLY:

{
  "relevant_categories": ["concepts", "topics"],
  "reasoning": "<one sentence>"
}
```

## Output
JSON as specified. The orchestrator reads only the listed categories' indexes for `pick-candidate-articles`.

## Notes
Skipped in `--deep` mode (which reads all category indexes unconditionally) and in `--quick` mode (which falls through directly to `answer-from-indexes`).
