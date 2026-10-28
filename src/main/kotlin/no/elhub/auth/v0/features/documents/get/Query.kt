package no.elhub.auth.v0.features.documents.get

import no.elhub.auth.common.auth.AuthorizationParty
import java.util.UUID

data class Query(
    val documentId: UUID,
    val authorizedParty: AuthorizationParty
)
