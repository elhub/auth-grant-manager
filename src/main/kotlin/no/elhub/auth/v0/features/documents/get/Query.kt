package no.elhub.auth.v0.features.documents.get

import no.elhub.auth.v0.features.common.party.AuthorizationParty
import java.util.UUID

data class Query(
    val documentId: UUID,
    val authorizedParty: AuthorizationParty
)
