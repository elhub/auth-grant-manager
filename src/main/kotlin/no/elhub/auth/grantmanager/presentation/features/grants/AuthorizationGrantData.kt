package no.elhub.auth.grantmanager.presentation.features.grants

import kotlinx.serialization.Serializable
import no.elhub.auth.grantmanager.data.models.AuthorizationGrantDbEntity
import no.elhub.auth.grantmanager.domain.models.Grant
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipData
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne
import no.elhub.devxp.jsonapi.model.JsonApiRelationships
import no.elhub.devxp.jsonapi.response.JsonApiResponse
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObjectWithRelationships
import org.koin.ext.getFullName
import java.util.TimeZone

@Serializable
data class GrantResponseAttributes(
    val status: String,
    val grantedAt: String,
    val validFrom: String,
    val validTo: String,
) : JsonApiAttributes

@Serializable
data class GrantRelationships(
    val grantedFor: JsonApiRelationshipToOne,
    val grantedBy: JsonApiRelationshipToOne,
    val grantedTo: JsonApiRelationshipToOne,
) : JsonApiRelationships

typealias AuthorizationGrantResponse = JsonApiResponse.SingleDocumentWithRelationships<GrantResponseAttributes, GrantRelationships>
typealias AuthorizationGrantsResponse = JsonApiResponse.CollectionDocumentWithRelationships<GrantResponseAttributes, GrantRelationships>

val timezoneIdOslo: String = TimeZone.getAvailableIDs().single { it.contains("oslo", true) }
val defaultTimezone: TimeZone = TimeZone.getTimeZone(timezoneIdOslo)

fun Grant.toApiResponse(): AuthorizationGrantResponse = AuthorizationGrantResponse(
    data = JsonApiResponseResourceObjectWithRelationships(
        type = this.javaClass.kotlin.getFullName(),
        id = this.id.toString(),
        attributes = GrantResponseAttributes(
            status = when {
                this.active -> "active"
                this.expired -> "expired"
                // this.revoked -> "revoked"
                // this.exhausted -> "exhausted"
                else -> throw IllegalStateException("Could not determine grant status")
            },
            grantedAt = defaultTimezone.getOffset(this.grantedAt.toEpochMilli()).toString(),
            validFrom = defaultTimezone.getOffset(this.validFrom.toEpochMilli()).toString(),
            validTo = defaultTimezone.getOffset(this.validTo.toEpochMilli()).toString(),
        ),
        relationships = GrantRelationships(
            grantedFor = JsonApiRelationshipToOne(
                data = JsonApiRelationshipData(
                    id = this.grantedFor,
                    type = this.grantedFor.javaClass.kotlin.getFullName()
                )
            ),
            grantedBy = JsonApiRelationshipToOne(
                data = JsonApiRelationshipData(
                    id = this.grantedBy.id.toString(),
                    type = this.grantedBy.javaClass.kotlin.getFullName()
                )
            ),
            grantedTo = JsonApiRelationshipToOne(
                data = JsonApiRelationshipData(
                    id = this.grantedTo.id.toString(),
                    type = this.grantedTo.javaClass.kotlin.getFullName()
                )
            )
        ),
    )
)

fun AuthorizationGrantDbEntity.toGetAuthorizationGrantResponse(): AuthorizationGrantResponse {
    val attributes = GrantResponseAttributes(
        status = this.grantStatus.toString(),
        grantedAt = this.grantedAt.toString(),
        validFrom = this.validFrom.toString(),
        validTo = this.validTo.toString()
    )

    val relationships = GrantRelationships(
        grantedFor = JsonApiRelationshipToOne(
            data = JsonApiRelationshipData(
                id = this.grantedFor,
                type = "Person"
            )
        ),
        grantedBy = JsonApiRelationshipToOne(
            data = JsonApiRelationshipData(
                id = this.grantedBy,
                type = "Person"
            )
        ),
        grantedTo = JsonApiRelationshipToOne(
            data = JsonApiRelationshipData(
                id = this.grantedTo,
                type = "Organization"
            )
        )
    )

    return AuthorizationGrantResponse(
        data = JsonApiResponseResourceObjectWithRelationships(
            type = "AuthorizationGrant",
            id = this.id,
            attributes = attributes,
            relationships = relationships,
        )
    )
}

fun List<AuthorizationGrantDbEntity>.toGetAuthorizationGrantsResponse(): AuthorizationGrantsResponse = AuthorizationGrantsResponse(
    data = this.map { authorizationGrant ->
        val attributes = GrantResponseAttributes(
            status = authorizationGrant.grantStatus.toString(),
            grantedAt = authorizationGrant.grantedAt.toString(),
            validFrom = authorizationGrant.validFrom.toString(),
            validTo = authorizationGrant.validTo.toString()
        )

        val relationships = GrantRelationships(
            grantedFor = JsonApiRelationshipToOne(
                data = JsonApiRelationshipData(
                    id = authorizationGrant.grantedFor,
                    type = "Person"
                )
            ),
            grantedBy = JsonApiRelationshipToOne(
                data = JsonApiRelationshipData(
                    id = authorizationGrant.grantedBy,
                    type = "Person"
                )
            ),
            grantedTo = JsonApiRelationshipToOne(
                data = JsonApiRelationshipData(
                    id = authorizationGrant.grantedTo,
                    type = "Organization"
                )
            )
        )

        JsonApiResponseResourceObjectWithRelationships(
            type = "AuthorizationGrant",
            id = authorizationGrant.id,
            attributes = attributes,
            relationships = relationships
        )
    }
)
