package no.elhub.auth.data.exposed.tables

import kotlinx.datetime.toKotlinLocalDateTime
import no.elhub.auth.domain.request.AuthorizationRequest
import no.elhub.auth.domain.request.RequestStatus
import no.elhub.auth.domain.request.RequestType
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

object AuthorizationRequestTable  : UUIDTable("authorization_request") {
    val requestType = customEnumeration(
        name = "request_type",
        fromDb = { value -> RequestType.valueOf(value as String) },
        toDb = { PGEnum("authorization_request_type", it) }
    )
    val status = customEnumeration(
        name = "request_status",
        fromDb = { value -> RequestStatus.valueOf(value as String) },
        toDb = { PGEnum("authorization_request_status", it) }
    )
    val requestedBy = varchar("requested_by", 16)
    val requestedFrom = varchar("requested_from", 16)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
    val validTo = datetime("valid_to")
}


fun ResultRow.toAuthorizationRequest() = AuthorizationRequest(
        id = this[AuthorizationRequestTable.id].toString(),
        requestType = this[AuthorizationRequestTable.requestType],
        status = this[AuthorizationRequestTable.status],
        requestedBy = this[AuthorizationRequestTable.requestedBy],
        requestedFrom = this[AuthorizationRequestTable.requestedFrom],
        createdAt = this[AuthorizationRequestTable.createdAt].toKotlinLocalDateTime(),
        updatedAt = this[AuthorizationRequestTable.updatedAt].toKotlinLocalDateTime(),
        validTo = this[AuthorizationRequestTable.validTo].toKotlinLocalDateTime()
)
