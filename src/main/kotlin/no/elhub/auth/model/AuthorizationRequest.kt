package no.elhub.auth.model

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import no.elhub.auth.utils.PGEnum
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

data class AuthorizationRequest(
    val id: String,
    val requestType: RequestType,
    val status: AuthorizationRequestStatus,
    val requestedBy: Long,
    val requestedFrom: Long,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val validTo: LocalDateTime
) {
    object Entity: UUIDTable("authorization_request") {
        val requestType = customEnumeration(
            name = "request_type",
            fromDb = { value -> RequestType.valueOf(value as String) },
            toDb = { PGEnum("authorization_request_type", it) }
        )
        val status = customEnumeration(
            name = "request_status",
            fromDb = { value -> AuthorizationRequestStatus.valueOf(value as String) },
            toDb = { PGEnum("authorization_request_status", it) }
        )
        val requestedBy = long("requested_by")
        val requestedFrom = long("requested_from")
        val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
        val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
        val validTo = datetime("valid_to")
    }

    constructor(row: ResultRow) : this(
        id = row[Entity.id].toString(),
        requestType = row[Entity.requestType],
        status = row[Entity.status],
        requestedBy = row[Entity.requestedBy],
        requestedFrom = row[Entity.requestedFrom],
        createdAt = row[Entity.createdAt].toKotlinLocalDateTime(),
        updatedAt = row[Entity.updatedAt].toKotlinLocalDateTime(),
        validTo = row[Entity.validTo].toKotlinLocalDateTime()
    )
}
