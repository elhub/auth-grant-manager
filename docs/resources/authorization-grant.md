# Authorization grant

A grant is a stored permission resulting from an approved request or confirmed
document. Its API resource type is `ConsumableGrant` or `PersistentGrant`.
There is no direct v1 grant-creation operation.

## Business information

Each grant records its principal, authorizing organization (`grantedBy`),
receiving market entity (`grantedTo`), actual approving person (`approvedBy`),
source request/document, granted scopes, validity, status, and timestamps.

A source can create several grants. Each grant has its own ID and independent
lifecycle; it is not interchangeable with the source ID.

## Status transitions

| From | To | Cause |
|------|----|-------|
| New grant | `Pending` | Approval creates a grant with a future start. |
| New grant | `Active` | Approval creates a grant whose validity has started. |
| `Pending` | `Active` | Start reached while still valid and not revoked. |
| `Pending` or `Active` | `Expired` | Exclusive end reached. |
| `Pending` or `Active` | `Revoked` | Principal, authorizing organization, or system withdraws permission. |
| `Active` consumable grant | `Exhausted` | System records consumption. |

`Revoked`, `Exhausted`, and `Expired` are terminal. Persistent grants cannot be
exhausted. Clients cannot directly set `Pending`, `Active`, or `Expired`.
See [validity](../concepts/validity.md) for the exact time boundaries.

## Access and operations

The principal and recipient can read individual grants and list their grants.
System actors can also read individual grants; listing does not give them
unrestricted visibility. The authorizing organization may revoke, but that
role alone does not confer grant read access. The approving person likewise
does not automatically receive read or revocation access.

Supported operations are retrieve, list, and lifecycle update. Updates retain
the existing grant type; scopes, participants, and validity are not editable
through this operation.

## Use and withdrawal

A grant must be active and cover the principal, recipient, and proposed
action. The consuming service evaluates the grant and performs the authorized
action. Exhaustion and revocation update the grant; they do not undo a
completed action or change the source's status.

Revocation applies to one grant at a time, even if its source produced several.
Updates to a terminal grant are rejected.

## Implementation references

* [Domain record](../../src/main/kotlin/no/elhub/auth/v1/domain/AuthorizationGrant.kt)
* [Read authorization](../../src/main/kotlin/no/elhub/auth/v1/features/grants/GetGrantHandler.kt)
* [Update authorization](../../src/main/kotlin/no/elhub/auth/v1/features/grants/UpdateGrantHandler.kt)
* [Persistence and transitions](../../src/main/kotlin/no/elhub/auth/v1/repositories/GrantRepository.kt)
