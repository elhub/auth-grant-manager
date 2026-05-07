---
name: test-driven-development
description: >
  Use when implementing any feature or bug fix. Defines the red-green-refactor cycle and the rule that no
  production code may be written without a failing test first. Load before starting any implementation work.
---
# Test-Driven Development

Write the test first. Watch it fail. Write minimal code to pass. Refactor.

## Cycle

```text
RED   → failing test (must fail for the right reason, not compilation)
GREEN → minimal code to pass
REFACTOR → clean up, no new behaviour, all tests stay green
→ repeat
```

## Hard rules

- No production code without a failing test first. If you wrote code before the test, delete it.
- One test per behaviour. If the test name contains "and", split it.
- `suspend` functions → `coEvery`, not `every`.
- Mocks reset in `beforeTest` — see testing-kotest skill.

## Bug fixes

Write a test that reproduces the bug first. It must fail. Then fix. The test is the regression guard.

## Done when

- Every new function has a test written first (seen to fail)
- All tests pass
- No test-only methods on production classes
