package no.elhub.auth.features.documents.get

import no.elhub.auth.features.common.party.PartyIdentifier
import java.util.UUID

data class Query(
    val documentId: UUID,
    val requestedByIdentifier: PartyIdentifier
)
