# propose-rule-additions

**Type**: LLM
**Used by**: `lessons --rules` Step 7

## Inputs
- lessons array
- existing CLAUDE.md (project + global if available)
- existing AGENTS.md if available

## Prompt template
```
You are proposing CLAUDE.md / AGENTS.md rule additions from session
lessons.

For each lesson, decide:
- target: "global CLAUDE.md" | "project CLAUDE.md" | "AGENTS.md" | "none"
  - global: applies to all projects (rare, only for cross-project rules)
  - project: applies to this project (most common)
  - AGENTS.md: applies to non-Claude agents too (when runtime-neutral)
  - none: too specific or already covered

Existing rule files (if available, between fences):
CLAUDE.md (project): ```{project_claude_md}```
CLAUDE.md (global): ```{global_claude_md_or_empty}```
AGENTS.md: ```{agents_md_or_empty}```

Lessons:
{lessons_array}

Return JSON ONLY:

{
  "proposals":[
    {
      "lesson_index":0,
      "target":"project CLAUDE.md"|"global CLAUDE.md"|"AGENTS.md"|"none",
      "section":"<existing section heading to add the rule under>",
      "after_existing_rule":"<the existing rule line that the new line should follow, or null>",
      "exact_text_to_add":"- <imperative one-liner rule>",
      "why_useful":"<one phrase>"
    }
  ]
}
```

## Output
JSON as specified.

## Notes
Do **not** auto-edit CLAUDE.md / AGENTS.md. Present each proposal with target / section / exact text / rationale, and let the user approve each one individually.
