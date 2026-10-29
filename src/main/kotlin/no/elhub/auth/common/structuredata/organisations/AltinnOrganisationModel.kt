package no.elhub.auth.common.structuredata.organisations

import kotlinx.serialization.Serializable
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.response.JsonApiResponse

@Serializable
data class AltinnAttributes(
    val organizationNo: String,
    val name: String,
) : JsonApiAttributes

typealias AltinnOrganisationResponse = JsonApiResponse.CollectionDocument<AltinnAttributes>
