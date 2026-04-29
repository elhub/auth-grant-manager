package no.elhub.auth.features.requests.query

import no.elhub.auth.features.common.Pagination
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.requests.AuthorizationRequest

data class Query(
    val authorizedParty: AuthorizationParty,
    val pagination: Pagination = Pagination(),
    val status: AuthorizationRequest.Status? = null,
)
