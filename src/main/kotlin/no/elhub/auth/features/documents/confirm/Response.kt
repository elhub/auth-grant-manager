package no.elhub.auth.features.documents.confirm

import kotlinx.serialization.Serializable
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.model.JsonApiLinks
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipData
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne
import no.elhub.devxp.jsonapi.model.JsonApiRelationships
import no.elhub.devxp.jsonapi.response.JsonApiResponse
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObjectWithRelationships

@Serializable
data class ConfirmDocumentResponseAttributes(
    val status: String,
    val documentType: String
) : JsonApiAttributes

@Serializable
data class ConfirmDocumentResponseRelationships(
    val grant: JsonApiRelationshipToOne
) : JsonApiRelationships

typealias ConfirmDocumentResponse =
    JsonApiResponse.SingleDocumentWithRelationships<ConfirmDocumentResponseAttributes, ConfirmDocumentResponseRelationships>

fun ConfirmDocumentResult.toResponse(): ConfirmDocumentResponse =
    ConfirmDocumentResponse(
        data = JsonApiResponseResourceObjectWithRelationships(
            type = "AuthorizationDocument",
            id = document.id.toString(),
            attributes = ConfirmDocumentResponseAttributes(
                status = document.status.toString(),
                documentType = document.type.toString()
            ),
            relationships = ConfirmDocumentResponseRelationships(
                grant = JsonApiRelationshipToOne(
                    data = JsonApiRelationshipData(
                        id = grant.id,
                        type = "AuthorizationGrant"
                    ),
                    links = JsonApiLinks.RelationShipLink(
                        self = "/authorization-grants/${grant.id}"
                    )
                )
            )
        ),
        links = JsonApiLinks.ResourceObjectLink(
            self = "/authorization-documents/${document.id}"
        )
    )
