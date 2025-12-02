package no.elhub.auth.features.requests.confirm.dto

import kotlinx.serialization.Serializable
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.request.JsonApiRequest

@Serializable
data class ConfirmRequestAttributes(
    val status: AuthorizationRequest.Status
) : JsonApiAttributes

typealias JsonApiConfirmRequest = JsonApiRequest.SingleDocument<ConfirmRequestAttributes>
