package no.elhub.auth.features.requests.common

import arrow.core.Either
import arrow.core.raise.either
import no.elhub.auth.features.common.RepositoryError
import no.elhub.auth.features.common.RepositoryWriteError
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

interface RequestPropertiesRepository {
    fun insert(properties: List<AuthorizationRequestProperty>): Either<RepositoryError, Unit>
    fun findBy(requestId: UUID): List<AuthorizationRequestProperty>
}

class ExposedRequestPropertiesRepository : RequestPropertiesRepository {

    override fun insert(properties: List<AuthorizationRequestProperty>): Either<RepositoryError, Unit> =
        either<RepositoryWriteError, Unit> {
            if (properties.isEmpty()) return@either

            transaction {
                AuthorizationRequestPropertyTable.batchInsert(properties) { property ->
                    this[AuthorizationRequestPropertyTable.requestId] = property.requestId
                    this[AuthorizationRequestPropertyTable.key] = property.key
                    this[AuthorizationRequestPropertyTable.value] = property.value
                }
            }
        }.mapLeft { RepositoryWriteError.UnexpectedError }

    override fun findBy(requestId: UUID): List<AuthorizationRequestProperty> =
        AuthorizationRequestPropertyTable
            .selectAll()
            .where { AuthorizationRequestPropertyTable.requestId eq requestId }
            .map { it.toAuthorizationRequestProperty() }
}

object AuthorizationRequestPropertyTable : Table("auth.authorization_request_property") {
    val requestId = uuid("authorization_request_id").references(AuthorizationRequestTable.id)
    val key = varchar("key", 64)
    val value = text("value")
    val createdAt = timestampWithTimeZone("created_at").default(OffsetDateTime.now(ZoneId.of("Europe/Oslo")))
}

private fun ResultRow.toAuthorizationRequestProperty() = AuthorizationRequestProperty(
    requestId = this[AuthorizationRequestPropertyTable.requestId],
    key = this[AuthorizationRequestPropertyTable.key],
    value = this[AuthorizationRequestPropertyTable.value]
)
