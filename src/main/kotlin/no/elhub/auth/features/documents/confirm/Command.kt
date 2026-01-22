package no.elhub.auth.features.documents.confirm

import no.elhub.auth.features.common.party.AuthorizationParty
import java.util.UUID

data class Command(
    val documentId: UUID,
    val authorizedParty: AuthorizationParty,
    val signedFile: ByteArray
)
