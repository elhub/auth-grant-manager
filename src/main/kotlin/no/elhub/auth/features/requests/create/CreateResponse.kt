package no.elhub.auth.features.requests.create

import kotlinx.serialization.Serializable
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.model.JsonApiLinks
import no.elhub.devxp.jsonapi.model.JsonApiResourceLinks
import no.elhub.devxp.jsonapi.response.JsonApiResponse
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceWithAttributesAndLinks

@Serializable
data class CreateRequestResponseAttributes(
    val status: String,
    val requestType: String
) : JsonApiAttributes

@Serializable
data class CreateRequestResponseLinks(
    val self: String
) : JsonApiResourceLinks

typealias CreateRequestResponseResource = JsonApiResponseResourceWithAttributesAndLinks<CreateRequestResponseAttributes, CreateRequestResponseLinks>
typealias CreateRequestResponse = JsonApiResponse.SingleDocumentWithAttributesAndLinks<CreateRequestResponseAttributes, CreateRequestResponseLinks>

fun AuthorizationRequest.toCreateResponse() = CreateRequestResponse(
    data = CreateRequestResponseResource(
        type = "AuthorizationRequest",
        id = this.id.toString(),
        attributes = CreateRequestResponseAttributes(
            status = this.status.name,
            requestType = this.type.name
        ),
        links = CreateRequestResponseLinks(
            self = "/authorization-requests/${this.id}",
        )
    ),
    links = JsonApiLinks.ResourceObjectLink("/authorization-requests")
)
