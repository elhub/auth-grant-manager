---
name: test-driven-development
description: >
  Use when implementing any feature or bug fix.
  Defines the red-green-refactor cycle and the rule that no production code
  may be written without a failing test first.
  Load before starting any implementation work.
---

# Test-Driven Development

Write the test first. Watch it fail. Write minimal code to pass. Refactor.

## The cycle

```text
RED   → write one failing test
         ↓ confirm it fails for the right reason
GREEN → write minimal code to pass
         ↓ confirm all tests pass
REFACTOR → clean up, no new behaviour
         ↓ confirm still green
         → next test
```

## Red

Write one test covering one behaviour. The test must fail before any implementation exists. If it passes immediately, it is not testing anything new.

```kotlin
class CreateHandlerTest : FunSpec({
    test("returns Left(AuthorizationError) when party does not match model") {
        val repo = mockk<RequestRepository>()
        val partyService = mockk<PartyService>()

        coEvery { partyService.resolve(any()) } returns mismatchedParty.right()

        val result = CreateHandler(repo, partyService).invoke(validModel)

        result.shouldBeLeft(CreateError.AuthorizationError)
    }
})
```

Run the test. Confirm it fails because the feature doesn't exist, not because of a compilation error or wrong setup.

## Green

Write the minimum code to make the test pass. Do not add behaviour the test doesn't require.

## Refactor

Clean up duplication and naming. Do not change behaviour. All tests must remain green throughout.

## Hard rules

- No production code without a failing test first. If you wrote code before the test, delete it and start from the test.
- One test per behaviour. If the test name contains "and", split it.
- `suspend` functions are mocked with `coEvery`, not `every`.
- Mocks are reset in `beforeTest` — see the Testing skill.

## Bug fixes

Before fixing a bug, write a test that reproduces it. The test must fail. Then fix. The test is the regression guard.

## What counts as a complete implementation

- Every new function or method has a test that was written first
- Each test was seen to fail before the implementation existed
- All tests pass
- No test-only methods exist on production classes
