# Capability: Lessons learned (`/wiki:ll`)

> Extract lessons learned from the current session — error→fix patterns, user
> corrections, discoveries. Saved as structured notes the wiki can query
> later. `--rules` emits enforceable rules instead of prose.

Lessons-learned captures knowledge that was **learned by doing**, not by
reading. The source material is the *current session transcript* — errors
hit, fixes discovered, corrections from the user, configuration changes,
gotchas, patterns that only emerged during implementation.

This differs from the other capture commands:

| Command | Direction |
|---------|-----------|
| `ingest` | external material in (URLs, files, text from outside) |
| `compile` | raw → synthesized articles |
| `ll` | internal session experience → structured knowledge |

## 1. Inputs

| Argument | Meaning |
|----------|---------|
| `<topic hint>` (positional, optional) | A phrase describing what the session was about. If omitted, infer from conversation context. |
| `--wiki <name>` | Target a specific topic wiki |
| `--local` | Use project-local `.wiki/` |
| `--dry-run` | Show extracted lessons without writing |
| `--rules` | Also suggest CLAUDE.md / AGENTS.md rule additions |

## 2. Workflow

### Step 0 (deterministic) — Hub + wiki resolution

Standard prelude. If no wiki matches the session topic and no `--wiki` is
specified, offer to create one with `--new-topic`.

### Step 1 (LLM) — Session scan

The session transcript itself is the input. The LLM runs over the transcript
once to identify lesson-worthy events.

**Prompt:**

```
You are extracting lessons learned from a coding/research session
transcript.

Session transcript:
{transcript_concatenation_user_and_assistant_turns}

Topic hint (optional): "{topic_hint_or_empty}"

Look for these signals, in priority order:

1. Error→Fix patterns
   Sequences where something failed, was diagnosed, and fixed. Extract:
   - symptom (the error message or failure behavior)
   - assumption (what was initially believed, if different from root cause)
   - root_cause (the actual problem)
   - fix (what was done to resolve it)

2. User corrections
   Moments where the user redirected the approach: "no, not that",
   "wrong profile", "use X instead", "that's the wrong file."

3. Discoveries
   Things that worked unexpectedly, or where the solution required
   non-obvious knowledge. The test: would this have been obvious to
   someone starting the same task?

4. Configuration changes
   Files created or modified during the session — especially dotfiles,
   settings, profiles, shell configs. Materialized decisions.

5. Gotchas & quirks
   Platform-specific behaviors, tool-specific edge cases, undocumented
   behaviors encountered. Things that would trip up the next person.

Return JSON ONLY:

{
  "session_summary": "<1-2 sentences on what the session was about>",
  "topic_keywords": ["..."],                  // 3-7 lowercase tags
  "events": [
    {
      "type": "error_fix" | "correction" | "discovery" | "config_change" | "gotcha",
      "context": "<what was being done>",
      "symptom": "<error/behavior, may be empty for non-error types>",
      "root_cause": "<why it happened, may be empty>",
      "fix": "<what was done>",
      "evidence_quote": "<a direct quote from the transcript demonstrating this event>"
    }
  ]
}
```

A typical session yields 2-7 events. If the LLM returns >10, the
extraction was too granular; if <2, look harder.

### Step 2 (LLM) — Lesson generalization + dedup

Take the events from Step 1 and turn them into structured lessons. Multiple
events may collapse into one lesson; the lesson must be **useful outside
this specific session**.

**Prompt:**

```
You are generalizing session events into reusable lessons.

Events:
{events_array_from_step_1}

For each lesson, produce:
- title: a short imperative or descriptive title
- category: gotcha | pattern | rule | discovery | correction
- context: what was being done when this was learned
- symptom: the error or failure (may be empty for discovery/pattern)
- root_cause: why it happened (may be empty)
- fix: what was done (may be empty for rules)
- rule: a generalizable principle, ONE SENTENCE, that applies beyond this case

DEDUPLICATE: If multiple events teach the same lesson, merge them into one
with the clearest example.

GENERALIZE: The "rule" must be useful outside the specific session.
- Good: "Each AI tool needs its own nono profile extending that tool's built-in profile."
- Bad: "Add user_caches_macos to custom-codex profile."

BE SPECIFIC: Include exact error messages, file paths, tool names where they
add precision. Vague lessons are useless.

Return JSON ONLY:

{
  "lessons": [
    {
      "title": "...",
      "category": "gotcha" | "pattern" | "rule" | "discovery" | "correction",
      "context": "...",
      "symptom": "...",
      "root_cause": "...",
      "fix": "...",
      "rule": "...",
      "tags": ["..."]                       // 2-5 tags from session topic + lesson specifics
    }
  ]
}
```

### Step 3 (LLM) — Wiki targeting

Pick the right topic wiki for these lessons.

```
Pick the best-matching topic wiki for these lessons.

Topic wikis (slug → description):
{wikis_json_descriptions}

Session keywords: {topic_keywords}
Lesson titles: [...]
Lesson tags: [...]

Return JSON ONLY:

{
  "best_match": "<slug or null>",
  "match_strength": "strong" | "weak" | "none",
  "alternatives": ["<slug>", ...],
  "suggest_new_wiki": null | "<proposed slug>",
  "reasoning": "<one sentence>"
}
```

Logic on the result:

- `--wiki` set → override; ignore the LLM's pick.
- `match_strength == "strong"` → use it.
- otherwise → present a numbered choice including alternatives,
  `suggest_new_wiki`, and "ask which".

### Step 4 (LLM, deterministic application) — Find related articles

For each lesson, grep the target wiki for related keywords (deterministic).
For each lesson with a hit, ask the LLM whether the article is relevant:

```
Decide whether this article is the right place to append a one-line
back-reference to a new lesson.

Lesson: {title} — {rule}
Article path: {path}
Article frontmatter: {fm}
Article body:
"""{body}"""

Return JSON ONLY:

{
  "should_append": true | false,
  "preferred_section": "<existing section heading or 'tail'>",
  "appended_text": "<exactly what to insert: a one-line bullet or short subsection, including the back-reference link to raw/notes/...md>"
}
```

Track these as `article_updates` for Step 5 / 6.

### Step 5 (deterministic) — Write the raw note

Path: `<wiki>/raw/notes/<YYYY-MM-DD>-ll-<slug>.md` where slug is derived
from the session summary.

Frontmatter:

```yaml
---
title: "Lessons Learned: {session_summary}"
type: notes
source: "session"
ingested: YYYY-MM-DD
tags: [lessons-learned, {topic_tags}]
summary: "{one-line summary of what was learned}"
# optional analytical fields:
session_topic: "{topic_hint_or_inferred}"
lesson_count: N
session_category: lessons-learned
confidence: high
---
```

Body (chunked writes — write skeleton + first lesson first, then Edit to
append remaining lessons):

```markdown
# Lessons Learned: {session topic}

> Extracted from session on YYYY-MM-DD. N lessons covering {brief scope}.

## Lesson 1: {title}

**Category**: {category}
**Context**: {context}
**Symptom**: {symptom}
**Root cause**: {root cause}
**Fix**: {fix}
**Rule**: {rule}

## Lesson 2: ...
```

Note: `type: notes` keeps lint C11 happy (the file lives in `raw/notes/`).
The `session_*` and `lesson_count` fields are extra-canonical metadata; lint
C13 tolerates unknown keys with a warning rather than rewriting them.

### Step 6 (deterministic) — Article back-references

For each entry in `article_updates`:

1. Read the article.
2. Use Edit to append the `appended_text` at the `preferred_section` (do
   NOT rewrite existing content). Common pattern:
   ```markdown
   ## Lessons from practice

   - {rule} ([note](../../raw/notes/<date>-ll-<slug>.md))
   ```
3. Update the article's `updated:` frontmatter to today.

If no article matched, skip — the lesson lives in `raw/notes/` and will be
integrated in the next `/wiki:compile`.

### Step 7 (LLM, only if `--rules`) — Rule suggestions

```
You are proposing CLAUDE.md / AGENTS.md rule additions from session
lessons.

For each lesson, decide:
- target: "global CLAUDE.md" | "project CLAUDE.md" | "AGENTS.md" | "none"
  - global: applies to all projects (rare, only for cross-project rules)
  - project: applies to this project (most common)
  - AGENTS.md: applies to non-Claude agents too (when the rule is
    runtime-neutral, e.g. file-system invariants)
  - none: too specific or already covered

Existing rule files (if available, between fences):
CLAUDE.md (project): ```{project_claude_md}```
CLAUDE.md (global, ~/.claude/CLAUDE.md): ```{global_claude_md_or_empty}```
AGENTS.md: ```{agents_md_or_empty}```

Lessons:
{lessons_array_from_step_2}

Return JSON ONLY:

{
  "proposals": [
    {
      "lesson_index": 0,
      "target": "project CLAUDE.md" | "global CLAUDE.md" | "AGENTS.md" | "none",
      "section": "<existing section heading to add the rule under>",
      "after_existing_rule": "<the existing rule line that the new line should follow, or null for end-of-section>",
      "exact_text_to_add": "- <imperative one-liner rule>",
      "why_useful": "<one phrase>"
    }
  ]
}
```

**Do NOT auto-edit** CLAUDE.md / AGENTS.md. Present each proposal with:

- the exact text to add
- where to add it (file, section, after which line)
- why it's useful

User approves each one individually.

### Step 8 (deterministic) — Indexes + log

1. Append to `raw/notes/_index.md` (best-effort).
2. Append to `raw/_index.md` (best-effort).
3. Append to master `_index.md` Recent Changes.
4. Append to `log.md`:
   ```
   ## [YYYY-MM-DD] ll | "{session topic}" → raw/notes/YYYY-MM-DD-ll-<slug>.md (N lessons, M articles updated)
   ```

### Step 9 (deterministic) — Report

To the user:

- Number of lessons extracted.
- Each lesson title + rule (one line each).
- Which articles were updated (if any).
- Suggested rules (if `--rules`), with approval prompts.

## 3. `--dry-run` behavior

After Steps 1-3, present the extracted lessons + the chosen wiki:

```
Would write to: <wiki>/raw/notes/<YYYY-MM-DD>-ll-<slug>.md (5 lessons)
Would update: wiki/concepts/nono-profiles.md
              wiki/concepts/codex-marketplace.md

Save these lessons? (y/n)
```

On `y`, run Steps 4-9. On `n`, exit without writing.

## 4. Output schemas summary

- **Step 1**: events[] (raw extracted moments).
- **Step 2**: lessons[] (generalized, dedup'd, ready for the note).
- **Step 3**: wiki targeting.
- **Step 4**: per-article should_append + appended_text.
- **Step 7**: proposals[] for rule additions.

## 5. Persistence summary

| File | Written by |
|------|------------|
| `raw/notes/<date>-ll-<slug>.md` | Step 5 (chunked) |
| Various `wiki/<cat>/*.md` | Step 6 (Edit-append only) |
| `raw/notes/_index.md`, `raw/_index.md`, master `_index.md` | Step 8 (best-effort) |
| `log.md` | Step 8 |

No durable session state. (Lessons-learned is a one-shot capture.)

## 6. Edge cases

- **Empty session / no events**: Step 1 returns `events: []`. Tell the
  user "No lesson-worthy events found in this session."
- **Session is itself a lesson run**: detect that the transcript already
  references `/wiki:ll`; refuse to recurse.
- **No matching wiki**: Step 3 returns `match_strength: "none"`. Offer
  "Create a new wiki named `<suggested slug>`?" before writing.
- **Lessons would update an article that doesn't exist yet**: Step 6 skips
  it — the lesson stays in `raw/notes/` and will be picked up by the next
  `/wiki:compile`.
- **`--rules` proposes a rule that conflicts with existing one**: the LLM
  prompt includes existing CLAUDE.md / AGENTS.md content; the proposal
  should mark `target: "none"` if redundant. The user is the final filter.
- **Transcript exceeds context window**: chunk by user/assistant turn pairs
  and run Step 1 multiple times; Step 2 deduplicates across chunks.

## 7. Java reimplementation outline

```java
public class LessonsService {
  public LessonsResult run(LessonsRequest req, String transcript) {
    WikiContext wiki = wikis.resolveOrAsk(req);

    EventsResult ev = scanSessionLLM(transcript, req.topicHint);   // Step 1
    if (ev.events.isEmpty()) return LessonsResult.empty();

    LessonsResult lessons = generalizeLLM(ev.events);              // Step 2
    Targeting tgt = chooseWikiLLM(lessons, wikis.list());          // Step 3
    wiki = wikis.applyTargeting(tgt, req);

    Map<Path, ArticleUpdate> updates = new HashMap<>();
    for (Lesson l : lessons.lessons) {
      List<Path> hits = grepRelated(wiki, l.tags);
      for (Path p : hits) {
        ArticleDecision d = relevanceLLM(l, p);                    // Step 4
        if (d.shouldAppend) updates.put(p, d.toUpdate(l));
      }
    }

    if (req.dryRun) return preview(lessons, wiki, updates);

    Path note = writeRawNoteChunked(wiki, lessons);                // Step 5
    for (var e : updates.entrySet()) {                             // Step 6
      fs.append(e.getKey(), e.getValue().appendedText, e.getValue().section);
      bumpUpdatedFrontmatter(e.getKey());
    }

    if (req.rules) {
      RulesProposals rp = proposeRulesLLM(lessons, claudeMd, agentsMd);  // Step 7
      promptUserToApply(rp);
    }

    bestEffort(() -> updateIndexesAndLog(wiki, note, lessons, updates));   // Step 8
    return LessonsResult.from(lessons, note, updates);
  }
}
```
