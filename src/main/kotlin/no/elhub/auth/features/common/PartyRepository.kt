package no.elhub.auth.features.common

import arrow.core.Either
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

interface PartyRepository {
    fun findOrInsert(type: AuthorizationParty.ElhubResource, resourceId: String): Either<RepositoryWriteError, AuthorizationParty>
    fun find(id: UUID): Either<RepositoryReadError, AuthorizationParty>
}

class ExposedPartyRepository : PartyRepository {

    override fun findOrInsert(type: AuthorizationParty.ElhubResource, resourceId: String): Either<RepositoryWriteError, AuthorizationParty> =
        Either.catch {
            transaction {
                AuthorizationPartyTable
                    // look in the table where type == given AND resource_id = given
                    .selectAll()
                    .where { (AuthorizationPartyTable.type eq type) and (AuthorizationPartyTable.resourceId eq resourceId) }
                    .singleOrNull()
                    ?.toAuthorizationParty() // return if found
                    ?: run {
                        // try to insert a new row -> ignore if someone else is inserting the same type
                        val ins = AuthorizationPartyTable.insertIgnore {
                            it[AuthorizationPartyTable.type] = type
                            it[AuthorizationPartyTable.resourceId] = resourceId
                        }
                        if (ins.resultedValues?.isNotEmpty() == true) {
                            ins.resultedValues!!.first().toAuthorizationParty() // return if created
                        } else {
                            // run the same select again if the insert was ignored
                            AuthorizationPartyTable
                                .selectAll()
                                .where { (AuthorizationPartyTable.type eq type) and (AuthorizationPartyTable.resourceId eq resourceId) }
                                .single()
                                .toAuthorizationParty()
                        }
                    }
            }
        }.mapLeft { RepositoryWriteError.UnexpectedError }

    override fun find(id: UUID): Either<RepositoryReadError, AuthorizationParty> =
        Either
            .catch {
                transaction {
                    AuthorizationPartyTable
                        .selectAll()
                        .where { AuthorizationPartyTable.id eq id }
                        .singleOrNull()
                        ?.toAuthorizationParty()
                        ?: throw NoSuchElementException("Party not found: $id")
                }
            }
            .mapLeft {
                if (it is NoSuchElementException) {
                    RepositoryReadError.NotFoundError
                } else {
                    RepositoryReadError.UnexpectedError
                }
            }
}

object AuthorizationPartyTable : UUIDTable("auth.authorization_party") {
    val type = customEnumeration(
        name = "type",
        fromDb = { value -> AuthorizationParty.ElhubResource.valueOf(value as String) },
        toDb = { PGEnum("elhub_resource", it) },
    )

    val resourceId = varchar("resource_id", 255)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }

    init {
        index(isUnique = true, columns = arrayOf(type, resourceId))
    }
}

fun ResultRow.toAuthorizationParty() = AuthorizationParty(
    id = this[AuthorizationPartyTable.id].value,
    type = this[AuthorizationPartyTable.type],
    resourceId = this[AuthorizationPartyTable.resourceId],
    createdAt = this[AuthorizationPartyTable.createdAt]
)
