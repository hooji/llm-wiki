# generate-research-plan-paths

**Type**: LLM
**Used by**: `research --plan` Phase 1.5 (also works with question and thesis modes)

## Inputs
- topic / question / thesis
- existing-coverage summary from `scope-existing-knowledge`

## Prompt template
```
Decompose into 3-5 independent research paths. Each path must:
- have a clear, non-overlapping scope
- be searchable independently (no dependencies on other paths' findings)
- target a specific aspect (foundational, current state, applications,
  criticisms, adjacent connections)

Topic: "{topic_or_question_or_thesis}"
Existing coverage: {scope_summary}

Return JSON ONLY:

{
  "paths": [
    {
      "name": "Cryptographic foundations",
      "focus": "Shor's algorithm vs ECDLP, key sizes, quantum gate counts",
      "search_angles": ["shor algorithm elliptic curve", "quantum gate count ECDLP", "NIST PQC"],
      "target_sources": 4
    },
    ...
  ]
}
```

## Output
JSON as specified. Presented to user; on `y`, persisted into `.research-session.json` `paths[]`. Each path-agent runs its own internal Phase 2 swarm with prefixed filenames.
