package no.elhub.auth.features.grants.common

import arrow.core.Either
import no.elhub.auth.config.TransactionContext
import no.elhub.auth.features.common.RepositoryError
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.common.RepositoryWriteError
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID

interface GrantPropertiesRepository {
    suspend fun insert(properties: List<AuthorizationGrantProperty>): Either<RepositoryError, Unit>
    suspend fun findBy(grantId: UUID): List<AuthorizationGrantProperty>
}

class ExposedGrantPropertiesRepository(
    private val transactionContext: TransactionContext,
) : GrantPropertiesRepository {

    override suspend fun insert(properties: List<AuthorizationGrantProperty>): Either<RepositoryError, Unit> =
        transactionContext(
            "db_operations",
            "GrantPropertiesRepository",
            "insert",
            { RepositoryWriteError.UnexpectedError }
        ) {
            if (properties.isNotEmpty()) {
                AuthorizationGrantPropertyTable.batchInsert(properties) { property ->
                    this[AuthorizationGrantPropertyTable.grantId] = property.grantId
                    this[AuthorizationGrantPropertyTable.key] = property.key
                    this[AuthorizationGrantPropertyTable.value] = property.value
                }
            }
        }

    override suspend fun findBy(grantId: UUID): List<AuthorizationGrantProperty> =
        transactionContext(
            "db_operations",
            "GrantPropertiesRepository",
            "findby",
            { RepositoryReadError.UnexpectedError }
        ) {
            AuthorizationGrantPropertyTable
                .selectAll()
                .where { AuthorizationGrantPropertyTable.grantId eq grantId }
                .map { it.toAuthorizationGrantProperty() }
        }.fold({ emptyList() }, { it })
}

object AuthorizationGrantPropertyTable : Table("auth.authorization_grant_property") {
    val grantId = javaUUID("authorization_grant_id").references(AuthorizationGrantTable.id)
    val key = varchar("key", 64)
    val value = text("value")
}

private fun ResultRow.toAuthorizationGrantProperty() = AuthorizationGrantProperty(
    grantId = this[AuthorizationGrantPropertyTable.grantId],
    key = this[AuthorizationGrantPropertyTable.key],
    value = this[AuthorizationGrantPropertyTable.value]
)
