# render-plan-markdown

**Type**: Tool
**Used by**: `plan` Stage 6 (before write)

## Inputs
- format-specific JSON from `generate-plan-document`
- chosen format

## Prompt template
None (deterministic templating).

## Procedure
Per-format, render the JSON into markdown using a fixed template. Examples:

### roadmap
```markdown
# Plan: {goal}

> Generated from [{wiki-name}]({path}) wiki ({N} articles consulted)

## Executive Summary
{executive_summary}

## Architecture Decisions

### Decision 1: {title}
**Context**: ...
**Options considered**:
- Option A: ... (per [...])
- Option B: ... (per [...])
**Decision**: {decision} because {rationale}
**Consequences**: ...

## Implementation Phases

### Phase 1: {title} (estimated effort: {estimated_effort})
**Goal**: ...
**Tasks**:
- [ ] Task 1
**Dependencies**: ...
**Validation**: ...
**Wiki grounding**: Based on [Article](path) which says...

## Risks & Mitigations
| Risk | Source | Mitigation |
|------|--------|------------|

## Open Questions
- ...

## Sources Consulted
- [Article 1](path) — what was drawn from it
```

### adr
One ADR per decision, MADR shape (status / context / decision drivers / options / decision outcome / consequences / more information).

### rfc
Google/Uber style: Context & Scope / Goals / Non-Goals / Design / Alternatives / Cross-Cutting.

### spec
Architecture / API / Data Model / Implementation / Testing / Deployment.

## Output
Markdown string.
