package no.elhub.auth.common.structuredata.organisations

import kotlinx.serialization.Serializable
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.response.JsonApiResponse

@Serializable
data class IdentificationAttributes(
    val identificationType: IdentificationType,
) : JsonApiAttributes

@Serializable
enum class IdentificationType {
    PORTAL,
    PLUGIN,
    ORGANIZATION
}

typealias IdentificationResponse = JsonApiResponse.CollectionDocument<IdentificationAttributes>
