package no.elhub.auth.v0.features.grants.consume.dto

import kotlinx.serialization.Serializable
import no.elhub.auth.v0.features.grants.AuthorizationGrant
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.request.JsonApiRequest

@Serializable
data class ConsumeRequestAttributes(
    val status: AuthorizationGrant.Status
) : JsonApiAttributes

typealias JsonApiConsumeRequest = JsonApiRequest.SingleDocument<ConsumeRequestAttributes>
