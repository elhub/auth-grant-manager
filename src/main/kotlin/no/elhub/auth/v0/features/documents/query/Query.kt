package no.elhub.auth.v0.features.documents.query

import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.v0.features.common.Pagination
import no.elhub.auth.v0.features.documents.AuthorizationDocument

data class Query(
    val authorizedParty: AuthorizationParty,
    val pagination: Pagination = Pagination(),
    val statuses: List<AuthorizationDocument.Status> = emptyList(),
)
