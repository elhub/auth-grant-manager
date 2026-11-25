# Authorization document

## Purpose

Collect approval through a signed PDF. The market entity arranges signing outside Elhub and returns the signed
document for verification.

## Preconditions

* The requester (`requestedBy`) is a market entity with a valid Elhub role that allows it to request the authorization type.
* The party asked to authorize (`requestedOf`) can represent the principal (`requestedPrincipal`).
* The person signing the document has authority to approve the authorization.

## Sequence

```mermaid
sequenceDiagram
    participant EU as End User
    participant ME as Market Entity
    participant AGM as auth-grant-manager
    participant EMIF as EMIF
    participant SP as Signing Service
    EU ->> ME: Initiates transaction
    ME ->> AGM: POST /authorization-documents
    Note right of AGM: Generate and sign the PDF with Elhub's certificate.
    AGM ->> ME: 201 Created (documentId)
    ME ->> AGM: GET /authorization-documents/{documentId}.pdf
    AGM ->> ME: 200 OK (document.pdf)
    ME ->> SP: Send PDF for signing
    Note right of SP: Market Entity gets the document<br>signed using an approved certificate<br>auhtority and signing service.
    EU ->> SP: Signs the document
    SP ->> ME: Return signed PDF
    ME ->> AGM: PUT /authorization-documents/{documentId}.pdf
    Note right of AGM: Verify the signatures and the signer's authority.
    AGM ->> ME: 204 No Content
    ME ->> AGM: GET /authorization-documents/{documentId}
    AGM ->> ME: 200 OK (document status and grants)
    ME ->> EMIF: Send BRS with authorizationGrantId
```

The market entity creates an authorization document in Elhub and uses its ID to retrieve the Elhub-signed PDF.
The end user signs it through a signing provider. Only the original requester can return the signed PDF.
Elhub checks that it matches the original document, verifies the signatures, and confirms that the signer
has authority to approve. The market entity can then retrieve the document status and the resulting grant IDs
for use in Elhub.

## Outcome

On successful confirmation, the system stores the signed PDF, records the signer as `approvedBy`, and creates the
grants defined by the authorization type in the same transaction. A failed confirmation creates no grants. The
document has no rejection operation and expires if it is not confirmed by its deadline.

## Related Resources

* [AuthorizationDocument](../resources/authorization-document.md)
