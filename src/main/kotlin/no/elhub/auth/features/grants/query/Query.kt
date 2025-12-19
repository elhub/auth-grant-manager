package no.elhub.auth.features.grants.query

import no.elhub.auth.features.common.party.AuthorizationParty

data class Query(
    val authorizedParty: AuthorizationParty
)
