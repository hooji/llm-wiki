package net.llmwiki.core;

/**
 * Prompt templates lifted verbatim from the architecture docs in
 * docs/architecture/ and docs/transitions/.
 *
 * Use {placeholder} syntax (the same as Templates.expand). Placeholders here
 * are filled in by the capability code before each LLM call.
 *
 * Each constant is named after its transition:
 *   <transition-name> in docs/transitions/  ->  TRANSITION_NAME constant.
 *
 * Keeping all prompts here makes them easy to A/B and trivial to inspect.
 */
public final class Prompts {
  private Prompts() {}

  // ----- ingest -----

  public static final String EXTRACT_SOURCE_METADATA = """
      You are extracting structured metadata for a knowledge-base raw source.

      Content (between fences):
      ```
      {content_markdown}
      ```

      Return JSON ONLY with this exact shape:

      {
        "title": "<short descriptive title, max 80 chars>",
        "summary": "<2-3 sentence factual summary; no marketing language>",
        "tags": ["<lowercase-hyphenated-tag>", ...],
        "type_suggestion": "articles" | "papers" | "repos" | "notes" | "data",
        "authors": ["..."]
      }

      Tag rules:
      - lowercase, hyphen-separated
      - specific over general (good: "self-attention"; bad: "ml")
      - 3-7 tags total
      - no near-duplicates ("nlp" vs "natural-language-processing")
      """;

  public static final String FETCH_URL_EXTRACTION = """
      Extract the complete article content from this page. Return: title,
      author(s) if listed, date published if listed, and the full article text
      preserving all factual claims, data points, code examples, and technical
      details. Format as clean markdown.

      Page (between fences):
      ```
      {content}
      ```

      Return JSON ONLY:
      {
        "title": "...",
        "authors": ["..."],
        "published": "YYYY-MM-DD" | null,
        "content_markdown": "..."
      }
      """;

  public static final String CLASSIFY_TOPIC_WIKI = """
      You are routing a new source into the best-matching topic wiki.

      Source:
        title: {title}
        summary: {summary}
        tags: {tags}

      Topic wikis (slug -> description):
      {wikis_json}

      Return JSON:
      {
        "best_match": "<slug or null>",
        "match_strength": "strong" | "weak" | "none",
        "reasoning": "<one sentence>",
        "alternatives": ["<slug>", "..."]
      }
      """;

  // ----- compile -----

  public static final String EXTRACT_SOURCE_SIGNALS = """
      You are extracting structured signals from a raw source for a knowledge
      wiki compiler.

      Source frontmatter:
      {frontmatter}

      Source body (between fences):
      ```
      {body}
      ```

      Return JSON ONLY:

      {
        "key_concepts": [
          {
            "name": "<canonical name, Title Case>",
            "slug": "<lowercase-hyphenated>",
            "kind": "concept" | "topic" | "reference",
            "salience": 1,
            "summary": "<one sentence>"
          }
        ],
        "key_facts": ["..."],
        "relationships": [
          {"from":"<slug>","kind":"is-a|part-of|contrasts-with|depends-on|created-by","to":"<slug>"}
        ],
        "tags": ["<lowercase-hyphenated>"],
        "evidence_strength": "meta-analysis" | "rct" | "cohort" | "case" | "expert-opinion" | "anecdotal" | "documentation" | "primary-data",
        "volatility_suggestion": "hot" | "warm" | "cold",
        "rationale": "<one sentence on why this volatility>"
      }

      Concept classification:
      - concept: bounded idea explainable in 1-3 pages
      - topic: broader theme tying concepts together
      - reference: curated list of resources/tools/links

      Volatility:
      - hot: product specs, pricing, current events
      - warm: best practices, framework comparisons (default)
      - cold: foundational concepts, history, math
      """;

  public static final String PLAN_ARTICLE = """
      You are planning a new wiki article for an LLM-compiled knowledge base.

      Article slug: {slug}
      Article kind: {kind}

      Sources contributing to this article:
      {sources_json}

      Existing related articles in the wiki:
      {related_json}

      Return JSON ONLY:

      {
        "title": "<title case display name>",
        "aliases": ["alternate name"],
        "summary": "<2-3 sentence summary for index>",
        "tags": ["..."],
        "confidence": "high" | "medium" | "low",
        "confidence_rationale": "<one sentence>",
        "volatility": "hot" | "warm" | "cold",
        "abstract_paragraph": "<single paragraph>",
        "sections": [
          {"heading": "## Background", "intent": "what to cover, 1-3 sentences"}
        ],
        "see_also": [
          {"slug": "<existing or to-be-created>", "relationship": "<one phrase>"}
        ],
        "source_attributions": [
          {"path": "raw/papers/...md", "what_it_contributed": "<one phrase>"}
        ]
      }
      """;

  public static final String WRITE_ARTICLE_SECTION = """
      You are writing a single section of a wiki article.

      Article: {title}
      Section heading: {heading}
      Section intent: {intent}

      Source extracts available for this section:
      {extracts_json}

      Constraints:
      - Synthesize. Do NOT copy-paste.
      - Self-contained: a reader should not need to consult the raw sources.
      - Be specific. Include data points, mechanisms, examples.
      - Note honest disagreement when sources disagree.
      - When referencing another wiki article inline, use dual-link format:
          [[other-slug|Name]] ([Name](../<category>/other-slug.md))
      - No marketing language.

      Return JSON ONLY:
      {
        "section_markdown": "<the section body, in markdown, NOT including the ## heading>",
        "inline_cross_refs": ["other-slug-1"],
        "new_facts": ["..."]
      }
      """;

  // ----- query -----

  public static final String PICK_RELEVANT_CATEGORIES = """
      You are routing a question to the right corner of a wiki.

      Question: "{question}"

      Master index Contents:
      {master_index}

      Return JSON ONLY:
      {
        "relevant_categories": ["concepts","topics","references"],
        "reasoning": "<one sentence>"
      }
      """;

  public static final String PICK_CANDIDATE_ARTICLES = """
      You are picking the most relevant wiki articles for a question.

      Question: "{question}"

      Articles available:
      {articles_json}

      Return JSON ONLY:
      {
        "candidates": [
          {"path":"<path>","relevance":"primary","why":"<phrase>"}
        ]
      }
      """;

  public static final String SYNTHESIZE_ANSWER = """
      You are answering a question from a wiki. Use ONLY the provided article
      content. Do NOT use your training knowledge. If the wiki does not have
      enough information, say so.

      Question: "{question}"

      Wiki articles (in order of relevance):
      {articles_json}

      Sibling wiki overlap (only _index.md content): {sibling_json}

      --with supplementary wiki content (craft/skill, optional): {with_json}

      Return JSON ONLY:
      {
        "answer_markdown": "<the full answer in markdown, with [text](path) citations>",
        "sources_used": [
          {"path":"wiki/...md","confidence":"high","what_drawn":"<one phrase>"}
        ],
        "related_in_other_wikis": [
          {"wiki":"<name>","title":"<article>","why":"<phrase>"}
        ],
        "knowledge_gaps": ["<gap 1>"],
        "suggested_ingest": ["<topic or URL pattern>"]
      }

      Citation rule: every factual claim that came from an article must have an
      inline link [text](path) to that article. Mention confidence when it is
      medium or low.
      """;

  public static final String ANSWER_FROM_INDEXES = """
      Answer this question from index summaries alone. Do NOT request additional
      articles. If the indexes do not contain enough to answer, say so.

      Question: "{question}"

      Index entries:
      {entries_json}

      Return JSON ONLY:
      {
        "answer_markdown": "<short answer or 'insufficient information in indexes'>",
        "sources_used": [{"path":"...","what_drawn":"..."}],
        "knowledge_gaps": ["..."],
        "suggest_rerun_without_quick": true
      }
      """;

  public static final String RANK_SEARCH_RESULTS = """
      Rank these wiki search results by relevance to the query.

      Query: "{query}"

      Results:
      {results_json}

      Ranking rules: title match > summary match > body match. Multiple-term match
      beats single. More recent beats older.

      Return JSON ONLY:
      {
        "ranked": [{"path":"...","rank":1,"reason":"<phrase>"}]
      }
      """;

  public static final String BUILD_RESUME_BRIEFING = """
      Given this resume context, suggest the most useful next step(s).

      Context:
      {context_json}

      Return JSON:
      {
        "suggestions": [{"label":"...","command":"..."}]
      }
      """;

  // ----- research -----

  public static final String SCOPE_EXISTING_KNOWLEDGE = """
      You are scoping a research run. The wiki currently contains:

      Master index summary:
      {master_index}

      Articles already covering this topic (from grep):
      {existing_json}

      User's research topic: "{topic}"

      Return JSON ONLY:
      {
        "existing_coverage_summary": "<2-3 sentences>",
        "gaps": [{"gap":"<specific gap>","why_matters":"<phrase>"}],
        "search_angles": ["<angle 1>", "<angle 2>"]
      }
      """;

  public static final String DECOMPOSE_QUESTION = """
      Decompose this question into 3-5 focused, independently searchable
      sub-questions. The sub-questions together must produce a complete answer
      to the original question.

      Question: "{question}"

      Return JSON ONLY:
      {
        "sub_questions": [
          {"id":1,"tag":"what","question":"...","search_strategy":"..."}
        ]
      }
      """;

  public static final String GENERATE_RESEARCH_PLAN_PATHS = """
      Decompose into 3-5 independent research paths. Each path must:
      - have a clear, non-overlapping scope
      - be searchable independently (no dependencies on other paths' findings)
      - target a specific aspect (foundational, current state, applications,
        criticisms, adjacent connections)

      Topic: "{topic}"
      Existing coverage: {scope_summary}

      Return JSON ONLY:
      {
        "paths": [
          {
            "name": "...",
            "focus": "...",
            "search_angles": ["...", "..."],
            "target_sources": 4
          }
        ]
      }
      """;

  public static final String SCORE_SOURCE_CREDIBILITY = """
      You are independently scoring source credibility for a research
      ingestion pipeline.

      Source:
      {source_json}

      Rubric:
      +2 if peer-reviewed (DOI, journal, conference, PubMed, arxiv with venue)
      +1 if recent (<=3 years)
      0  if 3-10 years old
      -1 if >10 years (unless foundational/landmark)
      +1 if known author/institution
      -1 if potential bias (industry-sponsored without disclosure, activist org, predatory journal)
      -1 if vendor primary source (first-party docs/blog about own product)
      +1 per other agent that corroborates (max +2)

      Non-stacking: bias signals do NOT stack.

      Return JSON ONLY:
      {
        "credibility_score": 0,
        "tier": "high" | "medium" | "low" | "reject",
        "rationale": "<one sentence>",
        "bias_flags": []
      }
      """;

  public static final String GENERATE_ROUND_REPORT = """
      Generate the round report.

      Topic: "{topic}"
      Round: {round}
      Round details:
      {details_json}

      Compute progress_score (0-100) from the components in the architecture
      doc. Determine remaining_gaps and suggested_followups from the source
      content that was NOT ingested or that raises further questions.

      Return JSON ONLY:
      {
        "progress_score": 0,
        "score_breakdown": {"sources":0,"articles":0,"cross_refs":0,"credibility":0},
        "confidence_map": [{"article":"<path>","confidence":"high","why":"<phrase>"}],
        "new_connections": ["..."],
        "remaining_gaps": [{"gap":"<specific gap>","why_matters":"<phrase>"}],
        "suggested_followups": ["..."],
        "termination_recommendation": "continue" | "early_complete" | "low_yield_warning"
      }
      """;

  public static final String REFLECT_ACROSS_ROUNDS = """
      Reflect across all prior rounds. Priorities (in order):
      1. Draw connections between this round's findings and ALL prior rounds.
      2. Update cross-references — list See-Also additions to make.
      3. Re-evaluate earlier gaps — which are now filled, which still open.
      4. Score remaining gaps: impact x feasibility x specificity = composite (1-125).
      5. Adjust direction — only if findings clearly indicate a shift (rare).

      All prior rounds:
      {history_json}

      This round:
      {round_json}

      Return JSON ONLY:
      {
        "cross_round_connections": ["..."],
        "see_also_additions": [
          {"from":"wiki/.../a.md","to":"wiki/.../b.md","relationship":"<phrase>"}
        ],
        "gap_reevaluation": {
          "filled":["..."],
          "still_open_upgraded":["..."],
          "new":["..."]
        },
        "scored_next_gaps": [
          {"gap":"...","impact":5,"feasibility":4,"specificity":5,"composite":100}
        ],
        "direction_shift": null,
        "early_completion_recommended": false
      }
      """;

  public static final String GENERATE_QUESTION_PLAYBOOK = """
      Generate a playbook that answers the original question using the new wiki
      articles.

      Original question: "{question}"
      Sub-questions covered: {subq_json}
      New wiki articles: {articles_json}

      Return JSON ONLY:
      {
        "title": "...",
        "frontmatter": {"type":"playbook","sources":["wiki/.../*.md"]},
        "sections": [
          {"heading":"## The question","markdown":"..."},
          {"heading":"## Answer in one paragraph","markdown":"..."},
          {"heading":"## Key findings","markdown":"<bulleted, one per sub-question>"},
          {"heading":"## Actionable steps","markdown":"<numbered list>"},
          {"heading":"## Examples","markdown":"..."},
          {"heading":"## Sources","markdown":"<links to wiki articles>"}
        ],
        "derived_theses": ["<testable claim 1>"]
      }
      """;

  // ----- thesis -----

  public static final String DECOMPOSE_THESIS = """
      You are decomposing a thesis for a multi-agent investigation pipeline.

      Thesis: "{thesis}"

      Return JSON ONLY:
      {
        "core_claim": "<the central assertion in one sentence>",
        "key_variables": ["<var1>","<var2>"],
        "testable_prediction": "<what would be true if the thesis is correct>",
        "falsification_criteria": "<what evidence would disprove it>",
        "scope_boundary": "<what is NOT part of this thesis>",
        "sub_claims": []
      }
      """;

  public static final String UPDATE_THESIS_EVIDENCE_TABLES = """
      You are updating the evidence tables on a thesis file.

      Thesis file (current state):
      {thesis_md}

      New sources just ingested + scored:
      {sources_json}

      For each new source, decide which section it belongs in (Evidence For,
      Evidence Against, Nuances & Caveats) and assign a combined strength tag
      "Strong" / "Moderate" / "Weak".

      Return JSON ONLY:
      {
        "edits":[
          {
            "section":"Evidence For",
            "row":{
              "strength":"Strong",
              "title":"...",
              "evidence":"<one-line summary>",
              "source_link":"[Title](../../raw/.../...md)",
              "wiki_link":"[Concept](../concepts/...md)"
            }
          }
        ],
        "round_evidence_counts":{"for":0,"against":0,"nuance":0}
      }
      """;

  public static final String RENDER_THESIS_VERDICT = """
      Render a thesis verdict.

      Thesis: "{thesis}"
      Cumulative evidence after all rounds:
      {evidence_json}

      Per-round counts: {rounds_json}

      Verdict rules:
      - supported: clear preponderance of strong evidence in favor; opposing weak
      - partially-supported: supportive overall but with meaningful caveats
      - contradicted: clear preponderance of strong evidence against
      - mixed: roughly balanced strong evidence on both sides
      - insufficient-evidence: no strong evidence either way

      Never hide mixed evidence behind a binary label.

      Return JSON ONLY:
      {
        "verdict":"supported|partially-supported|contradicted|mixed|insufficient-evidence",
        "confidence":"high|medium|low",
        "summary_2_3_sentences":"...",
        "strongest_supporting_evidence":["..."],
        "strongest_opposing_evidence":["..."],
        "key_caveats":["..."],
        "what_would_change_this_verdict":["..."],
        "suggested_followup_theses":["..."]
      }
      """;

  // ----- librarian / audit -----

  public static final String SCORE_QUALITY_TIER_2 = """
      You are scoring the quality of a wiki article.

      Article (between fences):
      ```
      {article}
      ```

      Article frontmatter:
      {frontmatter}

      Source confidences (from raw frontmatter): {confidences}

      Score on four dimensions (1-5 each):

      1. Depth
         1 = single paragraph, no structure
         3 = multiple sections, covers key aspects
         5 = comprehensive treatment with nuance, examples, edge cases
      2. Source quality
         1 = no sources or single low-confidence source
         3 = 2-3 sources, mixed confidence
         5 = 4+ high-confidence sources that corroborate
      3. Coherence
         1 = disjointed, no logical flow
         3 = readable structure, minor gaps
         5 = clear narrative arc, smooth transitions
      4. Utility
         1 = trivial or obvious information
         3 = useful for understanding the topic
         5 = actionable for decision-making

      Return JSON ONLY:
      {
        "depth": 1,
        "source_quality": 1,
        "coherence": 1,
        "utility": 1,
        "flags": ["thin-coverage","single-source","low-confidence-sources","no-see-also","stale","unverified"],
        "rationale": "<one sentence>"
      }
      """;

  public static final String IDENTIFY_CLAIMS_UNDER_SCRUTINY = """
      You are identifying which specific claims need fresh verification.

      Artifact: {artifact_path}
      Artifact body (relevant excerpt):
      "{excerpt}"

      Triggers fired: {triggers_json}

      Return JSON ONLY:
      {
        "claims":[
          {
            "id":1,
            "text":"<the specific claim, quoted or paraphrased>",
            "type":"factual|causal|predictive|comparative",
            "supporting_sources_in_artifact":["raw/...","wiki/..."],
            "stake":"low|medium|high"
          }
        ]
      }
      """;

  public static final String RENDER_CLAIM_VERDICT = """
      You are reaching a truth verdict for a claim audited via parallel
      support+attack research.

      Claim: "{claim}"

      Agent findings:
      {findings_json}

      Local evidence summary (the artifact's existing citations):
      {local_json}

      Reach a verdict from this rubric:
      - supported:    clear preponderance of strong evidence in favor
      - weakened:     supportive evidence still exists but weaker than implied
      - contradicted: clear preponderance of strong evidence against
      - unresolved:   evidence does not converge

      Never hide mixed evidence behind a binary label.

      Return JSON ONLY:
      {
        "verdict":"supported|weakened|contradicted|unresolved",
        "confidence":"high|medium|low",
        "rationale_2_3_sentences":"...",
        "key_supporting_evidence":["<url> — <one-liner>"],
        "key_opposing_evidence":["<url> — <one-liner>"],
        "what_would_change_this":"<specific future evidence>",
        "recommended_action":"refresh source|retract claim|add caveat|no action|escalate"
      }
      """;

  // ----- lessons -----

  public static final String SCAN_SESSION_TRANSCRIPT = """
      You are extracting lessons learned from a coding/research session
      transcript.

      Session transcript:
      {transcript}

      Topic hint (optional): "{topic_hint}"

      Look for: error->fix patterns, user corrections, discoveries,
      configuration changes, gotchas & quirks.

      Return JSON ONLY:
      {
        "session_summary":"<1-2 sentences>",
        "topic_keywords":["..."],
        "events":[
          {
            "type":"error_fix|correction|discovery|config_change|gotcha",
            "context":"...",
            "symptom":"...",
            "root_cause":"...",
            "fix":"...",
            "evidence_quote":"..."
          }
        ]
      }
      """;

  public static final String GENERALIZE_LESSONS = """
      You are generalizing session events into reusable lessons.

      Events:
      {events_json}

      For each lesson, produce: title, category, context, symptom, root_cause,
      fix, rule (one-sentence generalization), tags.

      DEDUPLICATE: if multiple events teach the same lesson, merge.
      GENERALIZE: rule must be useful outside the session.
      BE SPECIFIC: include exact error messages, paths, tool names.

      Return JSON ONLY:
      {
        "lessons":[
          {
            "title":"...",
            "category":"gotcha|pattern|rule|discovery|correction",
            "context":"...",
            "symptom":"...",
            "root_cause":"...",
            "fix":"...",
            "rule":"...",
            "tags":["..."]
          }
        ]
      }
      """;

  // ----- plan -----

  public static final String ASSEMBLE_WIKI_CONTEXT = """
      You are assembling research context for an implementation plan.

      Goal: "{goal}"

      Wiki articles read (full content, in order):
      {articles_json}

      Sibling wiki overlap (only _index summaries):
      {siblings_json}

      --with wiki content (craft/skill, optional):
      {with_json}

      Return JSON ONLY:
      {
        "directly_relevant":[{"path":"...","title":"...","contributes":"<phrase>"}],
        "supporting_context":[{"path":"...","title":"...","relevant_because":"<phrase>"}],
        "constraints_from_wiki":["<constraint>"],
        "risks_from_contrarian_articles":[{"risk":"...","source":"<path>"}],
        "knowledge_gaps":[{"gap":"<specific gap>","why_blocking":"<phrase>"}],
        "summary_for_user":"<2-3 sentence summary>"
      }
      """;

  public static final String GENERATE_INTERVIEW_QUESTIONS = """
      You are conducting a brief interview to surface requirements the wiki
      can't answer.

      Goal: "{goal}"
      Wiki context summary: "{summary}"

      Generate 3-7 clarifying questions. Good questions ask things the wiki
      cannot know.

      Return JSON ONLY:
      {
        "questions":[
          {
            "id":1,
            "question":"<the question>",
            "why_asked":"<one phrase>",
            "expected_answer_shape":"free-text|yes-no|numeric|choice"
          }
        ]
      }
      """;

  public static final String GENERATE_PLAN_DOCUMENT_ROADMAP = """
      You are writing an implementation plan grounded in a wiki knowledge base.

      Goal: "{goal}"
      Format: roadmap
      Wiki context (synthesized):
      {context_json}

      Citations: every architectural decision must cite a wiki article using
      the dual-link or standard markdown link form.

      Return JSON ONLY:
      {
        "executive_summary":"...",
        "architecture_decisions":[
          {
            "title":"...",
            "context_from_wiki":[{"path":"...","contributes":"..."}],
            "options":[{"name":"Option A","description":"...","wiki_basis":"<path|null>"}],
            "decision":"Option <X>",
            "rationale":"...",
            "consequences":"..."
          }
        ],
        "phases":[
          {
            "n":1,
            "title":"...",
            "estimated_effort":"<S/M/L|days>",
            "goal":"...",
            "tasks":["..."],
            "dependencies":["..."],
            "validation":"...",
            "wiki_grounding":[{"path":"...","says":"<phrase>"}]
          }
        ],
        "risks_table":[{"risk":"...","source":"<wiki path>","mitigation":"..."}],
        "open_questions":["..."],
        "sources_consulted":[{"path":"...","what_drawn":"..."}]
      }
      """;

  // ----- output -----

  public static final String GENERATE_OUTPUT_SUMMARY = """
      You are generating a summary from a wiki knowledge base.

      Subject articles (from primary wiki):
      {subject_json}

      Craft articles (from --with wikis, optional):
      {craft_json}

      Mode: {mode}

      Return JSON ONLY:
      {
        "title":"...",
        "subtitle":"<optional>",
        "tldr":"<2-3 sentences>",
        "sections":[
          {"heading":"## Key Points","markdown":"<3-7 bullets, with [Article](path) citations>"},
          {"heading":"## What This Means","markdown":"..."},
          {"heading":"## Sources","markdown":"<list of dual-link citations>"}
        ]
      }
      """;

  public static final String GENERATE_OUTPUT_REPORT = """
      You are generating a report from a wiki knowledge base.

      Subject articles: {subject_json}
      Craft articles: {craft_json}
      Mode: {mode}

      Return JSON ONLY:
      {
        "title":"...",
        "executive_summary":"...",
        "sections":[
          {"heading":"## Background","markdown":"..."},
          {"heading":"## Findings","markdown":"<each with [Article](path) citations>"},
          {"heading":"## Analysis","markdown":"..."},
          {"heading":"## Conclusions","markdown":"..."},
          {"heading":"## Sources","markdown":"..."}
        ],
        "wiki_articles_used":["wiki/...md"]
      }
      """;
}
