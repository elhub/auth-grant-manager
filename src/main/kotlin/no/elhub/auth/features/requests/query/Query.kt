package no.elhub.auth.features.requests.query

import no.elhub.auth.features.common.party.AuthorizationParty

data class Query(
    val authorizedParty: AuthorizationParty,
)
