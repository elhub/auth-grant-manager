package no.elhub.auth.features.documents.common

import arrow.core.Either
import no.elhub.auth.features.common.PGEnum
import no.elhub.auth.features.common.RepositoryReadError
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

// TODO: this service might be used in the grant and request flows as well. Maybe move this to common folder?

interface PartyRepository {
    fun findOrCreate(type: ElhubResource, resourceId: String): Either<RepositoryReadError, AuthorizationParty>
}

class ExposedPartyRepository : PartyRepository {
    override fun findOrCreate(type: ElhubResource, resourceId: String): Either<RepositoryReadError, AuthorizationParty> =
        Either.catch {
            transaction {
                AuthorizationPartyTable
                    .selectAll()
                    .where { (AuthorizationPartyTable.type eq type) and (AuthorizationPartyTable.resourceId eq resourceId) }
                    .singleOrNull()
                    ?.toAuthorizationParty()
                    ?: run {
                        val ins = AuthorizationPartyTable.insertIgnore {
                            it[AuthorizationPartyTable.type] = type
                            it[AuthorizationPartyTable.resourceId] = resourceId
                        }
                        if (ins.resultedValues?.isNotEmpty() == true) {
                            ins.resultedValues!!.first().toAuthorizationParty()
                        } else {
                            AuthorizationPartyTable
                                .selectAll()
                                .where { (AuthorizationPartyTable.type eq type) and (AuthorizationPartyTable.resourceId eq resourceId) }
                                .single()
                                .toAuthorizationParty()
                        }
                    }
            }
        }.mapLeft { RepositoryReadError.UnexpectedError }

    private fun ResultRow.toAuthorizationParty() = AuthorizationParty(
        id = this[AuthorizationPartyTable.id].value,
        type = this[AuthorizationPartyTable.type],
        resourceId = this[AuthorizationPartyTable.resourceId],
        createdAt = this[AuthorizationPartyTable.createdAt]
    )
}

object AuthorizationPartyTable : UUIDTable("auth.authorization_party") {
    val type =
        customEnumeration(
            name = "type",
            fromDb = { value -> ElhubResource.valueOf(value as String) },
            toDb = { PGEnum("elhub_resource", it) }
        )
    val resourceId = varchar("resource_id", length = 256)
    val createdAt = timestamp("created_at").clientDefault { java.time.Instant.now() }

    init {
        index(isUnique = true, columns = arrayOf(type, resourceId))
    }
}
