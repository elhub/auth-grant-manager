package no.elhub.auth.features.documents.query

import no.elhub.auth.features.common.Pagination
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.documents.AuthorizationDocument

data class Query(
    val authorizedParty: AuthorizationParty,
    val pagination: Pagination = Pagination(),
    val statuses: List<AuthorizationDocument.Status> = emptyList(),
)
