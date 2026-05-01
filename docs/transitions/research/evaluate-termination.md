# evaluate-termination

**Type**: Tool
**Used by**: `research --min-time` Phase 7 (after every round)

## Inputs
- round report JSON
- trajectory (previous rounds' progress scores + gaps)
- elapsed time vs `--min-time` budget
- average round duration

## Prompt template
None.

## Procedure
```
if termination_recommendation == "early_complete": STOP
if progress_score >= 80 and no high-impact gap and cross_ref_density > 0.6: STOP

Trajectory checks:
- 3 consecutive declining rounds totaling 30+ pt drop -> warn declining trajectory
- 2 consecutive rounds within 5 pt and no new high-impact gap -> recommend early STOP
- any single round score < 20 -> stalled flag

if elapsed >= min_time_budget: STOP
if (elapsed + average_round_duration) > 1.5 × min_time_budget: STOP
if low_yield_count >= 2: optionally switch to --deep, then STOP if still low

else: CONTINUE with top-3 gaps from reflection
```

## Output
`{ "decision": "STOP" | "CONTINUE", "reason": "...", "next_round_focus": ["gap1","gap2","gap3"] }`
