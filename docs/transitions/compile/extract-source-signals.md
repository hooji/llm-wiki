# extract-source-signals

**Type**: LLM (one call per source, parallelizable)
**Used by**: `compile` Step 3

## Inputs
- one raw source (frontmatter + body)

## Prompt template
```
You are extracting structured signals from a raw source for a knowledge
wiki compiler.

Source frontmatter:
{frontmatter_yaml}

Source body (between fences):
```
{body_markdown}
```

Return JSON ONLY:

{
  "key_concepts": [
    {
      "name": "<canonical name, Title Case>",
      "slug": "<lowercase-hyphenated>",
      "kind": "concept" | "topic" | "reference",
      "salience": 1 | 2 | 3 | 4 | 5,
      "summary": "<one sentence>"
    }, ...
  ],
  "key_facts": [
    "<short factual claim with no editorializing>", ...
  ],
  "relationships": [
    {"from": "<slug>", "kind": "is-a" | "part-of" | "contrasts-with" | "depends-on" | "created-by", "to": "<slug>"}
  ],
  "tags": ["<lowercase-hyphenated>", ...],
  "evidence_strength": "meta-analysis" | "rct" | "cohort" | "case" | "expert-opinion" | "anecdotal" | "documentation" | "primary-data",
  "volatility_suggestion": "hot" | "warm" | "cold",
  "rationale": "<one sentence on why this volatility>"
}

Concept classification:
- concept: bounded idea explainable in 1-3 pages
- topic: broader theme tying concepts together
- reference: curated list of resources/tools/links

Volatility:
- hot: product specs, pricing, current events, competitive landscape
- warm: best practices, framework comparisons, market analysis (default)
- cold: foundational concepts, historical events, mathematical proofs
```

## Output
JSON as specified. Persist in memory keyed by source path.
