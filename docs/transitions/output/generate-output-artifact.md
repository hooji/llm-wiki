# generate-output-artifact

**Type**: LLM
**Used by**: `output` Step 3 (one of seven type-specific prompts)

## Inputs
- output type: `summary` / `report` / `study-guide` / `slides` / `timeline` / `glossary` / `comparison`
- gathered subject articles (full body)
- `--with` craft articles (full body, when supplied)
- mode: `standard` / `retardmax`

## Prompt template
Common header:
```
You are generating a {type} from a wiki knowledge base.

Subject articles (from primary wiki):
[
  {"path":"...","title":"...","confidence":"high","content":"<full body>"},
  ...
]

Craft articles (from --with wikis, optional):
[
  {"wiki":"<name>","path":"...","title":"...","apply_to":"<phrase>","content":"<full body>"}
]

Mode: {standard | retardmax}
```

Then per type, append the corresponding JSON schema spec. Examples:

### summary
```
Return JSON ONLY:
{ "title":"...","subtitle":"<optional>","tldr":"<2-3 sentences>",
  "sections":[{"heading":"## Key Points","markdown":"<3-7 bullets, with [Article](path) citations>"},
              {"heading":"## What This Means","markdown":"..."},
              {"heading":"## Sources","markdown":"<list of dual-link citations>"}] }
```

### report
```
Return JSON ONLY:
{ "title":"...","executive_summary":"...",
  "sections":[{"heading":"## Background","markdown":"..."},
              {"heading":"## Findings","markdown":"<each with [Article](path) citations>"},
              {"heading":"## Analysis","markdown":"..."},
              {"heading":"## Conclusions","markdown":"..."},
              {"heading":"## Sources","markdown":"..."}],
  "wiki_articles_used":["wiki/...md",...] }
```

### study-guide
```
Return JSON ONLY:
{ "title":"...",
  "concepts":[{"name":"...","slug":"...","definition":"...",
               "key_relationships":[{"to":"<slug>","kind":"is-a|part-of|contrasts-with","description":"..."}],
               "wiki_link":"wiki/concepts/...md"}],
  "questions":[{"q":"...","a":"<short answer with [Article](path) citations>",
                "difficulty":"easy|medium|hard"}] }
```

### slides
```
Return JSON ONLY:
{ "title":"...","subtitle":"...",
  "slides":[{"title":"...","bullets":["...","..."],
             "speaker_notes":"<optional>","wiki_citations":["wiki/...md"]}] }
```

### timeline
```
Return JSON ONLY:
{ "title":"...",
  "entries":[{"date":"YYYY|YYYY-MM|YYYY-MM-DD",
              "event":"...","significance":"<one sentence>",
              "source_article":"wiki/...md"}] }
```

### glossary
```
Return JSON ONLY:
{ "title":"...","scope":"<what this glossary covers>",
  "entries":[{"term":"...","aliases":["..."],
              "definition":"<1-3 sentences>","see_also":["<term>","<term>"],
              "source_article":"wiki/.../...md"}] }
```

### comparison
```
Return JSON ONLY:
{ "title":"...",
  "subjects":[{"name":"...","wiki_article":"wiki/...md"}],
  "dimensions":[{"dimension":"<feature being compared>",
                 "values_per_subject":["...","..."],
                 "notes":"<optional>"}],
  "summary":"<2-3 sentence overall takeaway>" }
```

### retardmax modifier
Append to the prompt: "Don't agonize over structure. Get the content down. More is better than less. Polish later." Skip the `--with` relevance filter. Allow more sections/slides than the type's defaults.

## Output
Type-specific JSON. Drives `render-output-markdown`.
