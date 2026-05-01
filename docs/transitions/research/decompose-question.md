# decompose-question

**Type**: LLM
**Used by**: `research` (question mode) Phase 1.b

## Inputs
- the user's question

## Prompt template
```
Decompose this question into 3-5 focused, independently searchable
sub-questions. The sub-questions together must produce a complete answer
to the original question.

Question: "{question}"

Return JSON ONLY:

{
  "sub_questions": [
    {"id":1,"tag":"what", "question":"What patterns do viral long-form articles share?", "search_strategy":"..."},
    {"id":2,"tag":"why",  "question":"What psychological/social mechanisms drive sharing?","search_strategy":"..."},
    {"id":3,"tag":"how",  "question":"What's the step-by-step process to write one?",     "search_strategy":"..."},
    {"id":4,"tag":"who",  "question":"Who's done this and what do they say?",             "search_strategy":"..."},
    {"id":5,"tag":"data", "question":"What does the data say (engagement, virality)?",    "search_strategy":"..."}
  ]
}
```

## Output
JSON as specified. Presented to user, then dispatched as one agent per sub-question.
