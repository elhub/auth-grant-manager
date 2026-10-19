package no.elhub.auth.v1.repositories

import no.elhub.auth.v0.config.withTransaction
import no.elhub.auth.v0.features.common.party.AuthorizationPartyRecord
import no.elhub.auth.v0.features.common.party.AuthorizationPartyTable
import no.elhub.auth.v0.features.common.party.PartyType
import no.elhub.auth.v0.features.common.party.toAuthorizationParty
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID

interface PartyRepository {
    suspend fun find(id: UUID): AuthorizationPartyRecord
    suspend fun findByIds(ids: List<UUID>): Map<UUID, AuthorizationPartyRecord>
    suspend fun findOrInsert(type: PartyType, partyId: String): AuthorizationPartyRecord
}

class ExposedPartyRepository : PartyRepository {
    override suspend fun findByIds(ids: List<UUID>): Map<UUID, AuthorizationPartyRecord> {
        if (ids.isEmpty()) return emptyMap()

        return withTransaction {
            AuthorizationPartyTable.selectAll()
                .where { AuthorizationPartyTable.id inList ids }
                .associate { it[AuthorizationPartyTable.id].value to it.toAuthorizationParty() }
        }
    }

    override suspend fun find(id: UUID): AuthorizationPartyRecord =
        findByIds(listOf(id)).values.singleOrNull()
            ?: throw RepositoryReadError.NotFoundError("Party not found: $id")

    private fun find(type: PartyType, partyId: String): AuthorizationPartyRecord? =
        AuthorizationPartyTable.selectAll()
            .where { (AuthorizationPartyTable.type eq type) and (AuthorizationPartyTable.partyId eq partyId) }
            .singleOrNull()
            ?.toAuthorizationParty()

    override suspend fun findOrInsert(type: PartyType, partyId: String): AuthorizationPartyRecord = withTransaction {
        find(type, partyId)?.let { return@withTransaction it }

        // Another transaction may insert the same party after our lookup.
        AuthorizationPartyTable.insertIgnore {
            it[AuthorizationPartyTable.type] = type
            it[AuthorizationPartyTable.partyId] = partyId
        }

        checkNotNull(find(type, partyId)) { "Party not found after insert" }
    }
}
