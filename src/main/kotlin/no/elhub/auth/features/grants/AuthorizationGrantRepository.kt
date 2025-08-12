package no.elhub.auth.features.grants

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.datetime.Instant
import no.elhub.auth.features.errors.DomainError
import no.elhub.auth.model.AuthorizationGrant
import no.elhub.auth.model.AuthorizationGrantScopes
import no.elhub.auth.model.AuthorizationParty
import no.elhub.auth.model.AuthorizationScope
import no.elhub.auth.model.AuthorizationScopes
import no.elhub.auth.model.ElhubResource
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.UUID

object AuthorizationGrantRepository {

    private val logger = LoggerFactory.getLogger(AuthorizationGrantRepository::class.java)

    data class GrantsWithParties(
        val grants: List<AuthorizationGrant>,
        val parties: Map<Long, AuthorizationParty>
    )

    data class GrantWithParties(
        val grant: AuthorizationGrant?,
        val parties: Map<Long, AuthorizationParty>
    )

    private fun mapRowToParty(row: ResultRow): AuthorizationParty =
        AuthorizationParty(
            id = row[AuthorizationParty.Entity.id].value,
            type = ElhubResource.valueOf(row[AuthorizationParty.Entity.type].toString()),
            descriptor = row[AuthorizationParty.Entity.descriptor],
            createdAt = Instant.parse(row[AuthorizationParty.Entity.createdAt].toString())
        )

    fun findAll(): Either<DomainError, GrantsWithParties> = try {
        transaction {
            // fetch all grants
            val grants = AuthorizationGrant.Entity
                .selectAll()
                .map(::AuthorizationGrant)

            // collect all unique party IDs reference by the grants
            val partyIds = grants.flatMap { listOf(it.grantedFor, it.grantedBy, it.grantedTo) }.toSet()

            // fetch all parties in one query and map by ID
            val parties = AuthorizationParty.Entity
                .selectAll()
                .where { AuthorizationParty.Entity.id inList partyIds }
                .associate { row ->
                    val party = mapRowToParty(row)
                    party.id to party
                }

            GrantsWithParties(grants, parties)
        }.right()
    } catch (e: Exception) {
        logger.error("Unknown error occurred during fetch all grants and parties: ${e.message}")
        DomainError.RepositoryError.Unexpected(e).left()
    }

    fun findById(grantId: UUID): Either<DomainError, GrantWithParties> =
        try {
            val result = transaction {
                // fetch grant
                val grant = AuthorizationGrant.Entity
                    .selectAll()
                    .where { AuthorizationGrant.Entity.id eq grantId }
                    .singleOrNull()
                    ?.let { AuthorizationGrant(it) }

                if (grant == null) {
                    null // signal NotFoundError after transaction
                } else {
                    // fetch all parties in one query and map by ID
                    val partyIds = grant.let { listOf(grant.grantedFor, it.grantedBy, grant.grantedTo).toSet() } ?: emptySet()
                    val parties = AuthorizationParty.Entity
                        .selectAll()
                        .where { AuthorizationParty.Entity.id inList partyIds }
                        .associate { row ->
                            val party = mapRowToParty(row)
                            party.id to party
                        }

                    GrantWithParties(grant, parties)
                }
            }
            result?.right() ?: DomainError.RepositoryError.AuthorizationNotFound.left()
        } catch (e: Exception) {
            logger.error("Unknown error occurred during fetch grant by id: ${e.message}")
            DomainError.RepositoryError.Unexpected(e).left()
        }

    fun findScopesById(grantId: UUID): Either<DomainError, List<AuthorizationScope>> = try {
        transaction {
            // check if grant exist
            AuthorizationGrant.Entity
                .selectAll()
                .where { AuthorizationGrant.Entity.id eq grantId }
                .singleOrNull()
                ?.let {
                    // fetch all related scopes via join if grant exists
                    (AuthorizationGrantScopes innerJoin AuthorizationScopes)
                        .selectAll()
                        .where { AuthorizationGrantScopes.authorizationGrantId eq grantId }
                        .map { row ->
                            AuthorizationScope(
                                id = row[AuthorizationScopes.id].value,
                                authorizedResourceId = row[AuthorizationScopes.authorizedResourceId],
                                authorizedResourceType = row[AuthorizationScopes.authorizedResourceType],
                                permissionType = row[AuthorizationScopes.permissionType],
                                createdAt = Instant.parse(row[AuthorizationScopes.createdAt].toString())
                            )
                        }
                }
        }?.right() ?: DomainError.RepositoryError.AuthorizationNotFound.left()
    } catch (e: Exception) {
        logger.error("Unknown error occurred during fetch scope by id: ${e.message}")
        DomainError.RepositoryError.Unexpected(e).left()
    }
}
