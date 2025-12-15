package no.elhub.auth.features.documents.confirm

import no.elhub.auth.features.common.party.PartyIdentifier
import java.util.UUID

data class Command(
    val documentId: UUID,
    val requestedByIdentifier: PartyIdentifier,
    val signedFile: ByteArray
)
