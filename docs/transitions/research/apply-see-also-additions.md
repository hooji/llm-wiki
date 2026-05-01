# apply-see-also-additions

**Type**: Tool
**Used by**: `research --min-time` Phase 6 (after `reflect-across-rounds`)

## Inputs
- list of `{from, to, relationship}` triples from reflection

## Prompt template
None.

## Procedure
For each triple `(A, B, relationship)`:
- Edit A to insert under `## See Also`:
  ```
  [[B-slug|B title]] ([B title](../<cat>/B-slug.md)) — <relationship>
  ```
- Run `enforce-bidirectional-link(A, B)` so the back-edge from B → A is also written.

## Output
None (file mutations).
