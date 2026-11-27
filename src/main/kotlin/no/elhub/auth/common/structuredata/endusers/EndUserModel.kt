package no.elhub.auth.common.structuredata.endusers

import kotlinx.serialization.Serializable
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.response.JsonApiResponse

@Serializable
data class EndUserAttributes(
    val endUserUniqueId: String,
    val endUserType: EndUserType,
    val endUserId: String? = null,
) : JsonApiAttributes

@Serializable
enum class EndUserType {
    Company,
    Person,
}

typealias EndUserResponse = JsonApiResponse.SingleDocument<EndUserAttributes>
