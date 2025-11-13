package no.elhub.auth.features.documents.create

import kotlinx.serialization.Serializable
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.response.JsonApiResponse
import java.util.UUID

@Serializable
data class PersonAttributes(
    val type: String,
    val id: String
) : JsonApiAttributes

typealias AuthPersonsResponse = JsonApiResponse.SingleDocument<PersonAttributes>

data class Person(
    val internalId: UUID,
)
