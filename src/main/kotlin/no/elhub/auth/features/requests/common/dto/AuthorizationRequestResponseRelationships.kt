package no.elhub.auth.features.requests.common.dto

import kotlinx.serialization.Serializable
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne
import no.elhub.devxp.jsonapi.model.JsonApiRelationships

@Serializable
data class AuthorizationRequestResponseRelationships(
    val requestedBy: JsonApiRelationshipToOne,
    val requestedFrom: JsonApiRelationshipToOne,
    val requestedTo: JsonApiRelationshipToOne,
    val approvedBy: JsonApiRelationshipToOne? = null,
    val authorizationGrant: JsonApiRelationshipToOne? = null,
) : JsonApiRelationships
