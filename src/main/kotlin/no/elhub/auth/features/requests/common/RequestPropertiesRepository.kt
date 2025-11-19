package no.elhub.auth.features.requests.common

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

interface RequestPropertiesRepository {
    fun insert(properties: List<AuthorizationRequestProperty>)
    fun find(requestId: UUID): List<AuthorizationRequestProperty>
}

class ExposedRequestPropertiesRepository : RequestPropertiesRepository {
    override fun insert(properties: List<AuthorizationRequestProperty>) {
        if (properties.isEmpty()) return

        transaction {
            AuthorizationRequestPropertyTable.batchInsert(properties) { property ->
                this[AuthorizationRequestPropertyTable.requestId] = property.requestId
                this[AuthorizationRequestPropertyTable.key] = property.key
                this[AuthorizationRequestPropertyTable.value] = property.value
            }
        }
    }
    override fun find(requestId: UUID): List<AuthorizationRequestProperty> =
        transaction {
            AuthorizationRequestPropertyTable
                .selectAll()
                .where { AuthorizationRequestPropertyTable.requestId eq requestId }
                .map { it.toAuthorizationRequestProperty() }
        }
}

object AuthorizationRequestPropertyTable : Table("authorization_request_property") {
    val requestId = uuid("authorization_request_id")
    val key = varchar("key", 64)
    val value = text("value")
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
}

fun ResultRow.toAuthorizationRequestProperty() = AuthorizationRequestProperty(
    requestId = this[AuthorizationRequestPropertyTable.requestId],
    key = this[AuthorizationRequestPropertyTable.key],
    value = this[AuthorizationRequestPropertyTable.value],
)
