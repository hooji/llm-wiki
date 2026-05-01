# generate-interview-questions

**Type**: LLM
**Used by**: `plan` Stage 2 (skipped if `--no-interview` or `--quick`)

## Inputs
- goal
- Stage-1 `summary_for_user`

## Prompt template
```
You are conducting a brief interview to surface requirements the wiki
can't answer.

Goal: "{goal}"
Wiki context summary: "{stage_1_summary_for_user}"

Generate 3-7 clarifying questions. Good questions ask things the wiki
cannot know:
- Constraints the wiki can't know (timeline, budget, team size)
- Scale parameters (volume, traffic, growth assumptions)
- Priority tradeoffs
- Edge cases (the wiki notes [risk]; how should we handle it?)
- Non-functional requirements (perf targets, backwards compat needs)

DO NOT ask things the wiki already answers.

Return JSON ONLY:

{
  "questions":[
    {
      "id":1,
      "question":"<the question>",
      "why_asked":"<one phrase>",
      "expected_answer_shape":"free-text"|"yes-no"|"numeric"|"choice"
    }
  ]
}
```

## Output
JSON as specified. All questions are presented at once; user replies free-text; orchestrator stores answers keyed by `id`.
