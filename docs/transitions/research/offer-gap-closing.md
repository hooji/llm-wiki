# offer-gap-closing

**Type**: Tool
**Used by**: `research` (single-round and `--plan` mode) after Phase 5; **not** in `--min-time` rounds

## Inputs
- `remaining_gaps[]` from round report

## Prompt template
None.

## Procedure
If `remaining_gaps.length >= 2`, present a numbered menu:

```
### Close gaps?

Pick which gaps to research in parallel (all run at once):

1. Dose-response curves for red vs near-infrared wavelengths
2. Long-term safety data for daily exposure
3. Device comparison (clinical vs consumer panels)
...

Enter numbers (e.g. 1,2,4), "all", or "skip":
```

On selection, treat each picked gap as a path in a `--plan` dispatch and run a single round (no second confirmation; the user already chose). Inherit the current session's flags (`--deep`, `--wiki`, `--project`, etc).

## Output
None directly. Recursive call into a fresh research run with the chosen paths.

## Notes
`--min-time` rounds skip this offer because they have their own gap-to-round pipeline via reflection.
