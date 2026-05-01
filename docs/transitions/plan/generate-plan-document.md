# generate-plan-document

**Type**: LLM
**Used by**: `plan` Stage 5

## Inputs
- synthesized context (from `synthesize-plan-context`)
- format: `roadmap` (default) | `rfc` | `adr` | `spec`

## Prompt template
Common header for all formats:
```
You are writing an implementation plan grounded in a wiki knowledge base.

Goal: "{goal}"
Format: {roadmap|rfc|adr|spec}
Wiki context (synthesized):
{stage_4_context_json}

Citations: every architectural decision must cite a wiki article using
the dual-link format:
   [[slug|Display]] ([Display](../path/slug.md))
or the standard markdown link form for output:
   [Display](../../wiki/<category>/<slug>.md)
```

Then per format:

### roadmap
```
Return JSON ONLY:
{
  "executive_summary":"...",
  "architecture_decisions":[
    {"title":"...","context_from_wiki":[{"path":"...","contributes":"..."}],
     "options":[{"name":"Option A","description":"...","wiki_basis":"<path|null>"},...],
     "decision":"Option <X>","rationale":"...","consequences":"..."}
  ],
  "phases":[
    {"n":1,"title":"...","estimated_effort":"<S/M/L|days>",
     "goal":"...","tasks":["...","..."],"dependencies":["..."],
     "validation":"...",
     "wiki_grounding":[{"path":"...","says":"<phrase>"}]}
  ],
  "risks_table":[{"risk":"...","source":"<wiki path>","mitigation":"..."}],
  "open_questions":["..."],
  "sources_consulted":[{"path":"...","what_drawn":"..."}]
}
```

### rfc
```
Return JSON ONLY:
{
  "context_and_scope":"...",
  "goals":["..."],"non_goals":["..."],
  "design":{"overview":"...",
            "components":[{"name":"...","purpose":"...","wiki_basis":"<path|null>"}],
            "data_flows":["..."]},
  "alternatives_considered":[{"name":"...","why_not_chosen":"...","wiki_basis":"<path|null>"}],
  "cross_cutting":{"security":"...","performance":"...","backwards_compat":"..."}
}
```

### adr
```
Return JSON ONLY:
{
  "adrs":[
    {"id":1,"title":"<imperative title>","status":"Proposed",
     "context":"...","decision_drivers":["..."],
     "options":[{"name":"...","pros":["..."],"cons":["..."]}],
     "decision":"Option <X>","rationale":"...","consequences":"...",
     "more_information":[{"path":"...","what_it_informed":"..."}]}
  ]
}
```

### spec
```
Return JSON ONLY:
{
  "system_architecture":{"ascii_diagram":"...","components":[...]},
  "api_design":{"endpoints":[...],"data_models":[...]},
  "implementation_details":[{"area":"...","pattern":"...","wiki_basis":"<path>"}],
  "testing_strategy":{"unit":"...","integration":"...","e2e":"..."},
  "deployment":{"migration_path":"...","rollback_plan":"..."}
}
```

## Output
Format-specific JSON. Drives `render-plan-markdown`.
