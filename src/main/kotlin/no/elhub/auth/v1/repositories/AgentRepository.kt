package no.elhub.auth.v1.repositories

import no.elhub.auth.common.config.withTransaction
import no.elhub.auth.v1.common.auth.AgentType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID
import no.elhub.auth.v0.features.common.party.AuthorizationPartyRecord as AuthorizationAgentRecord
import no.elhub.auth.v0.features.common.party.AuthorizationPartyTable as AuthorizationAgentTable
import no.elhub.auth.v0.features.common.party.toAuthorizationParty as toAuthorizationAgent

interface AgentRepository {
    suspend fun find(id: UUID): AuthorizationAgentRecord
    suspend fun findByIds(ids: List<UUID>): Map<UUID, AuthorizationAgentRecord>
    suspend fun findOrInsert(type: AgentType, agentId: String): AuthorizationAgentRecord
}

class ExposedAgentRepository : AgentRepository {
    override suspend fun findByIds(ids: List<UUID>): Map<UUID, AuthorizationAgentRecord> {
        if (ids.isEmpty()) return emptyMap()

        return withTransaction {
            AuthorizationAgentTable.selectAll()
                .where { AuthorizationAgentTable.id inList ids }
                .associate { it[AuthorizationAgentTable.id].value to it.toAuthorizationAgent() }
        }
    }

    override suspend fun find(id: UUID): AuthorizationAgentRecord =
        findByIds(listOf(id)).values.singleOrNull()
            ?: throw RepositoryReadError.NotFoundError("Agent not found: $id")

    private fun find(type: AgentType, agentId: String): AuthorizationAgentRecord? =
        AuthorizationAgentTable.selectAll()
            .where { (AuthorizationAgentTable.type eq type) and (AuthorizationAgentTable.partyId eq agentId) }
            .singleOrNull()
            ?.toAuthorizationAgent()

    override suspend fun findOrInsert(type: AgentType, agentId: String): AuthorizationAgentRecord = withTransaction {
        find(type, agentId)?.let { return@withTransaction it }

        // Another transaction may insert the same agent after our lookup.
        AuthorizationAgentTable.insertIgnore {
            it[AuthorizationAgentTable.type] = type
            it[AuthorizationAgentTable.partyId] = agentId
        }

        checkNotNull(find(type, agentId)) { "Agent not found after insert" }
    }
}
