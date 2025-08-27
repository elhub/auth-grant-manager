package no.elhub.auth.features.requests.create

import no.elhub.auth.features.requests.AuthorizationRequest

data class CreateRequestCommand(
    val type: AuthorizationRequest.Type,
    val requester: String,
    val requestee: String,
)
