package no.elhub.auth.v0.features.documents.confirm

import no.elhub.auth.v0.features.common.party.AuthorizationParty
import java.util.UUID

data class Command(
    val documentId: UUID,
    val authorizedParty: AuthorizationParty,
    val signedFile: ByteArray
)
