package no.elhub.auth.features.documents.create.command

import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.create.PartyIdentifier

sealed interface DocumentMetaMarker {
    fun toMetaAttributes(): Map<String, String>
}

sealed class DocumentCommand(
    val requestedFrom: PartyIdentifier,
    val requestedTo: PartyIdentifier,
    val requestedBy: PartyIdentifier,
    val signedBy: PartyIdentifier,

    val meta: DocumentMetaMarker
)

fun DocumentCommand.toAuthorizationDocumentType(): AuthorizationDocument.Type = when (this) {
    is ChangeOfSupplierDocumentCommand -> AuthorizationDocument.Type.ChangeOfSupplierConfirmation
}
