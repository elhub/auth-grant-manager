package no.elhub.auth.features.requests.common

import arrow.core.Either
import arrow.core.raise.either
import no.elhub.auth.features.common.RepositoryError
import no.elhub.auth.features.common.RepositoryWriteError
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID

interface RequestPropertiesRepository {
    fun insert(properties: List<AuthorizationRequestProperty>): Either<RepositoryError, Unit>
    fun findBy(requestId: UUID): List<AuthorizationRequestProperty>
}

class ExposedRequestPropertiesRepository : RequestPropertiesRepository {

    override fun insert(properties: List<AuthorizationRequestProperty>): Either<RepositoryError, Unit> =
        either<RepositoryWriteError, Unit> {
            if (properties.isEmpty()) return@either

            AuthorizationRequestPropertyTable.batchInsert(properties) { property ->
                this[AuthorizationRequestPropertyTable.requestId] = property.requestId
                this[AuthorizationRequestPropertyTable.key] = property.key
                this[AuthorizationRequestPropertyTable.value] = property.value
            }
        }.mapLeft { RepositoryWriteError.UnexpectedError }

    override fun findBy(requestId: UUID): List<AuthorizationRequestProperty> =
        AuthorizationRequestPropertyTable
            .selectAll()
            .where { AuthorizationRequestPropertyTable.requestId eq requestId }
            .map { it.toAuthorizationRequestProperty() }
}

object AuthorizationRequestPropertyTable : Table("auth.authorization_request_property") {
    val requestId = javaUUID("authorization_request_id").references(AuthorizationRequestTable.id)
    val key = varchar("key", 64)
    val value = text("value")
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
}

private fun ResultRow.toAuthorizationRequestProperty() = AuthorizationRequestProperty(
    requestId = this[AuthorizationRequestPropertyTable.requestId],
    key = this[AuthorizationRequestPropertyTable.key],
    value = this[AuthorizationRequestPropertyTable.value]
)
