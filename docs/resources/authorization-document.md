# Authorization document

An authorization document records a proposed permission, its generated PDF,
and the result of successful signature verification.
Its API resource type is `AuthorizationDocument`.

## Business information

The record contains the document type, requested scopes, requester, principal,
authorizing organization, optional external reference, prepared metadata,
proposal validity, and creation/update timestamps. Framework agreements may
also carry requested grant-validity dates.

After confirmation it identifies the actual approving person and resulting
grants. The PDF is generated in the requested document language and initially
signed by Elhub. Successful confirmation replaces the stored file with the
returned signed PDF.

## Status transitions

| From | To | Cause |
|------|----|-------|
| New proposal | `Pending` | Successful generation, Elhub signing, and storage. |
| `Pending` | `Approved` | Successful confirmation before the deadline; signed evidence and grants are committed atomically. |
| `Pending` | `Expired` | Deadline reached without successful confirmation. |

`Approved` and `Expired` are terminal. There is no `Rejected` status.
Signature-validation failure does not create a new terminal state.

## Access and operations

`requestedBy` and `requestedPrincipal` may retrieve the record or PDF and
list documents. Only the original `requestedBy` may submit the signed PDF
for confirmation. The actual signatory must separately pass approval-authority
checks.

Supported operations are create, retrieve metadata, retrieve PDF, list, and
confirm. There is no v1 rejection, deletion, or editing of the proposal.

## Related process and implementation

* [Document process](../processes/authorization-document.md)
* [Domain record](../../src/main/kotlin/no/elhub/auth/v1/domain/AuthorizationDocument.kt)
* [Persistence and collection visibility](../../src/main/kotlin/no/elhub/auth/v1/repositories/DocumentRepository.kt)
* [Read authorization](../../src/main/kotlin/no/elhub/auth/v1/features/documents/GetDocumentHandler.kt)
