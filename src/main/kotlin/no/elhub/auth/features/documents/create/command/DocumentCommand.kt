package no.elhub.auth.features.documents.create.command

import no.elhub.auth.features.common.CreateScopeData
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.documents.AuthorizationDocument
import java.time.OffsetDateTime

interface DocumentMetaMarker {
    fun toMetaAttributes(): Map<String, String>
}

data class DocumentCommand(
    val type: AuthorizationDocument.Type,
    val requestedFrom: PartyIdentifier,
    val requestedTo: PartyIdentifier,
    val requestedBy: PartyIdentifier,
    val validTo: OffsetDateTime,
    val scopes: List<CreateScopeData>,
    val meta: DocumentMetaMarker,
)
