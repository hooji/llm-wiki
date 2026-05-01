# synthesize-answer

**Type**: LLM
**Used by**: `query` standard Step 6, `query --deep` D7

## Inputs
- user question
- read articles (full body) ranked by relevance
- sibling-wiki overlap summaries (deep only)
- supplementary `--with` wiki articles (when supplied)

## Prompt template
```
You are answering a question from a wiki. Use ONLY the provided article
content. Do NOT use your training knowledge. If the wiki does not have
enough information, say so.

Question: "{question}"

Wiki articles (in order of relevance):
[
  {
    "path":"wiki/concepts/...md",
    "title":"...",
    "confidence":"high",
    "content":"<full article body>"
  },
  ...
]

Sibling wiki overlap (only _index.md content): {sibling_index_summaries}

--with supplementary wiki content (craft/skill, optional): {with_wikis_content_or_empty}

Return JSON ONLY:

{
  "answer_markdown": "<the full answer in markdown, with [text](path) citations to the wiki articles>",
  "sources_used": [
    {"path":"wiki/...md","confidence":"high","what_drawn":"<one phrase>"}
  ],
  "related_in_other_wikis": [
    {"wiki":"<name>","title":"<article>","why":"<phrase>"}
  ],
  "knowledge_gaps": ["<gap 1>","<gap 2>"],
  "suggested_ingest": ["<topic or URL pattern to add>"]
}

Citation rule: every factual claim that came from an article must have an
inline link [text](path) to that article. Mention confidence when it is
medium or low.
```

## Output
JSON as specified. Rendered to user with Sources / Related / Gaps blocks; logged.
