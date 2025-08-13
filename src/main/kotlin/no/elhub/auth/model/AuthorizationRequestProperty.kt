package no.elhub.auth.model

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

typealias ExposedTable = org.jetbrains.exposed.sql.Table
data class AuthorizationRequestProperty(
    val authorizationRequestId: String,
    val key: String,
    val value: String,
    val createdAt: LocalDateTime

) {
    /**
     * Entity class for the AuthorizationRequest table.
     */
    object Table : ExposedTable("authorization_request_property") {
        val authorizationRequestId = uuid("authorization_request_id")
        val key = varchar("key", 64)
        val value = text("value")
        val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    }

    constructor(row: ResultRow) : this(
        row[Table.authorizationRequestId].toString(),
        row[Table.key],
        row[Table.value],
        row[Table.createdAt].toKotlinLocalDateTime()
    )
}
