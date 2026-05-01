# scan-session-transcript

**Type**: LLM
**Used by**: `lessons` Step 1

## Inputs
- raw session transcript (concatenated user + assistant turns)
- optional topic hint

## Prompt template
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
   non-obvious knowledge.

4. Configuration changes
   Files created or modified during the session — especially dotfiles,
   settings, profiles, shell configs.

5. Gotchas & quirks
   Platform-specific behaviors, tool-specific edge cases, undocumented
   behaviors encountered.

Return JSON ONLY:

{
  "session_summary":"<1-2 sentences on what the session was about>",
  "topic_keywords":["..."],
  "events":[
    {
      "type":"error_fix"|"correction"|"discovery"|"config_change"|"gotcha",
      "context":"<what was being done>",
      "symptom":"<error/behavior, may be empty for non-error types>",
      "root_cause":"<why it happened, may be empty>",
      "fix":"<what was done>",
      "evidence_quote":"<a direct quote from the transcript demonstrating this event>"
    }
  ]
}
```

## Output
JSON as specified. Typical session yields 2-7 events.

## Notes
For transcripts exceeding context, chunk by user/assistant turn pairs and run multiple times; `generalize-lessons` deduplicates across chunks.
