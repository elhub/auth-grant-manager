package no.elhub.auth.features.grants.query

import no.elhub.auth.features.common.party.PartyIdentifier

data class Query(
    val grantedTo: PartyIdentifier
)
