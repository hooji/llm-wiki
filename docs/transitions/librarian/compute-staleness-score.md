# compute-staleness-score

**Type**: Tool
**Used by**: `librarian` Pass 1 (per article), `audit` (when reusing librarian)

## Inputs
- article frontmatter (`volatility`, `verified`, `updated`, `created`, `sources`, `confidence`)
- raw source frontmatter (`ingested`) for each entry in `sources:`
- wiki `freshness_threshold` (default 70)

## Prompt template
None.

## Procedure
```
volatility = fm.volatility ?? "warm"
half_life = {"hot":30, "warm":90, "cold":365}[volatility]

# Resolved sources
resolved = 0
oldestIngestedDays = []
for srcPath in fm.sources:
    if exists(srcPath):
        resolved++
        oldestIngestedDays.add(daysSince(readFm(srcPath).ingested))

source_freshness   = 25 * 0.5^(avg(oldestIngestedDays) / half_life)
verification_score = (verified == null) ? 0 : 25 * 0.5^(daysSince(verified) / half_life)
compilation_score  = 25 * 0.5^(daysSince(updated ?? created) / half_life)
integrity_score    = 25 * (resolved / max(1, fm.sources.size()))

staleness_score = source_freshness + verification_score + compilation_score + integrity_score
```

## Output
```json
{
  "score": <0-100>,
  "factors": {
    "source_freshness": <0-25>,
    "verification": <0-25>,
    "compilation": <0-25>,
    "integrity": <0-25>
  },
  "below_threshold": <bool>
}
```

## Notes
Fully deterministic — no LLM. Volatility-scaled decay matches the Lindy Effect: cold content surviving without updates is more durable, not less.
