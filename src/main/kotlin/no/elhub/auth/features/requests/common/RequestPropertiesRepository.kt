package no.elhub.auth.features.requests.common

import arrow.core.Either
import arrow.core.raise.either
import no.elhub.auth.features.common.RepositoryError
import no.elhub.auth.features.common.RepositoryWriteError
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.transactions.transaction

interface RequestPropertiesRepository {
    fun insert(properties: List<AuthorizationRequestProperty>): Either<RepositoryError, Unit>
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
}

object AuthorizationRequestPropertyTable : Table("authorization_request_property") {
    val requestId = uuid("authorization_request_id")
    val key = varchar("key", 64)
    val value = text("value")
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
}
