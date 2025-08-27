package no.elhub.auth.features.requests.query

import kotlinx.serialization.Serializable
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.GetRequestResponseAttributes
import no.elhub.auth.features.requests.common.GetRequestResponseRelationships
import no.elhub.auth.features.requests.common.mapAttributes
import no.elhub.auth.features.requests.common.mapMetaProperties
import no.elhub.auth.features.requests.common.mapRelationships
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObjectWithRelationships

@Serializable
data class AuthorizationRequestsGetResponse(
    val data: List<JsonApiResponseResourceObjectWithRelationships<GetRequestResponseAttributes, GetRequestResponseRelationships>>
)

fun List<AuthorizationRequest>.toResponseBody(): AuthorizationRequestsGetResponse =
    AuthorizationRequestsGetResponse(
        data = this.map { authorizationRequest ->
            val attributes = mapAttributes(authorizationRequest)
            val relationships = mapRelationships(authorizationRequest)
            val metaProperties = mapMetaProperties(authorizationRequest)

            JsonApiResponseResourceObjectWithRelationships(
                type = "AuthorizationRequest",
                id = authorizationRequest.id,
                attributes = attributes,
                relationships = relationships,
                meta = metaProperties
            )
        }
    )


