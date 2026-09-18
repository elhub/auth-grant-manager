package no.elhub.auth.v0.features.grants.query

import no.elhub.auth.v0.features.common.Pagination
import no.elhub.auth.v0.features.common.party.AuthorizationParty

data class Query(
    val authorizedParty: AuthorizationParty,
    val pagination: Pagination = Pagination(),
)
