package no.elhub.auth.features.requests.create.model

import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.create.dto.CreateRequestMeta

data class CreateRequestModel(
    val requestType: AuthorizationRequest.Type,
    val meta: CreateRequestMeta,
)
