# score-quality-tier-2

**Type**: LLM
**Used by**: `librarian` Pass 2 Tier 2 (escalated articles only)

## Escalation triggers
Run only when **any** is true:
- staleness score < threshold
- `volatility: hot`
- Tier-1 depth proxy = 1 or 2 (suspected stub)

## Inputs
- full article markdown
- frontmatter
- source `confidence:` fields

## Prompt template
```
You are scoring the quality of a wiki article.

Article (between fences):
```
{full_article_markdown}
```

Article frontmatter:
{frontmatter_yaml}

Source confidences (from raw frontmatter): {[high, medium, ...]}

Score on four dimensions (1-5 each):

1. Depth
   1 = single paragraph, no structure
   3 = multiple sections, covers key aspects
   5 = comprehensive treatment with nuance, examples, edge cases

2. Source quality
   1 = no sources or single low-confidence source
   3 = 2-3 sources, mixed confidence
   5 = 4+ high-confidence sources that corroborate

3. Coherence
   1 = disjointed, no logical flow
   3 = readable structure, minor gaps
   5 = clear narrative arc, smooth transitions, no logical gaps

4. Utility
   1 = trivial or obvious information
   3 = useful for understanding the topic
   5 = actionable for decision-making, includes tradeoffs and recommendations

Return JSON ONLY:

{
  "depth": 1-5,
  "source_quality": 1-5,
  "coherence": 1-5,
  "utility": 1-5,
  "flags": [
    "thin-coverage","single-source","low-confidence-sources",
    "no-see-also","stale","unverified"
  ],
  "rationale": "<one sentence>"
}
```

## Output
JSON as specified.

Composite quality:
```
quality_score = ((depth + source_quality + coherence + utility) / 4) * 20    // 20-100
```

Non-escalated articles use `coherence: 3, utility: 3` (adequate default) so token cost scales with problem density, not wiki size.
