# generalize-lessons

**Type**: LLM
**Used by**: `lessons` Step 2

## Inputs
- events from `scan-session-transcript`

## Prompt template
```
You are generalizing session events into reusable lessons.

Events:
{events_array_from_step_1}

For each lesson, produce:
- title: short imperative or descriptive title
- category: gotcha | pattern | rule | discovery | correction
- context: what was being done when this was learned
- symptom: error or failure (may be empty)
- root_cause: why it happened (may be empty)
- fix: what was done (may be empty for rules)
- rule: a generalizable principle, ONE SENTENCE, that applies beyond this case

DEDUPLICATE: if multiple events teach the same lesson, merge into one
with the clearest example.

GENERALIZE: the "rule" must be useful outside the specific session.
- Good: "Each AI tool needs its own nono profile extending that tool's built-in profile."
- Bad: "Add user_caches_macos to custom-codex profile."

BE SPECIFIC: include exact error messages, file paths, tool names where they
add precision.

Return JSON ONLY:

{
  "lessons":[
    {
      "title":"...",
      "category":"gotcha"|"pattern"|"rule"|"discovery"|"correction",
      "context":"...",
      "symptom":"...",
      "root_cause":"...",
      "fix":"...",
      "rule":"...",
      "tags":["..."]
    }
  ]
}
```

## Output
JSON as specified.
