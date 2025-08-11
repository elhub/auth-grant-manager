package no.elhub.auth.grantmanager.presentation.model

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

data class AuthorizationRequestProperty(
        val authorizationRequestId: String,
        val key: String,
        val value: String,
        val createdAt: LocalDateTime
) {
    /** Entity class for the AuthorizationRequest table. */
    object Entity : Table("authorization_request_property") {
        val authorizationRequestId = uuid("authorization_request_id")
        val key = varchar("key", 64)
        val value = text("value")
        val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    }

    constructor(
            row: ResultRow
    ) : this(
            row[Entity.authorizationRequestId].toString(),
            row[Entity.key],
            row[Entity.value],
            row[Entity.createdAt].toKotlinLocalDateTime()
    )
}
