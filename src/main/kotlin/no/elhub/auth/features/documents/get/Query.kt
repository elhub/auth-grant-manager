package no.elhub.auth.features.documents.get

import no.elhub.auth.features.common.party.AuthorizationParty
import java.util.UUID

data class Query(
    val documentId: UUID,
    val authorizedParty: AuthorizationParty
)
