# assemble-wiki-context

**Type**: LLM
**Used by**: `plan` Stage 1

## Inputs
- goal text
- full content of relevant wiki articles (from index scan + Grep, including See-Also one-hop)
- sibling wiki `_index.md` overlap summaries
- `--with` wiki articles (when supplied) with full content

## Prompt template
```
You are assembling research context for an implementation plan.

Goal: "{goal}"

Wiki articles read (full content, in order):
[
  {"path":"...","title":"...","confidence":"high","content":"<full body>"},
  ...
]

Sibling wiki overlap (only _index summaries):
[{"wiki":"...","article":"...","summary":"..."}, ...]

--with wiki content (craft/skill, optional):
[
  {"wiki":"<name>","articles":[{"path":"...","content":"..."}]}
]

Return JSON ONLY:

{
  "directly_relevant":[
    {"path":"...","title":"...","contributes":"<one phrase>"}
  ],
  "supporting_context":[
    {"path":"...","title":"...","relevant_because":"<phrase>"}
  ],
  "constraints_from_wiki":["<constraint>",...],
  "risks_from_contrarian_articles":[
    {"risk":"...","source":"<path>"}
  ],
  "knowledge_gaps":[
    {"gap":"<specific gap>","why_blocking":"<phrase>"}
  ],
  "summary_for_user":"<2-3 sentence summary of what the wiki knows>"
}
```

## Output
JSON as specified. The `summary_for_user` is shown to the user before the interview.
