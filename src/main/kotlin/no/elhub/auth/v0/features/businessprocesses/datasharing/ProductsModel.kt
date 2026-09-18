package no.elhub.auth.v0.features.businessprocesses.datasharing

import kotlinx.serialization.Serializable
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.response.JsonApiResponse

@Serializable
data class Attributes(
    val id: Long,
    val name: String
) : JsonApiAttributes

typealias ProductsResponse = JsonApiResponse.CollectionDocument<Attributes>
