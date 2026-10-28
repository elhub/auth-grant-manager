package no.elhub.auth.v0.features.grants.query

import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.v0.features.common.Pagination

data class Query(
    val authorizedParty: AuthorizationParty,
    val pagination: Pagination = Pagination(),
)
