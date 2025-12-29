package no.elhub.auth.features.documents.create

import kotlinx.serialization.Serializable
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.DOCUMENTS_PATH
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.model.JsonApiLinks
import no.elhub.devxp.jsonapi.model.JsonApiResourceLinks
import no.elhub.devxp.jsonapi.response.JsonApiResponse
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceWithAttributesAndLinks

@Serializable
data class CreateDocumentResponseAttributes(
    val status: String,
    val documentType: String,
) : JsonApiAttributes

@Serializable
data class CreateDocumentResponseLinks(
    val self: String,
    val file: String,
) : JsonApiResourceLinks

typealias CreateDocumentResponseResource = JsonApiResponseResourceWithAttributesAndLinks<CreateDocumentResponseAttributes, CreateDocumentResponseLinks>
typealias CreateDocumentResponse = JsonApiResponse.SingleDocumentWithAttributesAndLinks<CreateDocumentResponseAttributes, CreateDocumentResponseLinks>

fun AuthorizationDocument.toCreateDocumentResponse() = CreateDocumentResponse(
    data = CreateDocumentResponseResource(
        type = "AuthorizationDocument",
        id = this.id.toString(),
        links = CreateDocumentResponseLinks(
            self = "${DOCUMENTS_PATH}/${this.id}",
            file = "${DOCUMENTS_PATH}/${this.id}.pdf"
        ),
        attributes = CreateDocumentResponseAttributes(
            status = this.status.toString(),
            documentType = this.type.toString()
        ),
    ),
    links = JsonApiLinks.ResourceObjectLink(DOCUMENTS_PATH)
)
