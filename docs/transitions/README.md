# Transition Catalog & Operation Mappings

This directory enumerates **every dataset-mutating step** llm-wiki performs as
a named **transition**. A transition takes inputs (current dataset state +
arguments), and produces outputs (new dataset state) via one of three
mechanisms:

| Type | Definition |
|------|-----------|
| **LLM** | A single LLM call with a prompt template; expected to return JSON. |
| **Agentic** | An agent task — the agent decides which sub-tools to invoke and runs its own loop until it returns a structured result. |
| **Tool** | A deterministic tool call (filesystem, parser, computation, web fetch with no agent loop). |

Each transition has its own file with: type, used-by, inputs, prompt template
(if any), and expected output. This README is the **operation map**: it walks
each user-facing operation through its sequence of transitions.

## Reading this catalog

- File path = transition name. `compile/extract-source-signals.md` is the
  transition `extract-source-signals` used by Compile.
- A transition can be used by multiple operations. Cross-references are
  documented in each file's "Used by" header.
- Transitions of type `Tool` are deterministic and cheap. Transitions of
  type `LLM` go through `LLMCall(prompt) → json`. Transitions of type
  `Agentic` are inner loops that themselves call other transitions
  (typically `web-search`, `web-fetch`, and zero or more LLM calls); they
  represent the harness's `Agent` dispatch.
- "Best-effort" transitions can fail or be skipped without corrupting state
  (the Derived Index Protocol heals indexes on next read).

## Transition index

### Common (used across capabilities)

| Transition | Type |
|-----------|------|
| [`common/resolve-hub`](common/resolve-hub.md) | Tool |
| [`common/resolve-wiki`](common/resolve-wiki.md) | Tool |
| [`common/init-hub`](common/init-hub.md) | Tool |
| [`common/init-topic-wiki`](common/init-topic-wiki.md) | Tool |
| [`common/stale-check-index`](common/stale-check-index.md) | Tool |
| [`common/rebuild-index`](common/rebuild-index.md) | Tool |
| [`common/append-activity-log`](common/append-activity-log.md) | Tool |
| [`common/append-session-event`](common/append-session-event.md) | Tool |
| [`common/refresh-session-checkpoint`](common/refresh-session-checkpoint.md) | Tool |
| [`common/write-ephemeral-session`](common/write-ephemeral-session.md) | Tool |
| [`common/delete-ephemeral-session`](common/delete-ephemeral-session.md) | Tool |
| [`common/structural-guardian-check`](common/structural-guardian-check.md) | Tool |
| [`common/web-search`](common/web-search.md) | Tool |
| [`common/web-fetch`](common/web-fetch.md) | Tool |

### Ingest

| Transition | Type |
|-----------|------|
| [`ingest/detect-source-kind`](ingest/detect-source-kind.md) | Tool |
| [`ingest/fetch-url-content`](ingest/fetch-url-content.md) | Tool |
| [`ingest/fetch-twitter-content`](ingest/fetch-twitter-content.md) | Tool |
| [`ingest/fetch-github-repo`](ingest/fetch-github-repo.md) | Tool |
| [`ingest/read-local-file`](ingest/read-local-file.md) | Tool |
| [`ingest/extract-source-metadata`](ingest/extract-source-metadata.md) | LLM |
| [`ingest/classify-topic-wiki`](ingest/classify-topic-wiki.md) | LLM |
| [`ingest/classify-inbox-batch`](ingest/classify-inbox-batch.md) | LLM |
| [`ingest/write-raw-source`](ingest/write-raw-source.md) | Tool |
| [`ingest/compilation-nudge`](ingest/compilation-nudge.md) | Tool |

### Ingest-collection

| Transition | Type |
|-----------|------|
| [`ingest-collection/detect-collection-adapter`](ingest-collection/detect-collection-adapter.md) | Tool |
| [`ingest-collection/inventory-git-collection`](ingest-collection/inventory-git-collection.md) | Tool |
| [`ingest-collection/inventory-mediawiki-dump`](ingest-collection/inventory-mediawiki-dump.md) | Tool |
| [`ingest-collection/inventory-mediawiki-api`](ingest-collection/inventory-mediawiki-api.md) | Tool |
| [`ingest-collection/dedup-collection-items`](ingest-collection/dedup-collection-items.md) | Tool |
| [`ingest-collection/fetch-collection-item`](ingest-collection/fetch-collection-item.md) | Tool |
| [`ingest-collection/summarize-collection-item`](ingest-collection/summarize-collection-item.md) | LLM |
| [`ingest-collection/write-collection-manifest`](ingest-collection/write-collection-manifest.md) | Tool |
| [`ingest-collection/write-collection-child`](ingest-collection/write-collection-child.md) | Tool |
| [`ingest-collection/rebuild-raw-indexes`](ingest-collection/rebuild-raw-indexes.md) | Tool |

### Compile

| Transition | Type |
|-----------|------|
| [`compile/placement-precheck`](compile/placement-precheck.md) | Tool |
| [`compile/survey-uncompiled-sources`](compile/survey-uncompiled-sources.md) | Tool |
| [`compile/extract-source-signals`](compile/extract-source-signals.md) | LLM |
| [`compile/map-concepts-to-articles`](compile/map-concepts-to-articles.md) | Tool |
| [`compile/plan-article`](compile/plan-article.md) | LLM |
| [`compile/write-article-skeleton`](compile/write-article-skeleton.md) | Tool |
| [`compile/write-article-section`](compile/write-article-section.md) | LLM |
| [`compile/enforce-bidirectional-link`](compile/enforce-bidirectional-link.md) | Tool |
| [`compile/update-output-projects-index`](compile/update-output-projects-index.md) | Tool |

### Query

| Transition | Type |
|-----------|------|
| [`query/pick-relevant-categories`](query/pick-relevant-categories.md) | LLM |
| [`query/pick-candidate-articles`](query/pick-candidate-articles.md) | LLM |
| [`query/answer-from-indexes`](query/answer-from-indexes.md) | LLM |
| [`query/synthesize-answer`](query/synthesize-answer.md) | LLM |
| [`query/rank-search-results`](query/rank-search-results.md) | LLM |
| [`query/detect-interrupted-session`](query/detect-interrupted-session.md) | Tool |
| [`query/read-durable-provenance`](query/read-durable-provenance.md) | Tool |
| [`query/build-resume-briefing`](query/build-resume-briefing.md) | LLM |

### Research

| Transition | Type |
|-----------|------|
| [`research/detect-input-mode`](research/detect-input-mode.md) | Tool |
| [`research/decompose-question`](research/decompose-question.md) | LLM |
| [`research/generate-research-plan-paths`](research/generate-research-plan-paths.md) | LLM |
| [`research/scope-existing-knowledge`](research/scope-existing-knowledge.md) | LLM |
| [`research/dispatch-research-agent`](research/dispatch-research-agent.md) | Agentic |
| [`research/score-source-credibility`](research/score-source-credibility.md) | LLM |
| [`research/dedup-source-list`](research/dedup-source-list.md) | Tool |
| [`research/select-top-sources`](research/select-top-sources.md) | Tool |
| [`research/generate-round-report`](research/generate-round-report.md) | LLM |
| [`research/evaluate-termination`](research/evaluate-termination.md) | Tool |
| [`research/reflect-across-rounds`](research/reflect-across-rounds.md) | LLM |
| [`research/apply-see-also-additions`](research/apply-see-also-additions.md) | Tool |
| [`research/generate-question-playbook`](research/generate-question-playbook.md) | LLM |
| [`research/offer-gap-closing`](research/offer-gap-closing.md) | Tool |

### Thesis

| Transition | Type |
|-----------|------|
| [`thesis/decompose-thesis`](thesis/decompose-thesis.md) | LLM |
| [`thesis/create-thesis-file`](thesis/create-thesis-file.md) | Tool |
| [`thesis/dispatch-thesis-agent`](thesis/dispatch-thesis-agent.md) | Agentic |
| [`thesis/select-anti-bias-roles`](thesis/select-anti-bias-roles.md) | Tool |
| [`thesis/update-thesis-evidence-tables`](thesis/update-thesis-evidence-tables.md) | LLM |
| [`thesis/render-thesis-verdict`](thesis/render-thesis-verdict.md) | LLM |
| [`thesis/apply-verdict-edit`](thesis/apply-verdict-edit.md) | Tool |

### Librarian

| Transition | Type |
|-----------|------|
| [`librarian/compute-staleness-score`](librarian/compute-staleness-score.md) | Tool |
| [`librarian/score-quality-tier-1`](librarian/score-quality-tier-1.md) | Tool |
| [`librarian/score-quality-tier-2`](librarian/score-quality-tier-2.md) | LLM |
| [`librarian/triage-stale-articles`](librarian/triage-stale-articles.md) | Tool |
| [`librarian/write-librarian-checkpoint`](librarian/write-librarian-checkpoint.md) | Tool |
| [`librarian/write-librarian-scan-results`](librarian/write-librarian-scan-results.md) | Tool |
| [`librarian/render-librarian-report`](librarian/render-librarian-report.md) | Tool |

### Audit

| Transition | Type |
|-----------|------|
| [`audit/derive-audit-scope`](audit/derive-audit-scope.md) | Tool |
| [`audit/reuse-or-rerun-librarian`](audit/reuse-or-rerun-librarian.md) | Tool |
| [`audit/scan-output-drift`](audit/scan-output-drift.md) | Tool |
| [`audit/classify-output-verdict`](audit/classify-output-verdict.md) | Tool |
| [`audit/identify-claims-under-scrutiny`](audit/identify-claims-under-scrutiny.md) | LLM |
| [`audit/reread-local-dependencies`](audit/reread-local-dependencies.md) | Tool |
| [`audit/refetch-primary-sources`](audit/refetch-primary-sources.md) | Tool |
| [`audit/dispatch-support-agent`](audit/dispatch-support-agent.md) | Agentic |
| [`audit/dispatch-attack-agent`](audit/dispatch-attack-agent.md) | Agentic |
| [`audit/dispatch-primary-source-agent`](audit/dispatch-primary-source-agent.md) | Agentic |
| [`audit/render-claim-verdict`](audit/render-claim-verdict.md) | LLM |
| [`audit/classify-provenance-state`](audit/classify-provenance-state.md) | Tool |
| [`audit/write-audit-reports`](audit/write-audit-reports.md) | Tool |

### Lessons

| Transition | Type |
|-----------|------|
| [`lessons/scan-session-transcript`](lessons/scan-session-transcript.md) | LLM |
| [`lessons/generalize-lessons`](lessons/generalize-lessons.md) | LLM |
| [`lessons/choose-target-wiki`](lessons/choose-target-wiki.md) | LLM |
| [`lessons/decide-article-backref`](lessons/decide-article-backref.md) | LLM |
| [`lessons/write-lessons-note`](lessons/write-lessons-note.md) | Tool |
| [`lessons/append-article-backreference`](lessons/append-article-backreference.md) | Tool |
| [`lessons/propose-rule-additions`](lessons/propose-rule-additions.md) | LLM |

### Plan

| Transition | Type |
|-----------|------|
| [`plan/assemble-wiki-context`](plan/assemble-wiki-context.md) | LLM |
| [`plan/generate-interview-questions`](plan/generate-interview-questions.md) | LLM |
| [`plan/frame-gap-search`](plan/frame-gap-search.md) | LLM |
| [`plan/extract-gap-finding`](plan/extract-gap-finding.md) | LLM |
| [`plan/synthesize-plan-context`](plan/synthesize-plan-context.md) | Tool |
| [`plan/generate-plan-document`](plan/generate-plan-document.md) | LLM |
| [`plan/render-plan-markdown`](plan/render-plan-markdown.md) | Tool |
| [`plan/write-plan-chunked`](plan/write-plan-chunked.md) | Tool |

### Output

| Transition | Type |
|-----------|------|
| [`output/gather-output-sources`](output/gather-output-sources.md) | Tool |
| [`output/pick-with-wiki-articles`](output/pick-with-wiki-articles.md) | LLM |
| [`output/generate-output-artifact`](output/generate-output-artifact.md) | LLM |
| [`output/render-output-markdown`](output/render-output-markdown.md) | Tool |
| [`output/write-output-chunked`](output/write-output-chunked.md) | Tool |

**Totals**: 112 transitions — 80 Tool, 27 LLM, 5 Agentic.

---

## Operation → transition sequences

Each operation below is named exactly as the user invokes it (`/wiki:foo`).
The "**[prelude]**" stands in for `resolve-hub` → `resolve-wiki`, which every
command runs first. The "**[finalize]**" tag stands in for
`structural-guardian-check` (best-effort) running after.

### `/wiki init <name>` (or `--new-topic` branch)

```
[prelude]
→ init-hub                                    (only if hub doesn't exist)
→ init-topic-wiki
→ append-activity-log
```

### `/wiki:ingest <url>` (single URL)

```
[prelude]
→ detect-source-kind
→ fetch-url-content                           (or fetch-github-repo / fetch-twitter-content per kind)
→ extract-source-metadata
→ classify-topic-wiki                         (only if hub-level + no --wiki)
→ write-raw-source
→ rebuild-index | (best-effort: append rows)
→ append-activity-log
→ compilation-nudge
[finalize]
```

### `/wiki:ingest <file>`

```
[prelude]
→ detect-source-kind
→ read-local-file
→ extract-source-metadata
→ classify-topic-wiki                         (only if hub-level + no --wiki)
→ write-raw-source
→ append-activity-log
→ compilation-nudge
[finalize]
```

### `/wiki:ingest "text"`

```
[prelude]
→ detect-source-kind
→ extract-source-metadata
→ classify-topic-wiki                         (only if hub-level + no --wiki)
→ write-raw-source
→ append-activity-log
→ compilation-nudge
[finalize]
```

### `/wiki:ingest --inbox`

```
[prelude]
→ for each inbox file:
    detect-source-kind
    fetch-url-content | read-local-file | fetch-twitter-content      (parallel)
    extract-source-metadata                                          (parallel)
→ classify-inbox-batch                        (single call over the whole batch)
→ for each item, in groups by target wiki:
    write-raw-source
→ rebuild-raw-indexes                         (batch rebuild)
→ append-activity-log
→ compilation-nudge
[finalize]
```

### `/wiki:ingest-collection <source>`

```
[prelude]
→ detect-collection-adapter
→ inventory-git-collection | inventory-mediawiki-dump | inventory-mediawiki-api
→ dedup-collection-items
→ for each item to ingest:
    fetch-collection-item
    summarize-collection-item                 (LLM, optional, batched)
    write-collection-child
→ write-collection-manifest
→ rebuild-raw-indexes
→ append-activity-log
[finalize]
(if --compile)
→ <full /wiki:compile sequence below>
```

### `/wiki:compile`

```
[prelude]
→ placement-precheck
→ survey-uncompiled-sources
(if no_op:) report and stop
→ for each source (parallel):
    extract-source-signals                    (LLM per source)
→ map-concepts-to-articles
→ for each article slug to create or update:
    plan-article                              (LLM)
    write-article-skeleton                    (skeleton write)
    for each section:
      write-article-section                   (LLM, sequential, chunked Edit-append)
    deterministic append: ## See Also, ## Sources sections
→ for each See-Also link:
    enforce-bidirectional-link
→ rebuild-index | best-effort row append      (per category index, master index)
→ update-output-projects-index                (if output/projects/ exists)
→ append-activity-log
[finalize]
```

### `/wiki:query <question>` (standard depth)

```
[prelude]
→ stale-check-index                           (master + category indexes)
→ pick-relevant-categories                    (LLM)
→ pick-candidate-articles                     (LLM)
read full article bodies + Grep terms         (deterministic read)
→ synthesize-answer                           (LLM)
→ append-activity-log
```

### `/wiki:query <question> --quick`

```
[prelude]
→ stale-check-index
→ pick-relevant-categories                    (LLM)
→ answer-from-indexes                         (LLM)
→ append-activity-log
```

### `/wiki:query <question> --deep`

```
[prelude]
→ stale-check-index                           (every category)
→ pick-candidate-articles                     (LLM, larger candidate set)
read all candidate articles + follow ALL See-Also (deterministic)
Grep wiki/ AND raw/ for terms                 (deterministic)
read sibling-wiki _index.md files             (deterministic)
→ synthesize-answer                           (LLM, with sibling overlap + with-wiki content)
→ append-activity-log
```

### `/wiki:query <terms> --list`

```
[prelude]
→ stale-check-index
index-scan + Grep                             (deterministic)
→ rank-search-results                         (LLM)
→ append-activity-log
```

### `/wiki:query --resume`

```
[prelude]
→ detect-interrupted-session
(if not found:) read-durable-provenance
read recent log.md entries                    (deterministic)
read master _index.md stats + last-3 articles (deterministic)
→ build-resume-briefing                       (LLM, small)
→ append-activity-log
(if a question was also passed: fall through to standard /wiki:query)
```

### `/wiki:research <topic>` (single round, topic mode)

```
[prelude]
→ detect-input-mode                           (returns TOPIC)
→ scope-existing-knowledge                    (LLM)         [Phase 1; skipped in --retardmax]
(parallel agent swarm — 5/8/10 agents)
→ dispatch-research-agent                     (Agentic, ×N)
→ dedup-source-list
→ for each surviving source (parallel):
    score-source-credibility                  (LLM)
→ select-top-sources
→ for each top source:
    <ingest sub-sequence: extract-source-metadata + write-raw-source + append-activity-log>
→ <full /wiki:compile sequence>
→ generate-round-report                       (LLM)
→ offer-gap-closing                           (only if 2+ remaining gaps)
→ append-activity-log
[finalize]
```

### `/wiki:research <question>` (question mode)

```
[prelude]
→ detect-input-mode                           (returns QUESTION)
→ decompose-question                          (LLM)
present sub-questions to user                 (deterministic)
→ scope-existing-knowledge                    (LLM)
(parallel — one agent per sub-question)
→ dispatch-research-agent                     (Agentic, ×N)
→ dedup-source-list
→ score-source-credibility                    (LLM, ×M)
→ select-top-sources
→ <ingest each + compile>
→ generate-question-playbook                  (LLM)
write playbook chunked                        (deterministic)
→ generate-round-report                       (LLM)
→ offer-gap-closing
→ append-activity-log
[finalize]
```

### `/wiki:research --plan <topic>`

```
[prelude]
→ detect-input-mode
→ scope-existing-knowledge                    (LLM)
→ generate-research-plan-paths                (LLM)
present plan to user, wait for y/edit/n       (deterministic)
→ write-ephemeral-session                     (mode=plan, paths populated)
(parallel across paths)
→ dispatch-research-agent                     (Agentic, ×paths × 5/8/10 sub-agents)
→ dedup-source-list                           (single global dedup across paths)
→ score-source-credibility                    (LLM ×M)
→ select-top-sources
→ <ingest all in parallel>
→ <single /wiki:compile pass over all new sources — sees full picture>
→ generate-round-report                       (LLM)
→ offer-gap-closing
→ delete-ephemeral-session
→ append-activity-log
[finalize]
```

### `/wiki:research --min-time <duration>`

```
[prelude]
→ detect-input-mode
→ write-ephemeral-session                     (single mode)
→ append-session-event                        (research_started)
→ refresh-session-checkpoint                  (initial)
loop:
    → scope-existing-knowledge                (LLM, Round 1 only; subsequent rounds use prior reflection's gaps)
    → dispatch-research-agent                 (Agentic, ×N parallel)
    → dedup-source-list
    → score-source-credibility                (LLM ×M)
    → select-top-sources
    → <ingest each + compile>
    → generate-round-report                   (LLM)
    → write-ephemeral-session                 (update rounds_completed[])
    → append-session-event                    (research_round_completed)
    → refresh-session-checkpoint
    → reflect-across-rounds                   (LLM)
    → apply-see-also-additions
    → append-session-event                    (research_reflection_completed)
    → evaluate-termination                    (Tool)
    if STOP: break
end loop
→ append-session-event                        (research_completed)
→ refresh-session-checkpoint                  (final, status:completed)
→ delete-ephemeral-session
→ append-activity-log
[finalize]
```

### `/wiki:research --mode thesis "<claim>"`

```
[prelude]
→ detect-input-mode                           (returns THESIS)
→ decompose-thesis                            (LLM)
present decomposition; wait for y             (deterministic)
→ create-thesis-file
(if --min-time:) → write-ephemeral-session    (.thesis-session.json)
                  → append-session-event      (research_started, mode=thesis)
                  → refresh-session-checkpoint
loop:
    (Round 1: 5 lenses; Round 2+: anti-bias)
    → select-anti-bias-roles                  (Round 2+ only)
    → dispatch-thesis-agent                   (Agentic, ×roles in parallel)
    → dedup-source-list
    → score-source-credibility                (LLM ×M)
    → select-top-sources
    → <ingest each + compile (standard)>
    → update-thesis-evidence-tables           (LLM, edits the thesis file)
    → generate-round-report                   (LLM)
    (if --min-time:)
       → write-ephemeral-session
       → append-session-event                 (round_completed)
       → refresh-session-checkpoint
       → reflect-across-rounds                (LLM)
       → apply-see-also-additions
       → evaluate-termination
       if STOP: break
    else: break
end loop
→ render-thesis-verdict                       (LLM)
→ apply-verdict-edit
(if --min-time:) → append-session-event       (research_completed)
                  → refresh-session-checkpoint (final)
                  → delete-ephemeral-session
→ append-activity-log
[finalize]
```

### `/wiki:librarian scan`

```
[prelude]
mkdir .librarian/                             (deterministic)
build article list                            (deterministic)
detect existing checkpoint.json               (deterministic, may resume)
loop per article:
    → compute-staleness-score                 (Tool)
    → score-quality-tier-1                    (Tool)
    if escalation triggered:
        → score-quality-tier-2                (LLM)
    → write-librarian-checkpoint              (atomic, after each article)
end loop
→ triage-stale-articles
present triage; on user yes per article:
    delegate to /wiki:refresh                 (separate command flow)
    OR bump verified: to today                (deterministic Edit)
→ write-librarian-scan-results
→ render-librarian-report
delete .librarian/checkpoint.json             (deterministic)
→ append-activity-log                         (.librarian/log.md AND wiki log.md)
[finalize]
```

### `/wiki:librarian report`

```
[prelude]
read .librarian/REPORT.md                     (deterministic)
display                                       (deterministic)
```

### `/wiki:audit scan`

```
[prelude]
→ derive-audit-scope
→ append-session-event                        (audit_started)
→ refresh-session-checkpoint                  (status:in_progress)

(Pass 1) — skipped on --outputs-only without wiki deps
→ reuse-or-rerun-librarian                    (may invoke entire librarian flow)

(Pass 2) — skipped on --wiki-only
→ scan-output-drift
→ classify-output-verdict                     (initial; refined after Pass 3)
→ append-session-event                        (audit_output_scan_completed)
→ refresh-session-checkpoint

(Pass 3) — escalation
for each escalated artifact:
    → identify-claims-under-scrutiny          (LLM)
    for each claim (highest stake first):
        → reread-local-dependencies
        → refetch-primary-sources             (skipped under --quick)
        (parallel)
        → dispatch-support-agent              (Agentic)
        → dispatch-attack-agent               (Agentic)
        → dispatch-primary-source-agent       (Agentic, optional, stake:high)
        → render-claim-verdict                (LLM)
→ classify-output-verdict                     (re-run with truth verdicts)
→ append-session-event                        (audit_truth_escalation_completed)
→ refresh-session-checkpoint

(Pass 4) — provenance
→ classify-provenance-state

(Pass 5) — write reports
→ write-audit-reports                         (.audit/scan-results.json + REPORT.md + .audit/log.md)
→ append-activity-log                         (wiki root log.md)
→ append-session-event                        (audit_completed)
→ refresh-session-checkpoint                  (final, status:completed)
[finalize]
```

### `/wiki:audit report`

```
[prelude]
read .audit/REPORT.md                         (deterministic)
display                                       (deterministic)
```

### `/wiki:ll [--rules] [--dry-run]`

```
[prelude]
→ scan-session-transcript                     (LLM, may chunk for long sessions)
→ generalize-lessons                          (LLM)
→ choose-target-wiki                          (LLM, only if --wiki not set)
for each lesson:
    Grep wiki/ for related terms              (deterministic)
    for each grep hit:
        → decide-article-backref              (LLM)
(if --dry-run) present; abort or continue
→ write-lessons-note                          (chunked: skeleton + Edit-append per lesson)
for each article-update entry:
    → append-article-backreference
(if --rules)
    → propose-rule-additions                  (LLM; presented for user approval, not auto-applied)
→ append-activity-log
[finalize]
```

### `/wiki:plan <goal>`

```
[prelude]
read master + category indexes; Grep + read articles; sibling _index peek
load --with wiki articles (if any)            (deterministic, may use pick-with-wiki-articles)
→ assemble-wiki-context                       (LLM)
present context summary                       (deterministic)

(Stage 2 — skipped if --no-interview or --quick)
→ generate-interview-questions                (LLM)
wait for user free-text answers               (deterministic)

(Stage 3 — skipped if --no-research or --quick)
for each knowledge_gap:
    → frame-gap-search                        (LLM)
    web-search per query                      (Tool)
    web-fetch per top hit                     (Tool)
    → extract-gap-finding                     (LLM ×hits)
    if ingest_into_wiki: <ingest sub-sequence>

→ synthesize-plan-context
→ generate-plan-document                      (LLM, format-specific)
→ render-plan-markdown
→ write-plan-chunked
→ rebuild-index | best-effort row append      (output/_index.md, master)
→ append-activity-log
[finalize]
```

### `/wiki:output <type>`

```
[prelude]
→ gather-output-sources
for each --with wiki:
    → pick-with-wiki-articles                 (LLM)
    read picked articles                      (deterministic)
→ generate-output-artifact                    (LLM, type-specific)
→ render-output-markdown
→ write-output-chunked
→ rebuild-index | best-effort row append      (output/_index.md, master)
→ update-output-projects-index                (if output/projects/ exists)
→ append-activity-log
[finalize]
```

---

## Composition patterns

A few sub-sequences appear so often they're worth naming:

### "ingest sub-sequence"
Used by `/wiki:ingest` (single), `/wiki:research` Phase 3, `/wiki:plan` Stage 3 ingestion bonus, `/wiki:ingest-collection` per-child:
```
extract-source-metadata           (LLM, only if not already extracted)
write-raw-source                  (Tool)
append rows to indexes            (Tool, best-effort)
append-activity-log               (Tool)
```

### "compile sub-sequence"
Used by `/wiki:compile`, `/wiki:research` Phase 4, `/wiki:research --plan` (single global pass), `/wiki:research --mode thesis` Phase 4 (followed by thesis-specific edits):
```
placement-precheck → survey-uncompiled-sources →
  per source: extract-source-signals →
  map-concepts-to-articles →
  per article: plan-article → write-article-skeleton → write-article-section ×N →
  enforce-bidirectional-link ×N →
  update-output-projects-index → append-activity-log
```

### "session-bracketed loop"
Used by `/wiki:research --min-time`, `/wiki:research --mode thesis --min-time`, `/wiki:audit`:
```
write-ephemeral-session (start) → append-session-event (start) → refresh-session-checkpoint (initial)
... per round/pass ...
   <round body>
   write-ephemeral-session (update) → append-session-event (round) → refresh-session-checkpoint
   (research only) reflect-across-rounds → apply-see-also-additions → append-session-event
   evaluate-termination → maybe break
... end loop ...
append-session-event (completed) → refresh-session-checkpoint (final, status:completed)
delete-ephemeral-session
```

### "agent swarm with credibility filter"
Used by `/wiki:research` Phase 2/2b, `/wiki:research --mode thesis` Phase 2/2b:
```
parallel ×N: dispatch-research-agent | dispatch-thesis-agent
dedup-source-list →
  per surviving source (parallel): score-source-credibility →
  select-top-sources
```

### "adversarial verification"
Used by `/wiki:audit` Pass 3:
```
identify-claims-under-scrutiny →
  per claim:
    reread-local-dependencies →
    refetch-primary-sources →
    parallel: dispatch-support-agent + dispatch-attack-agent + (optional) dispatch-primary-source-agent →
    render-claim-verdict
```

---

## Java reimplementation cheat sheet

Every transition lives behind one of three driver methods:

```java
String json = LLMCall(String prompt);                     // for type=LLM
AgentResult agent = AgentRun(String role, AgentContext);  // for type=Agentic (inner loop)
T result = tool.run(args);                                // for type=Tool
```

The catalog above is the complete inventory. Operations are pure compositions
of these transitions plus deterministic control flow (loops, branches,
parallel fan-out). No transition not listed here is needed to implement any
operation; conversely, every transition listed here is needed to implement at
least one operation.

The agentic transitions (`dispatch-research-agent`, `dispatch-thesis-agent`,
`dispatch-support-agent`, `dispatch-attack-agent`,
`dispatch-primary-source-agent`) are inner loops that themselves call
`web-search`, `web-fetch`, and zero or more LLM calls. Each one is documented
as a single transition because from the orchestrator's perspective it has a
fixed input (role + topic/claim) and a fixed JSON output shape. In a Java
reimplementation, write each as a class with its own internal pipeline.
