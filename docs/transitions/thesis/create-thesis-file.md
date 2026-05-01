# create-thesis-file

**Type**: Tool
**Used by**: `research --mode thesis` setup (after `decompose-thesis`)

## Inputs
- thesis statement
- decomposition output (`core_claim`, `key_variables`, `falsification_criteria`)
- target wiki

## Prompt template
None.

## Procedure
Write `<wiki>/wiki/theses/<slug>.md`:

```yaml
---
title: "Thesis: {thesis_statement}"
type: thesis
status: investigating
created: <today>
updated: <today>
verdict: pending
confidence: pending
core_claim: "{core_claim}"
key_variables: [{key_variables}]
falsification: "{falsification_criteria}"
---

# Thesis: {thesis_statement}

## Core Claim
{core_claim}

## Key Variables
- {var1}
- {var2}

## Testable Prediction
{testable_prediction}

## Falsification Criteria
{falsification_criteria}

## Evidence For
(populated during research)

## Evidence Against
(populated during research)

## Nuances & Caveats
(populated during research)

## Verdict
**Status**: Investigating
```

## Output
Path of the thesis file.

## Notes
Lint C11 placement rule (`type: thesis` → `wiki/theses/`) keeps this file in the right place. Edited by `update-thesis-evidence-tables` and `apply-verdict-edit`; never overwritten.
