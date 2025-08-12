package no.elhub.auth.data.persist.tables

import kotlinx.datetime.toKotlinLocalDateTime
import no.elhub.auth.domain.request.AuthorizationRequestProperty
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

object AuthorizationRequestPropertyTable : Table("authorization_request_property") {
    val authorizationRequestId = uuid("authorization_request_id")
    val key = varchar("key", 64)
    val value = text("value")
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
}

fun ResultRow.toAuthorizationProperty(): AuthorizationRequestProperty = AuthorizationRequestProperty(
    this[AuthorizationRequestPropertyTable.authorizationRequestId].toString(),
    this[AuthorizationRequestPropertyTable.key],
    this[AuthorizationRequestPropertyTable.value],
    this[AuthorizationRequestPropertyTable.createdAt].toKotlinLocalDateTime()
)
