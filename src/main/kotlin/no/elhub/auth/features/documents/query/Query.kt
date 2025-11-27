package no.elhub.auth.features.documents.query

import no.elhub.auth.features.common.PartyIdentifier

data class Query(
    val requestedByIdentifier: PartyIdentifier
)
