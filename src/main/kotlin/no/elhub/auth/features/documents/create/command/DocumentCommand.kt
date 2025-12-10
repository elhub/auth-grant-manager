package no.elhub.auth.features.documents.create.command

import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.documents.AuthorizationDocument

interface DocumentMetaMarker {
    fun toMetaAttributes(): Map<String, String>
}

data class DocumentCommand(
    val type: AuthorizationDocument.Type,
    val requestedFrom: PartyIdentifier,
    val requestedTo: PartyIdentifier,
    val requestedBy: PartyIdentifier,
    val meta: DocumentMetaMarker,
)
