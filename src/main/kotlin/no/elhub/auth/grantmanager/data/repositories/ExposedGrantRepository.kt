package no.elhub.auth.grantmanager.data.repositories

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.elhub.auth.grantmanager.data.models.AuthorizationGrantDbEntity
import no.elhub.auth.grantmanager.domain.errors.RepoRetrievalError
import no.elhub.auth.grantmanager.domain.repositories.GrantRepository
import no.elhub.auth.grantmanager.domain.models.Grant
import no.elhub.auth.grantmanager.presentation.features.grants.AuthorizationGrantRepository
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.time.ZoneOffset
import java.util.UUID

class ExposedGrantRepository : GrantRepository {

    private val logger = LoggerFactory.getLogger(AuthorizationGrantRepository::class.java)

    override fun create(grant: Grant) {
        TODO("Not yet implemented")
    }

    override fun get(id: UUID): Either<RepoRetrievalError, Grant> =
        try {
            transaction {
                AuthorizationGrantDbEntity.Entity
                    .selectAll()
                    .where { AuthorizationGrantDbEntity.Entity.id eq id }
                    .singleOrNull()?.let { thing -> AuthorizationGrantDbEntity(thing).toDomainObject() }
            }?.right()
                ?: RepoRetrievalError.NotFound.left()
        } catch (sqlEx: SQLException) {
            logger.error("SQL error occurred during fetch grant by id': ${sqlEx.message}")
            RepoRetrievalError.SystemError("An SQL error occurred when attempting to fetch the grant").left()
        } catch (exp: Exception) {
            logger.error("Unknown error occurred during fetch grant by id: ${exp.message}")
            RepoRetrievalError.SystemError("An unknown error occurred when attempting to retrieve the grant").left()
        }

    override fun update(grant: Grant) {
        TODO("Not yet implemented")
    }

    override fun delete(id: UUID) {
        TODO("Not yet implemented")
    }
}

fun AuthorizationGrantDbEntity.toDomainObject() = Grant(
    UUID.fromString(this.id),
    this.grantedFor,
    this.grantedBy,
    this.grantedTo,
    this.grantedAt.toInstant(ZoneOffset.UTC),
    this.validFrom.toInstant(ZoneOffset.UTC),
    this.validTo.toInstant(ZoneOffset.UTC),
)
