package no.elhub.auth.features.grants.update.dto

import kotlinx.serialization.Serializable
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.request.JsonApiRequest

@Serializable
data class UpdateRequestAttributes(
    val status: AuthorizationGrant.Status
) : JsonApiAttributes

typealias JsonApiUpdateRequest = JsonApiRequest.SingleDocument<UpdateRequestAttributes>
