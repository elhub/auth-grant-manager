# Authorization request

An authorization request records a proposed permission and an electronic
approval or rejection. Its API resource type is `AuthorizationRequest`.

## Business information

| Information | Meaning |
|-------------|---------|
| Request type and requested scopes | Business purpose and proposed permission. |
| Actor relationships | Requester, principal, authorizing organization, and actual approver when approved. |
| `externalReference` | Optional caller-provided correlation reference; not the resource identity or a grant. |
| `redirectUri` | Optional client navigation information; not an authorization decision. |
| `textReference`, `textLanguage` | Wording reference and recorded decision language. |
| Proposal validity | Deadline for deciding the request, distinct from grant validity. |
| Requested grant validity | Optional framework-agreement dates only. |
| Metadata and timestamps | Prepared business context and record creation/update times. |
| Grant relationship | Grants produced by approval, linked to their source. |

## Status transitions

| From | To | Cause |
|------|----|-------|
| New proposal | `Pending` | Successful creation. |
| `Pending` | `Approved` | Authorized person approves before the deadline; grants are created atomically. |
| `Pending` | `Rejected` | Authorized person rejects before the deadline; no grants are created. |
| `Pending` | `Expired` | Deadline reached without a decision. |

`Approved`, `Rejected`, and `Expired` are terminal. An approved request stays
approved even when its grants later become unusable.

## Access and operations

`requestedBy` and `requestedPrincipal` may retrieve and list the record.
Approval/rejection requires a person with an end-user token and a successful
PDP authority check; it is not inferred from read access. Other relationships
do not automatically confer read access.

Supported operations are create, retrieve, list, and decide. There is no v1
request deletion, cancellation, or editing of the proposed scope.

## Related process and implementation

* [Request process](../processes/authorization-request.md)
* [Domain record](../../src/main/kotlin/no/elhub/auth/v1/domain/AuthorizationRequest.kt)
* [Persistence and collection visibility](../../src/main/kotlin/no/elhub/auth/v1/repositories/RequestRepository.kt)
* [Read authorization](../../src/main/kotlin/no/elhub/auth/v1/features/requests/GetRequestHandler.kt)
