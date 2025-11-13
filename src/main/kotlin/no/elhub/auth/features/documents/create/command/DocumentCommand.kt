package no.elhub.auth.features.documents.create.command

import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.create.PartyIdentifier

sealed interface DocumentMetaMarker

sealed class DocumentCommand(
    val requestedFrom: PartyIdentifier,
    val requestedTo: PartyIdentifier,
    val requestedBy: PartyIdentifier,
    val signedBy: PartyIdentifier,
    // Represents business specific meta properties Document doesn't care about
    val meta: DocumentMetaMarker
)

fun DocumentCommand.toAuthorizationDocumentType(): AuthorizationDocument.Type {
    return when (this) {
        is ChangeOfSupplierDocumentCommand -> AuthorizationDocument.Type.ChangeOfSupplierConfirmation
    }
}