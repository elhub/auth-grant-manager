package no.elhub.auth.common.structuredata.organisations

import kotlinx.serialization.Serializable
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.response.JsonApiResponse

@Serializable
data class OrganisationAttributes(
    val name: String,
) : JsonApiAttributes

typealias OrganisationResponse = JsonApiResponse.SingleDocument<OrganisationAttributes>
