package no.elhub.auth.features.grants.common

import arrow.core.Either
import arrow.core.raise.either
import no.elhub.auth.features.common.RepositoryError
import no.elhub.auth.features.common.RepositoryWriteError
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID

interface GrantPropertiesRepository {
    fun insert(properties: List<AuthorizationGrantProperty>): Either<RepositoryError, Unit>
    fun findBy(grantId: UUID): List<AuthorizationGrantProperty>
}

class ExposedGrantPropertiesRepository : GrantPropertiesRepository {
    override fun insert(properties: List<AuthorizationGrantProperty>): Either<RepositoryError, Unit> =
        either<RepositoryWriteError, Unit> {
            if (properties.isEmpty()) return@either

            AuthorizationGrantPropertyTable.batchInsert(properties) { property ->
                this[AuthorizationGrantPropertyTable.grantId] = property.grantId
                this[AuthorizationGrantPropertyTable.key] = property.key
                this[AuthorizationGrantPropertyTable.value] = property.value
            }
        }.mapLeft { RepositoryWriteError.UnexpectedError }

    override fun findBy(grantId: UUID): List<AuthorizationGrantProperty> =
        AuthorizationGrantPropertyTable
            .selectAll()
            .where { AuthorizationGrantPropertyTable.grantId eq grantId }
            .map { it.toAuthorizationGrantProperty() }
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
