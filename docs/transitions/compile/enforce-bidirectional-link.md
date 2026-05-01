# enforce-bidirectional-link

**Type**: Tool
**Used by**: `compile` Step 8, `research` reflection

## Inputs
- pair of articles: `(A, B)` with relationship phrase

## Prompt template
None.

## Procedure
For each See-Also link from A → B:
1. If B does not exist → queue B for creation in this same compile pass (recurse via plan-article).
2. Else read B. Check whether B already has a See-Also entry pointing to A.
3. If not → Edit B to insert the dual-link reciprocal:
   ```
   [[A-slug|A title]] ([A title](../<cat>/A-slug.md)) — <relationship>
   ```

## Output
None (file mutations on disk).

## Notes
Bidirectionality is the lint C4 invariant; enforcing it inline at compile time means lint has nothing to fix later.
