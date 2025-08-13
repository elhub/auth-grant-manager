package no.elhub.auth.features.grants

import arrow.core.Either
import arrow.core.raise.either
import kotlinx.datetime.Instant
import no.elhub.auth.features.errors.RepositoryError
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
            id = row[AuthorizationParty.Table.id].value,
            type = ElhubResource.valueOf(row[AuthorizationParty.Table.type].toString()),
            descriptor = row[AuthorizationParty.Table.descriptor],
            createdAt = Instant.parse(row[AuthorizationParty.Table.createdAt].toString())
        )

    fun findAll(): Either<RepositoryError, GrantsWithParties> = either {
        transaction {
            val grants = AuthorizationGrant.Table
                .selectAll()
                .map(::AuthorizationGrant)
            val partyIds = grants.flatMap { listOf(it.grantedFor, it.grantedBy, it.grantedTo) }.toSet()
            val parties = AuthorizationParty.Table
                .selectAll()
                .where { AuthorizationParty.Table.id inList partyIds }
                .associate { row ->
                    val party = mapRowToParty(row)
                    party.id to party
                }

            val missingPartyIds = partyIds.filter { it !in parties }
            if (missingPartyIds.isNotEmpty()) {
                logger.error("Missing parties for grants: $missingPartyIds")
                raise(RepositoryError.AuthorizationPartyNotFound)
            }

            GrantsWithParties(grants, parties)
        }
    }

    fun findById(grantId: UUID): Either<RepositoryError, GrantWithParties> = either {
        transaction {
            val grant = AuthorizationGrant.Table
                .selectAll()
                .where { AuthorizationGrant.Table.id eq grantId }
                .singleOrNull()
                ?.let { AuthorizationGrant(it) }
                ?: run {
                    logger.error("Authorization grant not found for id=$grantId")
                    raise(RepositoryError.AuthorizationNotFound)
                }

            val partyIds = listOf(grant.grantedFor, grant.grantedBy, grant.grantedTo)
            val parties = AuthorizationParty.Table
                .selectAll()
                .where { AuthorizationParty.Table.id inList partyIds }
                .associate { row ->
                    val party = mapRowToParty(row)
                    party.id to party
                }
            GrantWithParties(grant, parties)
        }
    }

    fun findScopesById(grantId: UUID): Either<RepositoryError, List<AuthorizationScope>> = either {
        transaction {
            val scopes = AuthorizationGrant.Table
                .selectAll()
                .where { AuthorizationGrant.Table.id eq grantId }
                .singleOrNull()
                ?.let {
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
                } ?: run {
                logger.error("Scope not found for authorization grant with id=$grantId")
                raise(RepositoryError.AuthorizationNotFound)
            }
            scopes
        }
    }
}
