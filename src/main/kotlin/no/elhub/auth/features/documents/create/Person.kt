package no.elhub.auth.features.documents.create

import kotlinx.serialization.Serializable
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.request.JsonApiRequest
import no.elhub.devxp.jsonapi.response.JsonApiResponse
import java.util.UUID

@Serializable
data class PersonRequestAttributes(
    val nationalIdentityNumber: String
) : JsonApiAttributes

@Serializable
data class PersonResponseAttributes(
    val type: String,
    val id: String
) : JsonApiAttributes

typealias PersonRequest = JsonApiRequest.SingleDocument<PersonRequestAttributes>
typealias PersonsResponse = JsonApiResponse.SingleDocument<PersonResponseAttributes>

data class Person(
    val internalId: UUID,
)
