package no.elhub.auth.features.common.party

import arrow.core.Either
import no.elhub.auth.config.TransactionContext
import no.elhub.auth.features.common.PGEnum
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.common.RepositoryWriteError
import no.elhub.auth.features.common.currentTimeUtc
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.util.UUID

interface PartyRepository {
    suspend fun findOrInsert(type: PartyType, partyId: String): Either<RepositoryWriteError, AuthorizationPartyRecord>
    suspend fun find(id: UUID): Either<RepositoryReadError, AuthorizationPartyRecord>
}

class ExposedPartyRepository(private val transactionContext: TransactionContext) : PartyRepository {

    private val logger = LoggerFactory.getLogger(ExposedPartyRepository::class.java)

    override suspend fun findOrInsert(type: PartyType, partyId: String): Either<RepositoryWriteError, AuthorizationPartyRecord> =
        transactionContext<RepositoryWriteError, AuthorizationPartyRecord>("party_repo_find_or_insert", { error ->
            logger.error("Error occurred during findOrInsert() for authorization party: ${error.message}")
            RepositoryWriteError.UnexpectedError
        }) {
            AuthorizationPartyTable
                .selectAll()
                .where { (AuthorizationPartyTable.type eq type) and (AuthorizationPartyTable.partyId eq partyId) }
                .singleOrNull()
                ?.toAuthorizationParty()
                ?: run {
                    val ins = AuthorizationPartyTable.insertIgnore {
                        it[AuthorizationPartyTable.type] = type
                        it[AuthorizationPartyTable.partyId] = partyId
                    }
                    if (ins.resultedValues?.isNotEmpty() == true) {
                        ins.resultedValues!!.first().toAuthorizationParty()
                    } else {
                        AuthorizationPartyTable
                            .selectAll()
                            .where { (AuthorizationPartyTable.type eq type) and (AuthorizationPartyTable.partyId eq partyId) }
                            .single()
                            .toAuthorizationParty()
                    }
                }
        }

    override suspend fun find(id: UUID): Either<RepositoryReadError, AuthorizationPartyRecord> =
        transactionContext("party_repo_find", { error ->
            logger.error("Error occurred during find() for authorization party: ${error.message}")
            if (error is NoSuchElementException) {
                RepositoryReadError.NotFoundError
            } else {
                RepositoryReadError.UnexpectedError
            }
        }) {
            AuthorizationPartyTable
                .selectAll()
                .where { AuthorizationPartyTable.id eq id }
                .singleOrNull()
                ?.toAuthorizationParty()
                ?: throw NoSuchElementException("Party not found: $id")
        }
}

object AuthorizationPartyTable : UUIDTable("auth.authorization_party") {
    val type = customEnumeration(
        name = "type",
        sql = "authorization_party_type",
        fromDb = { value -> PartyType.valueOf(value as String) },
        toDb = { enumValue -> PGEnum("authorization_party_type", enumValue) }
    )

    val partyId = varchar("party_id", 255)
    val createdAt = timestampWithTimeZone("created_at").default(currentTimeUtc())

    init {
        index(isUnique = true, columns = arrayOf(type, partyId))
    }
}

data class AuthorizationPartyRecord(
    val id: UUID,
    val type: PartyType,
    val resourceId: String,
    val createdAt: OffsetDateTime,
)

fun ResultRow.toAuthorizationParty() = AuthorizationPartyRecord(
    id = this[AuthorizationPartyTable.id].value,
    type = this[AuthorizationPartyTable.type],
    resourceId = this[AuthorizationPartyTable.partyId],
    createdAt = this[AuthorizationPartyTable.createdAt]
)
