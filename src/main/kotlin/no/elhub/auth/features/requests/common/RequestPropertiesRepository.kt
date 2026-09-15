package no.elhub.auth.features.requests.common

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import no.elhub.auth.config.withTransaction
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.json.jsonb
import java.util.UUID

interface RequestPropertiesRepository {
    suspend fun insert(properties: List<AuthorizationRequestProperty>)
    fun findBy(requestId: UUID): List<AuthorizationRequestProperty>
}

class ExposedRequestPropertiesRepository : RequestPropertiesRepository {

    override suspend fun insert(properties: List<AuthorizationRequestProperty>) {
        if (properties.isEmpty()) return
        withTransaction {
            AuthorizationRequestPropertyTable.batchInsert(properties) { property ->
                this[AuthorizationRequestPropertyTable.requestId] = property.requestId
                this[AuthorizationRequestPropertyTable.key] = property.key
                this[AuthorizationRequestPropertyTable.value] = property.value
            }
        }
    }

    override fun findBy(requestId: UUID): List<AuthorizationRequestProperty> =
        AuthorizationRequestPropertyTable
            .selectAll()
            .where { AuthorizationRequestPropertyTable.requestId eq requestId }
            .map { it.toAuthorizationRequestProperty() }
}

object AuthorizationRequestPropertyTable : Table("auth.authorization_request_property") {
    val requestId = javaUUID("authorization_request_id").references(AuthorizationRequestTable.id)
    val key = varchar("key", 64)
    val value = jsonb<JsonElement>("value", Json)
}

internal fun ResultRow.toAuthorizationRequestProperty() = AuthorizationRequestProperty(
    requestId = this[AuthorizationRequestPropertyTable.requestId],
    key = this[AuthorizationRequestPropertyTable.key],
    value = this[AuthorizationRequestPropertyTable.value]
)
