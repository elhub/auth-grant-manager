# Skill Evaluator

Agent-driven evaluation of Copilot CLI skill quality.
**You do not run this yourself** — tell the Copilot CLI agent to do it.

## How to run

```
evaluate skills
```

or for a single case:

```
evaluate skills --case new-repository
```

The agent will:
1. Read test cases from `eval/cases.yaml`
2. Spawn a **generate** sub-agent per case (loads the relevant skills, writes code to `/tmp/eval-<id>/`)
3. Spawn a **judge** sub-agent that reads the generated code and scores each rubric criterion (LLM-as-judge)
4. Save results to `eval/results.jsonl`
5. Render a visual terminal report

## View past results

```
python3 eval/report.py          # full history
python3 eval/report.py --last 3 # last 3 runs
python3 eval/report.py --case new-repository
```

## Add a new test case

Edit `eval/cases.yaml`. Each case needs:

```yaml
- id: my-case
  description: "One-line description"
  skills:
    - skill-name  # which .github/skills/ directories to load
  prompt: |
    Detailed task for the generate agent.
    Tell it what to create and where: write files to /tmp/eval-my-case/
  rubric:
    - id: criterion-id
      criterion: "Short label shown in report"
      pass_when: |
        Detailed description for the judge agent — what does PASS look like?
      weight: 2   # higher = more important (1-3 recommended)
```

## Files

| File | Purpose |
| ---- | ------- |
| `cases.yaml` | Test case definitions (prompts + rubrics) |
| `results.jsonl` | Saved evaluation results (append-only, gitignored) |
| `report.py` | CLI renderer for saved results |
