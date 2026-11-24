# Authorization request

## Purpose

Collect an electronic approval or rejection from a person with authority to decide. The approval is collected through Elhub MyPage.

## Preconditions

* The requester (requestedBy) is a market entity with a valid Elhub role that allows it to request the authorization type.
* The principal (requestedPrincipal) is an agent that has the authority to approve or reject the request.


## Sequence

```mermaid
sequenceDiagram
    participant M as Requester
    participant A as Auth Grant Manager
    participant P as Approving person
    participant PDP as Policy Decision Point
    M->>A: Create authorization request
    A-->>M: Pending request with scopes and deadline
    P->>A: Approve or reject, with text language
    A->>PDP: Check person's approval authority
    PDP-->>A: Authorization decision
    alt Allowed and still pending within deadline
        A->>A: Record decision; create grants only for approval
        A-->>P: Updated request
    else Not allowed, expired, or already processed
        A-->>P: Error; no new grants
    end
```

The service records a text reference identifying the authorization wording.
The decision includes `textLanguage`, recording the language used for approval
or rejection. The update route requires an end-user token and retains the
actual person as the decision actor, including when that person acts on behalf
of an organization.

## Outcomes

Approval records `approvedBy` and creates the grants defined by the authorization type in the
same transaction. Rejection records the decision language and creates no
grants. A source can be decided only once; concurrent or repeated decisions
cannot create a second set of grants.

A pending request expires at its deadline. Clients cannot set it back to
`Pending` or directly mark it `Expired`. If the resulting grants cannot be
issued, approval fails.

**Access distinction:** reading a request is limited to `requestedBy` and
`requestedPrincipal`; permission to make a decision is checked separately.
Being `requestedOf` or the eventual `approvedBy` does not itself grant read
access.

## Implementation references

* [Request creation](../../src/main/kotlin/no/elhub/auth/v1/features/requests/CreateRequestHandler.kt)
* [Decision handling](../../src/main/kotlin/no/elhub/auth/v1/features/requests/UpdateRequestHandler.kt)
* [Decision actor resolution](../../src/main/kotlin/no/elhub/auth/common/auth/UpdateRequestActorResolver.kt)
* [Atomic decision persistence](../../src/main/kotlin/no/elhub/auth/v1/repositories/RequestRepository.kt)
