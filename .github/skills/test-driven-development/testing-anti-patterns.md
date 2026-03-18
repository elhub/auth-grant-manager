---
name: testing-anti-patterns
description:
  Use when writing or changing tests, adding mocks, or modifying production classes. Defines four anti-patterns to avoid: testing mock behaviour, test-only methods on production classes, mocking without understanding dependencies, and incomplete mock data structures.
---

# Testing Anti-Patterns

Reference when writing tests, adding mocks, or modifying production classes.

## Rule 1: Test behaviour, not mocks

Assert on what the code under test produces or does — not on whether a mock was called.

```kotlin
// ❌ — verifies mock invocation, not handler behaviour
coVerify { repo.insert(any()) }

// ✅ — verifies the handler returned the expected result
result.shouldBeRight { it.id shouldBe expectedId }
```

`coVerify` is acceptable only when the side effect (e.g., an event being published) is the observable outcome and there is no return value to assert on. It is
not a substitute for asserting on return values.

## Rule 2: No test-only methods on production classes

If a method is only called from test code, it does not belong on the production class. Put it in a test utility.

```kotlin
// ❌ — Session.destroy() exists only for test cleanup
afterEach { session.destroy() }

// ✅ — cleanup lives in test utilities
afterEach { cleanupSession(session) }  // defined in test sources
```

## Rule 3: Understand the dependency before mocking it

Mock at the right level. Mocking a method that has side effects the test depends on will produce a test that passes for the wrong reason.

```kotlin
// ❌ — mocking insert() means the request is never persisted,
//       so the subsequent find() returns NotFound, hiding the real path
coEvery { repo.insert(any()) } returns Unit.right()
val result = handler.invoke(model)
result.shouldBeRight()   // passes but proves nothing

// ✅ — mock the external service that is slow/unavailable,
//       let the repository run against the test DB
coEvery { partyService.resolve(any()) } returns party.right()
val result = handler.invoke(model)
result.shouldBeRight { it.status shouldBe AuthorizationRequest.Status.Pending }
```

Before adding a mock, answer: what are this dependency's side effects, and does the test depend on any of them?

## Rule 4: Mock the complete data structure

When a mock returns a data object, include all fields the real implementation would return. Partial mocks cause silent failures when downstream code accesses an
omitted field.

```kotlin
// ❌ — missing fields that Route or Handler may access
coEvery { repo.find(any()) } returns AuthorizationRequest(id = uuid, type = type).right()

// ✅ — complete, matches what ExposedRequestRepository would return
coEvery { repo.find(any()) } returns AuthorizationRequest(
    id = uuid,
    type = type,
    status = AuthorizationRequest.Status.Pending,
    requestedBy = partyIdentifier,
    createdAt = now,
    updatedAt = now,
    validTo = now.plusYears(1),
).right()
```

## Warning signs

- A test asserts `coVerify { mock.method() }` with no other assertion
- A method on a production class is only called from `afterEach` or test setup
- Mock setup is longer than the test body
- Removing the mock makes the test pass
- A test passes but you cannot explain what it is proving
