package no.elhub.auth.data.persist.repositories

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.datetime.Instant
import no.elhub.auth.data.persist.tables.AuthorizationGrantTable
import no.elhub.auth.data.persist.tables.AuthorizationPartyTable
import no.elhub.auth.data.persist.tables.AuthorizationScopeTable
import no.elhub.auth.data.persist.tables.toAuthorizationGrant
import no.elhub.auth.domain.grant.AuthorizationGrant
import no.elhub.auth.domain.grant.AuthorizationGrantProblem
import no.elhub.auth.domain.shared.AuthorizationParty
import no.elhub.auth.domain.shared.AuthorizationScope
import no.elhub.auth.domain.shared.ElhubResource
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.util.*
import no.elhub.auth.data.persist.tables.AuthorizationGrantScopeTable

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
            id = row[AuthorizationPartyTable.id].value,
            type = ElhubResource.valueOf(row[AuthorizationPartyTable.type].toString()),
            descriptor = row[AuthorizationPartyTable.descriptor],
            createdAt = Instant.parse(row[AuthorizationPartyTable.createdAt].toString())
        )

    fun findAll(): Either<AuthorizationGrantProblem, GrantsWithParties> = try {
        transaction {
            // fetch all grants
            val grants = AuthorizationGrantTable
                .selectAll()
                .toList()
                .map { it.toAuthorizationGrant() }

            // collect all unique party IDs reference by the grants
            val partyIds = grants.flatMap { listOf(it.grantedFor, it.grantedBy, it.grantedTo) }.toSet()

            // fetch all parties in one query and map by ID
            val parties = AuthorizationPartyTable
                .selectAll()
                .where { AuthorizationPartyTable.id inList partyIds }
                .associate { row ->
                    val party = mapRowToParty(row)
                    party.id to party
                }

            GrantsWithParties(grants, parties)
        }.right()
    } catch (sqlEx: SQLException) {
        logger.error("SQL error occurred during fetch all grants and parties: ${sqlEx.message}")
        AuthorizationGrantProblem.DataBaseError.left()
    } catch (exp: Exception) {
        logger.error("Unknown error occurred during fetch all grants and parties: ${exp.message}")
        AuthorizationGrantProblem.UnexpectedError.left()
    }

    fun findById(grantId: UUID): Either<AuthorizationGrantProblem, GrantWithParties> =
        try {
            val result = transaction {
                // fetch grant
                val grant = AuthorizationGrantTable
                    .selectAll()
                    .where { AuthorizationGrantTable.id eq grantId }
                    .singleOrNull()?.toAuthorizationGrant()

                if (grant == null) {
                    null // signal NotFoundError after transaction
                } else {
                    // fetch all parties in one query and map by ID
                    val partyIds =
                        grant.let { listOf(grant.grantedFor, it.grantedBy, grant.grantedTo).toSet() } ?: emptySet()
                    val parties = AuthorizationPartyTable
                        .selectAll()
                        .where { AuthorizationPartyTable.id inList partyIds }
                        .associate { row ->
                            val party = mapRowToParty(row)
                            party.id to party
                        }

                    GrantWithParties(grant, parties)
                }
            }
            result?.right() ?: AuthorizationGrantProblem.NotFoundError.left()
        } catch (sqlEx: SQLException) {
            logger.error("SQL error occurred during fetch grant by id': ${sqlEx.message}")
            AuthorizationGrantProblem.DataBaseError.left()
        } catch (exp: Exception) {
            logger.error("Unknown error occurred during fetch grant by id: ${exp.message}")
            AuthorizationGrantProblem.UnexpectedError.left()
        }

    fun findScopesById(grantId: UUID): Either<AuthorizationGrantProblem, List<AuthorizationScope>> = try {
        transaction {
            // check if grant exist
            AuthorizationGrantTable
                .selectAll()
                .where { AuthorizationGrantTable.id eq grantId }
                .singleOrNull()
                ?.let {
                    // fetch all related scopes via join if grant exists
                    (AuthorizationGrantScopeTable innerJoin AuthorizationScopeTable)
                        .selectAll()
                        .where { AuthorizationGrantScopeTable.authorizationGrantId eq grantId }
                        .map { row ->
                            AuthorizationScope(
                                id = row[AuthorizationScopeTable.id].value,
                                authorizedResourceId = row[AuthorizationScopeTable.authorizedResourceId],
                                authorizedResourceType = row[AuthorizationScopeTable.authorizedResourceType],
                                permissionType = row[AuthorizationScopeTable.permissionType],
                                createdAt = Instant.parse(row[AuthorizationScopeTable.createdAt].toString())
                            )
                        }
                }
        }?.right() ?: AuthorizationGrantProblem.NotFoundError.left()
    } catch (sqlEx: SQLException) {
        logger.error("SQL error occurred during fetch scope by id': ${sqlEx.message}")
        AuthorizationGrantProblem.DataBaseError.left()
    } catch (exp: Exception) {
        logger.error("Unknown error occurred during fetch scope by id: ${exp.message}")
        AuthorizationGrantProblem.UnexpectedError.left()
    }
}
