package no.elhub.auth.v1.features.documents

import no.elhub.auth.v1.Errors
import no.elhub.auth.v1.domain.AuthorizationDocumentStatus

sealed class ConfirmDocumentError(override val detail: String) : Errors(detail) {
    class NotAuthorized : ConfirmDocumentError("The authorized party is not permitted to confirm this document.")

    class InvalidStatus(status: AuthorizationDocumentStatus) : ConfirmDocumentError(
        "AuthorizationDocument cannot be confirmed from status '$status'. Only Pending documents can be confirmed.",
    )

    class SignatoryResolutionFailed : ConfirmDocumentError("The document signatory could not be resolved.")

    class SignatoryNotAllowed : ConfirmDocumentError("The signer does not match the document's requestedOf party.")

    class InvalidGrantValidity : ConfirmDocumentError(
        "The requested grant validity period has ended or ends before it starts.",
    )
}
