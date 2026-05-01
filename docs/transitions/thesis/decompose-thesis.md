# decompose-thesis

**Type**: LLM
**Used by**: `research --mode thesis` Phase 0

## Inputs
- the user's thesis statement (raw text)

## Prompt template
```
You are decomposing a thesis for a multi-agent investigation pipeline.

Thesis: "{thesis_statement}"

Return JSON ONLY:

{
  "core_claim": "<the central assertion in one sentence>",
  "key_variables": ["<var1>", "<var2>", "<var3>"],
  "testable_prediction": "<what would be true if the thesis is correct>",
  "falsification_criteria": "<what evidence would disprove it>",
  "scope_boundary": "<what is NOT part of this thesis — the bloat filter>",
  "sub_claims": [
    "<sub-claim 1>", "<sub-claim 2>", ...
  ]
}
```

## Output
JSON as specified. Presented to user for confirmation before any research.

## Notes
If `falsification_criteria` is empty/unfalsifiable, refuse to proceed and ask the user to refine the thesis.
