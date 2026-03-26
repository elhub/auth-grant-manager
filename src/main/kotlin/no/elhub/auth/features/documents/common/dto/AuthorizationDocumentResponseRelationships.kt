package no.elhub.auth.features.documents.common.dto

import kotlinx.serialization.Serializable
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne
import no.elhub.devxp.jsonapi.model.JsonApiRelationships

@Serializable
data class AuthorizationDocumentResponseRelationships(
    val requestedBy: JsonApiRelationshipToOne,
    val requestedFrom: JsonApiRelationshipToOne,
    val requestedTo: JsonApiRelationshipToOne,
    val signedBy: JsonApiRelationshipToOne? = null,
    val authorizationGrant: JsonApiRelationshipToOne? = null,
) : JsonApiRelationships
