package no.elhub.auth.v0.features.documents.confirm

import no.elhub.auth.common.auth.AuthorizationParty
import java.util.UUID

data class Command(
    val documentId: UUID,
    val authorizedParty: AuthorizationParty,
    val signedFile: ByteArray
)
