package no.elhub.auth.features.grants.common

import no.elhub.auth.features.grants.AuthorizationGrant


fun List<AuthorizationGrant>.toResponse() = AuthorizationGrantListResponse(
    data = this.map { it.toResponse().data }
)
