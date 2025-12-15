package no.elhub.auth.features.requests.update.dto

import kotlinx.serialization.Serializable
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.request.JsonApiRequest

@Serializable
data class UpdateRequestAttributes(
    val status: AuthorizationRequest.Status
) : JsonApiAttributes

typealias JsonApiUpdateRequest = JsonApiRequest.SingleDocument<UpdateRequestAttributes>
