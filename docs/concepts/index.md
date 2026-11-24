# Concepts

This section describes some of the important terms and concepts used in `auth-grant-manager`. The purpose is to explain **what** the
concepts mean, not **how** they are implemented.

A lot of these concepts are defined in the
[Elhub authorization model](https://docs.elhub.cloud/architecture/everest/auth/index.html). For a more thorough
understanding of the concepts, please read that.

## Glossary


| Concept                                                          | Meaning                                                                                                                                                                         |
|------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Authentication                                                   | Verifies who is making a request.                                                                                                                                               |
| Authorization                                                    | Determines whether the actor may perform the action.                                                                                                                            |
| Agent                                                            | The general concept for a person, organization, or Market Entity identified by an authentication method.                                                                        |
| Actor                                                            | The authenticated identity making a request. Every actor acts for an effective Principal.                                                                                       |
| Principal                                                        | The Agent whose authority is exercised for a request. The Principal may be the actor's own Agent or another Agent represented through delegation or another valid relationship. |
| Intrinsic authority                                              | Authority a principal has because of its relationships in the business domain.                                                                                                  |
| Delegated authority                                              | Authority an actor is allowed to exercise on behalf of a principal.                                                                                                             |
| [Authorization request](../resources/authorization-request.md)   | An electronic proposal a person can approve or reject.                                                                                                                          |
| [Authorization document](../resources/authorization-document.md) | A PDF proposal that must be signed and verified.                                                                                                                                |
| [Authorization grant](../resources/authorization-grant.md)       | A record of delegated authority issued after approval.                                                                                                                          |
| [Scope](../resources/authorization-scope.md)                     | A restriction on the resources, capabilities, or context in which authority may be exercised.                                                                                   |
| Consumable grant                                                 | An authorization grant that is exhausted after use.                                                                                                                             |
| Persistent grant                                                 | An authorization grant that can be used repeatedly while active.                                                                                                                |

### Roles in v1

| Request or document  | Grant        | Meaning                                                        |
|----------------------|--------------|----------------------------------------------------------------|
| `requestedBy`        | `grantedTo`  | Market entity asking for and receiving permission.             |
| `requestedPrincipal` | `principal`  | Organization whose authority the market entity may exercise.   |
| `requestedOf`        | `grantedBy`  | Organization asked to provide authorization for the principal. |
| `approvedBy`         | `approvedBy` | Person who approves the request or signs the document.         |

The market entity that submits a signed document is the caller; the signer is the approver. A grant records permission,
not the result of a contract change.

## Validity and Time

Validity can apply to the proposal (authorization requests and authorization documents), the grant, or the action taken
under it:

| Concept               | Business meaning                                                        |
|-----------------------|-------------------------------------------------------------------------|
| Proposal validity     | When a request can be approved or a document confirmed.                 |
| Grant validity        | When the resulting permission may be exercised.                         |
| Scope time constraint | Which dates or times are allowed for the resource covered by the grant. |

A scope time constraint limits what the grant permits. It does not determine when the grant itself becomes active or
expires.

### Approval window

All request and document types have an approval window based on the DocumentType/RequestType. The approval window is the
time period during which the request or document may be approved and is defined by the `validFrom` and `validTo` fields.
The approval window is enforced at the time of approval or confirmation. A request or document may be created at any time,
but it cannot be approved or confirmed outside its approval window.

Approved sources remain `Approved` after their approval deadline. The grants have their own lifecycles; expiry, revocation,
or exhaustion of a grant does not change the source back to pending or expired.
