# Capability: Audit

> Answer the broader trust question. Reuse the librarian pass, trace outputs
> across `raw/`, `wiki/`, and `output/`, detect drift, inspect provenance,
> and do fresh research when local evidence is not enough.

If `/wiki:librarian` ([06-librarian.md](06-librarian.md)) keeps the `wiki/` layer in check,
`/wiki:audit` answers a broader question: **can the user trust the current
knowledge and outputs right now?** It is allowed to follow the evidence
wherever it leads — re-reading upstream sources, fetching fresh primary
material, and running targeted adversarial research when local evidence is
weak, stale, or contradictory.

## 1. Inputs

| Subcommand | Meaning |
|------------|---------|
| `scan` (default) | Run the umbrella audit |
| `report` | Display the latest `.audit/REPORT.md` |

| Flag | Meaning |
|------|---------|
| `--artifact <path>` | Audit one wiki article or output artifact |
| `--project <slug>` | Audit outputs in `output/projects/<slug>/` |
| `--wiki-only` | Only the wiki-content pass |
| `--outputs-only` | Skip the wiki pass; focus on outputs + dependency chains |
| `--quick` | Local-only, skip fresh web research unless necessary |
| `--fresh` | Ignore cached `.librarian/scan-results.json`; rerun librarian |
| `--wiki <name>` / `--local` | Standard wiki resolution |

## 2. Design principles

1. **Truth over thrift.** Start local; spend extra search/fetch effort when
   the truth requires it.
2. **One command, many passes.** Internally runs librarian, drift,
   provenance, and (when needed) fresh research.
3. **Read-only on knowledge artifacts.** Audit writes only `.audit/` and may
   reuse `.librarian/`. Never rewrites wiki or output content during a scan.
4. **Adversarial verification.** For material findings, run *both* a
   confirming and a disproving query.
5. **Explicit unresolved states.** `unresolved` is better than false
   confidence.

## 3. Workflow

### Step 0 (deterministic) — Init scope

1. Resolve hub + wiki.
2. `mkdir -p <wiki>/.audit/`.
3. Derive scope:
   - `--artifact` → just that file.
   - `--project <slug>` → markdown deliverables under
     `output/projects/<slug>/` excluding `WHY.md`.
   - `--wiki-only` → all wiki articles.
   - `--outputs-only` → markdown outputs under `output/`, excluding
     `_index.md` and `WHY.md`.
   - default → full umbrella.
4. Append `audit_started` to `.session-events.jsonl` with scope, flags,
   timestamp.
5. Write initial `.session-checkpoint.json` with
   `command:"audit", status:"in_progress"`.

### Pass 1 (mixed) — Wiki content

Skip if `--outputs-only` and the targeted artifact has no wiki
dependencies.

1. If `.librarian/scan-results.json` exists and is recent and not `--fresh`:
   reuse it.
2. Otherwise run the librarian scan ([06-librarian.md](06-librarian.md)).
3. Pull forward the trust-relevant findings:
   - stale articles
   - low-quality articles
   - weak source chains (low-confidence sources, broken `sources:` refs)

These become `wiki_findings` in the audit JSON.

### Pass 2 (deterministic) — Output drift + dependency

Skip if `--wiki-only`.

For each output artifact in scope:

1. Read frontmatter: capture `sources:`, `generated:`, `project:`.
2. If `sources:` missing or empty → flag `missing-provenance`.
3. Resolve each dependency path:
   - `raw/...`, `wiki/...`, `output/...` resolve from the wiki root.
   - `../...` resolves relative to the artifact file.
4. If a dep doesn't resolve → flag `broken-source-ref`.
5. Compare dep dates against the artifact's `generated:` date:
   - If dep `updated:` / `ingested:` / `generated:` is **newer** → flag
     `drifted-dependency`.
6. If dep is a wiki article → inherit librarian findings:
   - stale upstream → `stale-upstream`
   - low-confidence/quality upstream → `weak-upstream`
7. If dep is another output artifact → recurse one hop into its
   `sources:` chain.

Classify each artifact into one of:

| Verdict | Trigger |
|---------|---------|
| `clean` | All deps resolve, none stale, none weak |
| `drifted` | At least one `drifted-dependency` |
| `provenance-gap` | `missing-provenance` or `broken-source-ref` |
| `weak-evidence` | Chain resolves but relies on stale/thin/low-confidence upstream |
| `contradicted` | Pass 3 fresh research disproved the artifact's claim |
| `unresolved` | Pass 3 ran but evidence didn't converge |

Append `audit_output_scan_completed` event with summary counts.

### Pass 3 (mixed, parallel agents) — Truth escalation

This is what makes audit broader than librarian.

#### Escalation triggers

Escalate beyond local files when **any** is true:

- The user explicitly asked whether they can trust the artifact.
- An output is `drifted` or has `provenance-gap`.
- A cited wiki article is stale, weak, or contradictory.
- The source chain is thin AND the claim matters.
- The topic is `volatility: hot`.
- There are conflicting local claims that need external resolution.

Skip Pass 3 entirely when `--quick` is set unless the user explicitly
demanded fresh verification.

#### Per escalated item

##### Step 3a (LLM) — Identify claims under scrutiny

```
You are identifying which specific claims need fresh verification.

Artifact: {artifact_path}
Artifact body (relevant excerpt):
"{excerpt}"

Triggers fired: ["drifted-dependency", "stale-upstream", ...]

Return JSON ONLY:

{
  "claims": [
    {
      "id": 1,
      "text": "<the specific claim, quoted or paraphrased>",
      "type": "factual" | "causal" | "predictive" | "comparative",
      "supporting_sources_in_artifact": ["raw/...", "wiki/..."],
      "stake": "low" | "medium" | "high"      // how badly wrong-conclusion would hurt
    },
    ...
  ]
}
```

Prioritize `stake: high` claims first.

##### Step 3b (deterministic) — Re-read local deps

For each claim, re-read its cited raw sources and wiki dependencies. Note
which ones still exist and what they say.

##### Step 3c (deterministic) — Re-fetch primary material

If a raw source's `source:` URL is still live, refetch it. Compare to the
captured body in `raw/`. Note divergence as "upstream changed".

##### Step 3d (LLM × 2-3 in parallel) — Adversarial verification

For each claim, dispatch **two** (or three with primary-source branch)
parallel agents:

###### Support agent

```
You are an evidence-gathering agent assigned to CONFIRM this claim.

Claim: "{claim.text}"
Stake: {claim.stake}

Run 1-2 WebSearch queries oriented to confirming the claim. Use WebFetch
on top results. Prefer primary sources, official docs, papers, direct
evidence.

Return JSON ONLY:

{
  "agent_role": "support",
  "queries_run": ["..."],
  "findings": [
    {
      "url": "...",
      "title": "...",
      "type": "primary" | "secondary" | "tertiary",
      "evidence_strength": "meta-analysis" | "rct" | "cohort" | "documentation" | "case" | "expert-opinion" | "anecdotal",
      "snippet": "<short quote>",
      "supports_claim": true | false        // honesty: even a support agent can find counter-evidence
    }
  ]
}
```

###### Attack agent

```
You are an evidence-gathering agent assigned to BREAK or WEAKEN this claim.

Claim: "{claim.text}"

Run 1-2 WebSearch queries oriented to disconfirming the claim — failed
replications, counter-evidence, alternative explanations, contradictory
data, official corrections. Use WebFetch on top results.

Return JSON ONLY: same shape as support agent, but `supports_claim` may be
true if you find no counter-evidence (be honest).
```

###### Optional primary-source branch

Same shape, focus: "find the original primary source for this claim and
report what it actually says."

##### Step 3e (LLM) — Verdict per claim

```
You are reaching a truth verdict for a claim audited via parallel
support+attack research.

Claim: "{claim.text}"

Agent findings:
{
  "support":  [...],
  "attack":   [...],
  "primary":  [...]            // optional
}

Local evidence summary (the artifact's existing citations):
{...}

Reach a verdict from this rubric:
- supported:    clear preponderance of strong evidence in favor; opposing weak
- weakened:     supportive evidence still exists but is weaker than the artifact implies
- contradicted: clear preponderance of strong evidence against
- unresolved:   evidence does not converge; honest "we don't know"

Never hide mixed evidence behind a binary label.

Return JSON ONLY:

{
  "verdict": "supported" | "weakened" | "contradicted" | "unresolved",
  "confidence": "high" | "medium" | "low",
  "rationale_2_3_sentences": "...",
  "key_supporting_evidence": ["<url> — <one-liner>"],
  "key_opposing_evidence": ["<url> — <one-liner>"],
  "what_would_change_this": "<specific future evidence>",
  "recommended_action": "<one of: refresh source, retract claim, add caveat, no action, escalate to /wiki:research>"
}
```

Append `audit_truth_escalation_completed` event with counts of each
verdict.

### Pass 4 (deterministic) — Session provenance

Check for:

- `.session-events.jsonl`
- `.session-checkpoint.json`
- `.research-session.json`
- `.thesis-session.json`

Classify the wiki's provenance state:

| State | When |
|-------|------|
| `replayable` | `.session-events.jsonl` exists; session actions can be traced |
| `partial` | only `.session-checkpoint.json` exists |
| `missing` | no durable session artifacts |

Diagnostic, not punitive. Missing event logs are reported as a limitation,
not a content failure.

### Pass 4b (deterministic) — Maintain audit's own provenance

1. Append `audit_started` (already done in Step 0).
2. Append milestone events: `audit_output_scan_completed`,
   `audit_truth_escalation_completed`.
3. Refresh `.session-checkpoint.json` after each milestone with:
   ```json
   {
     "updated_at": "...",
     "command": "audit",
     "session_id": "...",
     "status": "in_progress",
     "scope": "full|...",
     "summary": {
       "wiki_findings": N,
       "outputs_scanned": N,
       "drifted_outputs": N,
       "research_escalations": N,
       "verdict_counts": {"supported":N,"weakened":N,"contradicted":N,"unresolved":N},
       "provenance_state": "replayable|partial|missing"
     },
     "artifacts": [
       {"path": ".audit/scan-results.json", "sha256": "..."},
       {"path": ".audit/REPORT.md",         "sha256": "..."}
     ]
   }
   ```
4. After reports are written, append `audit_completed` and refresh
   checkpoint one last time.
5. **Do not delete** `.session-events.jsonl` or `.session-checkpoint.json`
   on normal completion.

### Pass 5 (deterministic) — Write reports

1. `.audit/scan-results.json`:

   ```json
   {
     "audit_id": "2026-04-29T12:00:00Z",
     "scope": "full",
     "summary": {
       "wiki_findings": 3,
       "outputs_scanned": 8,
       "drifted_outputs": 2,
       "research_escalations": 4,
       "verdict_counts": {"supported":2,"weakened":1,"contradicted":0,"unresolved":1},
       "provenance_state": "partial"
     },
     "wiki": { /* selected librarian findings */ },
     "outputs": {
       "output/projects/<slug>/playbook.md": {
         "verdict": "drifted",
         "flags": ["drifted-dependency"],
         "drifted_deps": [{"path":"wiki/...md","reason":"updated 2026-04-25 > generated 2026-04-10"}],
         "claim_verdicts": [
           {"claim":"...","verdict":"weakened","confidence":"medium"}
         ]
       }
     },
     "investigations": [
       {"claim":"...","verdict":"contradicted","sources":[...],"action":"add caveat"}
     ],
     "provenance": {"state":"partial","files_present":[".session-checkpoint.json"]}
   }
   ```

2. `.audit/REPORT.md` — human-readable rendering of the same.

3. `.audit/log.md`:
   ```
   ## [YYYY-MM-DD] scan | scope=<scope>, outputs=N, drifted=M, escalations=K
   ```

4. wiki root `log.md`:
   ```
   ## [YYYY-MM-DD] audit | scope=<scope>, outputs=N, drifted=M, escalations=K
   ```

### Pass 6 (deterministic) — Present results

Lead with **trust verdicts**, not raw diagnostics. The summary should answer:

1. Can the user trust the targeted artifact or wiki right now?
2. What is drifted, contradicted, or unresolved?
3. What additional research did the audit perform?
4. What should the user do next?

Concrete next-step commands when helpful:

- `/wiki:librarian` for focused wiki maintenance.
- `/wiki:refresh <path>` for stale wiki articles.
- `/wiki:research ...` when the audit found a real knowledge gap.

## 4. `report` subcommand (deterministic)

```
if not exists(.audit/REPORT.md):
    "No audit report found. Run /wiki:audit first."
else:
    display .audit/REPORT.md
    note when audit ran (from scan-results.audit_id)
    note whether librarian was reused or refreshed
```

## 5. Output schemas summary

LLM-driven JSON shapes:

- **Step 3a**: claims to verify (text, stake, supporting deps).
- **Step 3d (×N)**: agent findings (one per support/attack/primary branch).
- **Step 3e**: per-claim verdict + recommended action.

Deterministic JSON shapes:

- **scan-results.json**: full audit dump.

## 6. Persistence summary

| File | Lifecycle |
|------|-----------|
| `.audit/scan-results.json` | Final, machine-readable |
| `.audit/REPORT.md` | Final, human-readable |
| `.audit/log.md` | Append-only |
| `.session-events.jsonl` | Append-only (durable, shared with research) |
| `.session-checkpoint.json` | Atomic write (durable, shared) |
| wiki root `log.md` | Append-only |

## 7. Edge cases

- **Network failure during fresh research**: agent returns empty findings.
  Verdict for that claim becomes `unresolved` with low confidence. Don't
  retry indefinitely.
- **Output references retracted source**: lint C4b or the deleted-file
  check in Pass 2 surfaces it as `broken-source-ref`. The output is
  flagged `provenance-gap`, escalated if stake is high.
- **Audit of a thesis verdict**: thesis files are wiki articles
  (`type: thesis`). They are audited like any wiki article; their
  evidence tables are dependency-chains for Pass 2.
- **Recursive output dependency cycle**: cap recursion at one hop into
  output→output chains. A cycle is a content defect; flag and stop.
- **`--artifact` points to non-existent path**: stop with
  `Artifact not found.` Don't create empty audit reports.
- **No wiki**: stop with `No wiki found. Run /wiki init first.`

## 8. Java reimplementation outline

```java
public class AuditService {
  public AuditResult audit(AuditRequest req) {
    WikiContext wiki = wikis.resolve(req);
    Path auditDir = wiki.root.resolve(".audit");
    fs.mkdirs(auditDir);

    AuditScope scope = AuditScope.derive(req);
    AuditCheckpoint cp = AuditCheckpoint.start(wiki, scope);
    appendEvent(wiki, "audit_started", cp);

    // Pass 1
    LibrarianResult lib = scope.includesWiki()
        ? librarian.scanOrReuse(wiki, req.fresh)
        : LibrarianResult.empty();

    // Pass 2
    Map<Path, OutputFinding> outputs = scope.includesOutputs()
        ? scanOutputs(wiki, scope)
        : Map.of();
    appendEvent(wiki, "audit_output_scan_completed", summary(outputs));

    // Pass 3
    List<Investigation> investigations = new ArrayList<>();
    for (var entry : outputs.entrySet()) {
      if (shouldEscalate(entry.getValue(), req.quick, lib)) {
        List<Claim> claims = identifyClaimsLLM(entry.getKey(), entry.getValue());
        for (Claim c : claims) {
          rereadDeps(c);
          maybeRefetchPrimaries(c);
          List<AgentFinding> support = agentLLM(c, "support");
          List<AgentFinding> attack  = agentLLM(c, "attack");
          ClaimVerdict v = verdictLLM(c, support, attack);
          investigations.add(new Investigation(c, v, support, attack));
        }
      }
    }
    appendEvent(wiki, "audit_truth_escalation_completed", summary(investigations));

    // Pass 4
    Provenance prov = classifyProvenance(wiki);

    // Pass 5
    AuditResult result = AuditResult.assemble(scope, lib, outputs, investigations, prov);
    fs.writeAtomic(auditDir.resolve("scan-results.json"), result.toJson());
    fs.writeAtomic(auditDir.resolve("REPORT.md"), renderMd(result));
    appendLog(auditDir.resolve("log.md"), "scan", result.summary());
    appendLog(wiki.root.resolve("log.md"), "audit", result.summary());
    appendEvent(wiki, "audit_completed", result.summary());
    refreshCheckpoint(wiki, result, "completed");
    return result;
  }
}
```
