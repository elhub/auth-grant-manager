@file:OptIn(ExperimentalSerializationApi::class)

package no.elhub.auth.model

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import no.elhub.auth.utils.PGEnum
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime
import kotlin.uuid.ExperimentalUuidApi

data class AuthorizationRequest(
    val id: String,
    val requestType: RequestType,
    val requestStatus: RequestStatus,
    val requestedBy: String,
    val requestedTo: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val validTo: LocalDateTime,
    val properties: ArrayList<AuthorizationRequestProperty> = ArrayList<AuthorizationRequestProperty>()
) {

    /**
     * Entity class for the AuthorizationRequest table.
     */
    @OptIn(ExperimentalUuidApi::class)
    object Entity : UUIDTable("authorization_request") {
        // val id = uuid("id").defaultExpression(CustomFunction("gen_random_uuid()", UUIDColumnType()))
        val requestType = customEnumeration(
            name = "request_type",
            fromDb = { value -> RequestType.valueOf(value as String) },
            toDb = { PGEnum("authorization_request_type", it) }
        )
        val requestStatus = customEnumeration(
            name = "request_status",
            fromDb = { value -> RequestStatus.valueOf(value as String) },
            toDb = { PGEnum("authorization_request_status", it) }
        )
        val requestedBy = varchar("requested_by", 16)
        val requestedTo = varchar("requested_to", 16)
        val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
        val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
        val validTo = datetime("valid_to")
    }

    constructor(row: ResultRow) : this(
        id = row[Entity.id].toString(),
        requestType = row[Entity.requestType],
        requestStatus = row[Entity.requestStatus],
        requestedBy = row[Entity.requestedBy],
        requestedTo = row[Entity.requestedTo],
        createdAt = row[Entity.createdAt].toKotlinLocalDateTime(),
        updatedAt = row[Entity.updatedAt].toKotlinLocalDateTime(),
        validTo = row[Entity.validTo].toKotlinLocalDateTime(),
    )

    /**
     * Data class for the request object for AuthorizationRequest.
     */
    @Serializable
    data class Request(
        val data: Data,
    ) {

        @Serializable
        data class Data(
            @EncodeDefault
            val type: String = "AuthorizationRequest",
            val attributes: Attributes,
            val relationships: Relations,
            val meta: Meta,
        )

        @Serializable
        data class Attributes(
            val requestType: String,
        )

        @Serializable
        data class Relations(
            val requestedBy: RelationshipLink,
            val requestedTo: RelationshipLink,
        )

        @Serializable
        data class Meta(
            val contract: String,
        )
    }

    /**
     * Data class for the response object for AuthorizationRequest.
     */
    @Serializable
    data class Json(
        val data: AuthorizationRequestData,
        val meta: ResponseMeta,
        val links: ResponseLink,
    ) {
        constructor(request: AuthorizationRequest, selfLink: String) : this(
            data = AuthorizationRequestData(
                id = request.id,
                type = "AuthorizationRequest",
                attributes = AuthorizationRequestData.Attributes(
                    status = request.requestStatus,
                    createdAt = request.createdAt,
                    updatedAt = request.updatedAt,
                    validTo = request.validTo,
                ),
                relationships = AuthorizationRequestData.Relationships(
                    requestedBy = RelationshipLink(
                        data = RelationshipLink.DataLink(
                            id = request.requestedBy,
                            type = "User"
                        )
                    ),
                    requestedTo = RelationshipLink(
                        data = RelationshipLink.DataLink(
                            id = request.requestedTo,
                            type = "User"
                        )
                    )
                ),
                meta = AuthorizationRequestData.Meta(
                    contract = request.properties.find { it.key == "contract" }?.value
                )
            ),
            links = ResponseLink(selfLink),
            meta = ResponseMeta(),
        )
    }

    @Serializable
    data class AuthorizationRequestData(
        val id: String,
        val type: String,
        val attributes: Attributes,
        val relationships: Relationships,
        val meta: Meta,
    ) {
        @Serializable
        data class Attributes(
            val status: RequestStatus,
            val createdAt: LocalDateTime,
            val updatedAt: LocalDateTime,
            val validTo: LocalDateTime,
        )

        @Serializable
        data class Relationships(
            val requestedBy: RelationshipLink,
            val requestedTo: RelationshipLink,
        )

        @Serializable
        data class Meta(
            val contract: String?
        )
    }
}
