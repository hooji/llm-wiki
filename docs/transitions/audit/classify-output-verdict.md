# classify-output-verdict

**Type**: Tool
**Used by**: `audit` Pass 2 (after `scan-output-drift`)

## Inputs
- per-artifact drift findings
- truth verdicts from Pass 3 (when run)

## Prompt template
None.

## Procedure
Map flags to a single verdict per artifact (highest-severity wins):

| Verdict | Trigger |
|---------|---------|
| `clean` | All deps resolve, none stale, none weak |
| `drifted` | At least one `drifted-dependency` |
| `provenance-gap` | `missing-provenance` or `broken-source-ref` |
| `weak-evidence` | Chain resolves but relies on stale/thin/low-confidence upstream |
| `contradicted` | Pass 3 fresh research disproved a key claim |
| `unresolved` | Pass 3 ran but evidence didn't converge |

## Output
```json
{
  "verdicts": [
    {"path":"...","verdict":"drifted"|"clean"|...,"flags":[...]}
  ]
}
```
