package no.elhub.auth.features.requests.create

import no.elhub.auth.features.requests.AuthorizationRequest

data class Command(
    val type: AuthorizationRequest.Type,
    val requester: String,
    val requestee: String,
)
