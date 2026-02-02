package no.elhub.auth.features.documents.query

import no.elhub.auth.features.common.party.AuthorizationParty

data class Query(
    val authorizedParty: AuthorizationParty,
)
