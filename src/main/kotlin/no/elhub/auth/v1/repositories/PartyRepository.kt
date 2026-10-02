package no.elhub.auth.v1.repositories

import no.elhub.auth.v0.config.withTransaction
import no.elhub.auth.v0.features.common.party.AuthorizationPartyRecord
import no.elhub.auth.v0.features.common.party.AuthorizationPartyTable
import no.elhub.auth.v0.features.common.party.PartyType
import no.elhub.auth.v0.features.common.party.toAuthorizationParty
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID

interface PartyRepository {
    suspend fun find(id: UUID): AuthorizationPartyRecord
    suspend fun findOrInsert(type: PartyType, partyId: String): AuthorizationPartyRecord
}

class PartyNotFoundException(val id: UUID) : RuntimeException("Party not found: $id")

/** Uses the shared v0 party table and owns its transactions. */
class ExposedPartyRepository : PartyRepository {
    override suspend fun find(id: UUID): AuthorizationPartyRecord = withTransaction {
        AuthorizationPartyTable.selectAll()
            .where { AuthorizationPartyTable.id eq id }
            .singleOrNull()
            ?.toAuthorizationParty()
            ?: throw PartyNotFoundException(id)
    }

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
