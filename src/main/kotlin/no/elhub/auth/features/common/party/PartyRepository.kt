package no.elhub.auth.features.common.party

import arrow.core.Either
import no.elhub.auth.features.common.PGEnum
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.common.RepositoryWriteError
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.selectAll
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

interface PartyRepository {
    fun findOrInsert(
        type: PartyType,
        partyId: String,
    ): Either<RepositoryWriteError, AuthorizationPartyRecord>

    fun find(id: UUID): Either<RepositoryReadError, AuthorizationPartyRecord>
}

class ExposedPartyRepository : PartyRepository {
    private val logger = LoggerFactory.getLogger(ExposedPartyRepository::class.java)

    override fun findOrInsert(
        type: PartyType,
        partyId: String,
    ): Either<RepositoryWriteError, AuthorizationPartyRecord> =
        Either
            .catch {
                AuthorizationPartyTable
                    // look in the table where type == given AND resource_id = given
                    .selectAll()
                    .where { (AuthorizationPartyTable.type eq type) and (AuthorizationPartyTable.partyId eq partyId) }
                    .singleOrNull()
                    ?.toAuthorizationParty() // return if found
                    ?: run {
                        // try to insert a new row -> ignore if someone else is inserting the same type
                        val ins =
                            AuthorizationPartyTable.insertIgnore {
                                it[AuthorizationPartyTable.type] = type
                                it[AuthorizationPartyTable.partyId] = partyId
                            }
                        if (ins.resultedValues?.isNotEmpty() == true) {
                            ins.resultedValues!!.first().toAuthorizationParty() // return if created
                        } else {
                            // run the same select again if the insert was ignored
                            AuthorizationPartyTable
                                .selectAll()
                                .where { (AuthorizationPartyTable.type eq type) and (AuthorizationPartyTable.partyId eq partyId) }
                                .single()
                                .toAuthorizationParty()
                        }
                    }
            }.mapLeft { error ->
                logger.error("Error occurred during findOrInsert() for authorization grant: ${error.message}")
                RepositoryWriteError.UnexpectedError
            }

    override fun find(id: UUID): Either<RepositoryReadError, AuthorizationPartyRecord> =
        Either
            .catch {
                AuthorizationPartyTable
                    .selectAll()
                    .where { AuthorizationPartyTable.id eq id }
                    .singleOrNull()
                    ?.toAuthorizationParty()
                    ?: throw NoSuchElementException("Party not found: $id")
            }.mapLeft { error ->
                logger.error("Error occurred during find() for authorization grant: ${error.message}")
                if (error is NoSuchElementException) {
                    RepositoryReadError.NotFoundError
                } else {
                    RepositoryReadError.UnexpectedError
                }
            }
}

object AuthorizationPartyTable : UUIDTable("auth.authorization_party") {
    val type =
        customEnumeration(
            name = "type",
            sql = "authorization_party_type",
            fromDb = { value -> PartyType.valueOf(value as String) },
            toDb = { enumValue -> PGEnum("authorization_party_type", enumValue) },
        )

    val partyId = varchar("party_id", 255)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }

    init {
        index(isUnique = true, columns = arrayOf(type, partyId))
    }
}

data class AuthorizationPartyRecord(
    val id: UUID,
    val type: PartyType,
    val resourceId: String,
    val createdAt: Instant,
)

fun ResultRow.toAuthorizationParty() =
    AuthorizationPartyRecord(
        id = this[AuthorizationPartyTable.id].value,
        type = this[AuthorizationPartyTable.type],
        resourceId = this[AuthorizationPartyTable.partyId],
        createdAt = this[AuthorizationPartyTable.createdAt],
    )
