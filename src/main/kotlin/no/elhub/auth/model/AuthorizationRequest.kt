@file:OptIn(ExperimentalSerializationApi::class)

package no.elhub.auth.model

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.serialization.ExperimentalSerializationApi
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
}
