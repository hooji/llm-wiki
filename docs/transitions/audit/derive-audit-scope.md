# derive-audit-scope

**Type**: Tool
**Used by**: `audit` Step 0

## Inputs
- flags (`--artifact <path>`, `--project <slug>`, `--wiki-only`, `--outputs-only`, `--quick`, `--fresh`)

## Prompt template
None.

## Procedure
```
if --artifact:        scope = JUST(artifact)
elif --project <slug>: scope = OUTPUTS_IN("output/projects/<slug>/", exclude=[WHY.md])
elif --wiki-only:     scope = ALL_WIKI_ARTICLES
elif --outputs-only:  scope = ALL_OUTPUTS (exclude _index.md, WHY.md)
else:                 scope = FULL_UMBRELLA
```

## Output
`{ "scope": "...", "include_wiki_pass": <bool>, "include_outputs_pass": <bool>, "include_truth_pass": <bool>, "targets": [...] }`
