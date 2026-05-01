# detect-input-mode

**Type**: Tool
**Used by**: `research` pre-Phase-0

## Inputs
- raw `$ARGUMENTS` minus flags
- explicit `--mode thesis` if present

## Prompt template
None.

## Procedure
```
if --mode thesis flag: return THESIS (claim = the value of --mode thesis)
elif input matches /\b(prove that|is it true that|verify|test the claim|test the hypothesis)\b/i:
    return THESIS (claim = input minus the signal words)
elif input starts with what/why/how/when/where/who, contains "?",
     or matches /^how to|^what makes|^why does/:
    return QUESTION
else:
    return TOPIC
```

## Output
`{ "mode": "TOPIC" | "QUESTION" | "THESIS", "payload": "<topic|question|claim>" }`
