# score-quality-tier-1

**Type**: Tool
**Used by**: `librarian` Pass 2 Tier 1 (every article)

## Inputs
- article frontmatter and file (size, headings count) — no body read
- raw source `confidence:` fields

## Prompt template
None.

## Procedure
```
source_count = fm.sources.size()
confidence_to_int = {"high":5, "medium":3, "low":1}
avg_source_confidence = average of source_confidences (0 if none)

source_quality_t1 = round(avg_source_confidence)
                  + (source_count >= 4 ? 1 : 0)
                  - (source_count == 1 ? 1 : 0)
                  clamp 1-5

words = wcWords(article)
headings = countMatch(article, /^## /)

if words < 200 or headings == 0:           depth_t1 = 1
elif words < 500 or headings <= 1:         depth_t1 = 2
elif words < 1000:                         depth_t1 = 3
elif headings >= 3 and words >= 1500:      depth_t1 = 4
else:                                      depth_t1 = 3

flags = []
if not has_section(article, "## See Also"): flags.add("no-see-also")
```

## Output
```json
{
  "depth": 1-5,
  "source_quality": 1-5,
  "flags": ["no-see-also", ...]
}
```

## Notes
Tier 1 is metadata-only. The orchestrator compares the result to staleness + volatility to decide whether to escalate to `score-quality-tier-2`.
