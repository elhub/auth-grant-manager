package no.elhub.auth.features.grants.common

import kotlinx.serialization.Serializable
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipData
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne
import no.elhub.devxp.jsonapi.model.JsonApiRelationships
import no.elhub.devxp.jsonapi.response.JsonApiResponse
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObjectWithRelationships

typealias AuthorizationGrantsResponse = List<AuthorizationGrantResponse>

fun List<AuthorizationGrant>.toResponse() = this.map { it.toResponse() }
