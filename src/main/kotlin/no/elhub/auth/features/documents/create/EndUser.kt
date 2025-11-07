package no.elhub.auth.features.documents.create

import kotlinx.serialization.Serializable
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.response.JsonApiResponse
import java.util.UUID

@Serializable
data class EndUserAttributes(
    val type: String,
    val id: String
) : JsonApiAttributes

typealias AuthPersonsResponse = JsonApiResponse.SingleDocument<EndUserAttributes>

data class EndUser(
    val internalId: UUID,
)
