package no.elhub.auth.v1.repositories

import no.elhub.auth.common.config.withTransaction
import no.elhub.auth.v0.features.common.PGEnum
import no.elhub.auth.v0.features.common.Page
import no.elhub.auth.v0.features.common.Pagination
import no.elhub.auth.v1.common.auth.AuthorizationAgent
import no.elhub.auth.v1.common.now
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationGrantSourceType
import no.elhub.auth.v1.domain.AuthorizationGrantStatus
import no.elhub.auth.v1.domain.AuthorizationGrantType
import no.elhub.auth.v1.domain.AuthorizationScopeConstraint
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintAttribute
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintKind
import no.elhub.auth.v1.domain.GrantAgentFilters
import no.elhub.auth.v1.domain.GrantedAuthorizationScope
import no.elhub.auth.v1.domain.MeteringPointContractType
import no.elhub.auth.v1.domain.MeteringPointId
import no.elhub.auth.v1.domain.PermissionCapability
import no.elhub.auth.v1.domain.ResourceConstraint
import no.elhub.auth.v1.domain.ResourceType
import no.elhub.auth.v1.domain.grantValidityStatus
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.compoundOr
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.updateReturning
import org.postgresql.util.PGobject
import java.util.UUID
import kotlin.time.Instant
import no.elhub.auth.v0.features.common.party.AuthorizationPartyTable as AuthorizationAgentTable
import no.elhub.auth.v0.features.grants.common.toAuthorizationParty as toAuthorizationAgent

interface GrantRepository {
    suspend fun find(grantId: UUID): AuthorizationGrant
    suspend fun findBySourceIds(
        sourceType: AuthorizationGrantSourceType,
        sourceIds: List<UUID>
    ): Map<UUID, List<AuthorizationGrant>>

    suspend fun findAll(
        agent: AuthorizationAgent,
        pagination: Pagination,
        statuses: List<AuthorizationGrantStatus>,
        agentFilters: GrantAgentFilters,
    ): Page<AuthorizationGrant>

    suspend fun insert(grant: AuthorizationGrant): AuthorizationGrant
    suspend fun update(grantId: UUID, newStatus: AuthorizationGrantStatus): AuthorizationGrant
}

class ExposedGrantRepository(
    val agentRepository: AgentRepository,
) : GrantRepository {
    override suspend fun find(grantId: UUID): AuthorizationGrant = withTransaction {
        val row = AuthorizationGrantTable.selectAll()
            .where { AuthorizationGrantTable.id eq grantId }
            .singleOrNull()
            ?: throw RepositoryReadError.NotFoundError("Grant not found: $grantId")

        row.toAuthorizationGrant(
            principal = agentRepository.find(row[AuthorizationGrantTable.principal]).toAuthorizationAgent(),
            grantedBy = agentRepository.find(row[AuthorizationGrantTable.grantedBy]).toAuthorizationAgent(),
            grantedTo = agentRepository.find(row[AuthorizationGrantTable.grantedTo]).toAuthorizationAgent(),
            approvedBy = agentRepository.find(row[AuthorizationGrantTable.approvedBy]).toAuthorizationAgent(),
            scopes = getScopesByGrantId(listOf(grantId))[grantId].orEmpty(),
            now = now(),
        )
    }

    override suspend fun findBySourceIds(
        sourceType: AuthorizationGrantSourceType,
        sourceIds: List<UUID>,
    ): Map<UUID, List<AuthorizationGrant>> {
        if (sourceIds.isEmpty()) return emptyMap()

        return withTransaction {
            val rows = AuthorizationGrantTable.selectAll()
                .where {
                    (AuthorizationGrantTable.sourceType eq sourceType) and (AuthorizationGrantTable.sourceId inList sourceIds)
                }
                .toList()

            if (rows.isEmpty()) return@withTransaction emptyMap()

            val agentIds = rows.flatMap {
                listOf(
                    it[AuthorizationGrantTable.principal],
                    it[AuthorizationGrantTable.grantedBy],
                    it[AuthorizationGrantTable.grantedTo],
                    it[AuthorizationGrantTable.approvedBy],
                )
            }.distinct()
            val agents = agentRepository.findByIds(agentIds)
                .mapValues { (_, agent) -> agent.toAuthorizationAgent() }
            val scopes = getScopesByGrantId(rows.map { it[AuthorizationGrantTable.id].value })
            val now = now()

            rows.map { row ->
                row.toAuthorizationGrant(
                    principal = agents.getValue(row[AuthorizationGrantTable.principal]),
                    grantedBy = agents.getValue(row[AuthorizationGrantTable.grantedBy]),
                    grantedTo = agents.getValue(row[AuthorizationGrantTable.grantedTo]),
                    approvedBy = agents.getValue(row[AuthorizationGrantTable.approvedBy]),
                    scopes = scopes[row[AuthorizationGrantTable.id].value].orEmpty(),
                    now = now,
                )
            }.groupBy { it.sourceId }
        }
    }

    override suspend fun findAll(
        agent: AuthorizationAgent,
        pagination: Pagination,
        statuses: List<AuthorizationGrantStatus>,
        agentFilters: GrantAgentFilters,
    ): Page<AuthorizationGrant> = withTransaction {
        val agentId = agentRepository.findOrInsert(agent.type, agent.id).id
        val now = now()
        val agentCondition = listOf(
            AuthorizationGrantTable.principal eq agentId,
            AuthorizationGrantTable.grantedTo eq agentId,
            AuthorizationGrantTable.grantedBy eq agentId,
        ).compoundOr()

        val statusConditions = statuses.map { status ->
            when (status) {
                AuthorizationGrantStatus.Pending -> (AuthorizationGrantTable.status eq DatabaseGrantStatus.Active) and
                    (AuthorizationGrantTable.validFrom greater now) and
                    (AuthorizationGrantTable.validTo.isNull() or (AuthorizationGrantTable.validTo greater now))

                AuthorizationGrantStatus.Active -> (AuthorizationGrantTable.status eq DatabaseGrantStatus.Active) and
                    (AuthorizationGrantTable.validFrom lessEq now) and
                    (AuthorizationGrantTable.validTo.isNull() or (AuthorizationGrantTable.validTo greater now))

                AuthorizationGrantStatus.Expired -> (AuthorizationGrantTable.status eq DatabaseGrantStatus.Active) and
                    (AuthorizationGrantTable.validTo lessEq now)

                AuthorizationGrantStatus.Revoked -> AuthorizationGrantTable.status eq DatabaseGrantStatus.Revoked

                AuthorizationGrantStatus.Exhausted -> AuthorizationGrantTable.status eq DatabaseGrantStatus.Exhausted
            }
        }

        val agentFilterConditions = listOf(
            AuthorizationGrantTable.grantedBy to agentFilters.grantedBy,
            AuthorizationGrantTable.principal to agentFilters.principal,
            AuthorizationGrantTable.approvedBy to agentFilters.approvedBy,
            AuthorizationGrantTable.grantedTo to agentFilters.grantedTo,
        ).mapNotNull { (column, agents) ->
            if (agents.isEmpty()) return@mapNotNull null

            val matchingAgents = AuthorizationAgentTable
                .select(AuthorizationAgentTable.id)
                .where {
                    agents.map { agent ->
                        (AuthorizationAgentTable.type eq agent.type) and (AuthorizationAgentTable.partyId eq agent.id)
                    }.compoundOr()
                }

            column inSubQuery matchingAgents
        }

        val whereClause = listOfNotNull(
            agentCondition,
            statusConditions.takeIf { it.isNotEmpty() }?.compoundOr(),
        ).plus(agentFilterConditions).compoundAnd()

        val totalItems = AuthorizationGrantTable.selectAll().where { whereClause }.count()
        val rows = AuthorizationGrantTable.selectAll()
            .where { whereClause }
            .orderBy(AuthorizationGrantTable.createdAt to SortOrder.DESC, AuthorizationGrantTable.id to SortOrder.DESC)
            .limit(pagination.size)
            .offset(pagination.offset)
            .toList()

        if (rows.isEmpty()) return@withTransaction Page(emptyList(), totalItems, pagination)

        val agentIds = rows.flatMap {
            listOf(
                it[AuthorizationGrantTable.principal],
                it[AuthorizationGrantTable.grantedBy],
                it[AuthorizationGrantTable.grantedTo],
                it[AuthorizationGrantTable.approvedBy],
            )
        }.distinct()
        val agents = agentRepository.findByIds(agentIds)
            .mapValues { (_, agent) -> agent.toAuthorizationAgent() }
        val scopes = getScopesByGrantId(rows.map { it[AuthorizationGrantTable.id].value })

        val items = rows.map { row ->
            row.toAuthorizationGrant(
                principal = agents.getValue(row[AuthorizationGrantTable.principal]),
                grantedBy = agents.getValue(row[AuthorizationGrantTable.grantedBy]),
                grantedTo = agents.getValue(row[AuthorizationGrantTable.grantedTo]),
                approvedBy = agents.getValue(row[AuthorizationGrantTable.approvedBy]),
                scopes = scopes[row[AuthorizationGrantTable.id].value].orEmpty(),
                now = now,
            )
        }
        Page(items, totalItems, pagination)
    }

    override suspend fun insert(grant: AuthorizationGrant): AuthorizationGrant = withTransaction {
        val principal = agentRepository.findOrInsert(grant.principal.type, grant.principal.id)
        val grantedBy = agentRepository.findOrInsert(grant.grantedBy.type, grant.grantedBy.id)
        val grantedTo = agentRepository.findOrInsert(grant.grantedTo.type, grant.grantedTo.id)
        val approvedBy = agentRepository.findOrInsert(grant.approvedBy.type, grant.approvedBy.id)

        val inserted = AuthorizationGrantTable.insert {
            it[id] = grant.id
            it[type] = grant.type
            it[sourceType] = grant.sourceType
            it[sourceId] = grant.sourceId
            it[AuthorizationGrantTable.principal] = principal.id
            it[AuthorizationGrantTable.grantedBy] = grantedBy.id
            it[AuthorizationGrantTable.grantedTo] = grantedTo.id
            it[AuthorizationGrantTable.approvedBy] = approvedBy.id
            it[status] = when (grant.status) {
                AuthorizationGrantStatus.Active, AuthorizationGrantStatus.Pending, AuthorizationGrantStatus.Expired -> DatabaseGrantStatus.Active
                AuthorizationGrantStatus.Revoked -> DatabaseGrantStatus.Revoked
                AuthorizationGrantStatus.Exhausted -> DatabaseGrantStatus.Exhausted
            }
            it[validFrom] = grant.validFrom
            it[validTo] = grant.validTo
            it[createdAt] = grant.createdAt
            it[updatedAt] = grant.updatedAt
        }

        grant.scopes.forEach { scope ->
            val scopeId = AuthorizationGrantScopeTable.insert {
                it[grantId] = grant.id
                it[capability] = scope.capability
                it[resourceType] = scope.resourceType
            }[AuthorizationGrantScopeTable.id].value

            scope.constraints.forEach { constraint ->
                AuthorizationGrantScopeConstraintTable.insert {
                    it[AuthorizationGrantScopeConstraintTable.scopeId] = scopeId
                    it[kind] = constraint.constraintKind
                    it[attribute] = constraint.attribute
                    it[value] = when (val value = constraint.value) {
                        is ResourceConstraint.MeteringPoints -> value.ids.map { id -> id.value }
                        is ResourceConstraint.ContractTypes -> value.types.map { type -> type.name }
                        is ResourceConstraint.ValidFrom -> listOf(value.value.toString())
                    }
                }
            }
        }

        inserted.resultedValues!!.single().toAuthorizationGrant(
            principal = principal.toAuthorizationAgent(),
            grantedBy = grantedBy.toAuthorizationAgent(),
            grantedTo = grantedTo.toAuthorizationAgent(),
            approvedBy = approvedBy.toAuthorizationAgent(),
            scopes = grant.scopes,
            now = now(),
        )
    }

    override suspend fun update(grantId: UUID, newStatus: AuthorizationGrantStatus): AuthorizationGrant = withTransaction {
        val row = AuthorizationGrantTable.selectAll()
            .where { AuthorizationGrantTable.id eq grantId }
            .forUpdate(ForUpdateOption.PostgreSQL.ForNoKeyUpdate)
            .singleOrNull()
            ?: throw RepositoryWriteError.NotFoundError("Grant not found: $grantId")

        val now = now()
        val status = row.toAuthorizationGrantStatus(now)
        if (status == AuthorizationGrantStatus.Expired) {
            throw RepositoryWriteError.ExpiredError("Grant $grantId has expired")
        }

        val target = when {
            newStatus == AuthorizationGrantStatus.Revoked &&
                (status == AuthorizationGrantStatus.Active || status == AuthorizationGrantStatus.Pending) -> DatabaseGrantStatus.Revoked

            newStatus == AuthorizationGrantStatus.Exhausted && status == AuthorizationGrantStatus.Active &&
                row[AuthorizationGrantTable.type] == AuthorizationGrantType.Consumable ->
                DatabaseGrantStatus.Exhausted

            else -> null
        } ?: throw RepositoryWriteError.ConflictError("Invalid transition for grant $grantId from $status to $newStatus")

        AuthorizationGrantTable.updateReturning(where = { AuthorizationGrantTable.id eq grantId }) {
            it[AuthorizationGrantTable.status] = target
            it[updatedAt] = now
        }.single().toAuthorizationGrant(
            principal = agentRepository.find(row[AuthorizationGrantTable.principal]).toAuthorizationAgent(),
            grantedBy = agentRepository.find(row[AuthorizationGrantTable.grantedBy]).toAuthorizationAgent(),
            grantedTo = agentRepository.find(row[AuthorizationGrantTable.grantedTo]).toAuthorizationAgent(),
            approvedBy = agentRepository.find(row[AuthorizationGrantTable.approvedBy]).toAuthorizationAgent(),
            scopes = getScopesByGrantId(listOf(grantId))[grantId].orEmpty(),
            now = now,
        )
    }

    private fun getScopesByGrantId(grantIds: List<UUID>): Map<UUID, List<GrantedAuthorizationScope>> =
        (AuthorizationGrantScopeTable leftJoin AuthorizationGrantScopeConstraintTable)
            .selectAll()
            .where { AuthorizationGrantScopeTable.grantId inList grantIds }
            .groupBy { it[AuthorizationGrantScopeTable.grantId] }
            .mapValues { (_, rows) ->
                rows.groupBy { it[AuthorizationGrantScopeTable.id].value }
                    .values.map { scopeRows ->
                        scopeRows.first().toGrantedAuthorizationScope(
                            constraints = scopeRows.filter { it.getOrNull(AuthorizationGrantScopeConstraintTable.id) != null }
                                .map(ResultRow::toGrantScopeConstraint),
                        )
                    }
            }
}

fun ResultRow.toAuthorizationGrant(
    principal: AuthorizationAgent,
    grantedBy: AuthorizationAgent,
    grantedTo: AuthorizationAgent,
    approvedBy: AuthorizationAgent,
    scopes: List<GrantedAuthorizationScope>,
    now: Instant,
): AuthorizationGrant = AuthorizationGrant(
    id = this[AuthorizationGrantTable.id].value,
    type = this[AuthorizationGrantTable.type],
    sourceType = this[AuthorizationGrantTable.sourceType],
    sourceId = this[AuthorizationGrantTable.sourceId],
    principal = principal,
    grantedBy = grantedBy,
    grantedTo = grantedTo,
    approvedBy = approvedBy,
    status = toAuthorizationGrantStatus(now),
    validFrom = this[AuthorizationGrantTable.validFrom],
    validTo = this[AuthorizationGrantTable.validTo],
    createdAt = this[AuthorizationGrantTable.createdAt],
    updatedAt = this[AuthorizationGrantTable.updatedAt],
    scopes = scopes,
)

private fun ResultRow.toAuthorizationGrantStatus(now: Instant): AuthorizationGrantStatus =
    when (this[AuthorizationGrantTable.status]) {
        DatabaseGrantStatus.Revoked -> AuthorizationGrantStatus.Revoked

        DatabaseGrantStatus.Exhausted -> AuthorizationGrantStatus.Exhausted

        DatabaseGrantStatus.Active -> grantValidityStatus(
            validFrom = this[AuthorizationGrantTable.validFrom],
            validTo = this[AuthorizationGrantTable.validTo],
            now = now,
        )
    }

fun ResultRow.toGrantedAuthorizationScope(constraints: List<AuthorizationScopeConstraint>): GrantedAuthorizationScope =
    GrantedAuthorizationScope(
        capability = this[AuthorizationGrantScopeTable.capability],
        resourceType = this[AuthorizationGrantScopeTable.resourceType],
        constraints = constraints,
    )

fun ResultRow.toGrantScopeConstraint(): AuthorizationScopeConstraint {
    val attribute = this[AuthorizationGrantScopeConstraintTable.attribute]
    val values = this[AuthorizationGrantScopeConstraintTable.value]
    return AuthorizationScopeConstraint(
        constraintKind = this[AuthorizationGrantScopeConstraintTable.kind],
        attribute = attribute,
        value = when (attribute) {
            AuthorizationScopeConstraintAttribute.MeteringPointId -> ResourceConstraint.MeteringPoints(
                values.map(MeteringPointId::create).toSet(),
            )

            AuthorizationScopeConstraintAttribute.ContractType -> ResourceConstraint.ContractTypes(
                values.map(MeteringPointContractType::valueOf).toSet(),
            )

            AuthorizationScopeConstraintAttribute.ValidFrom -> ResourceConstraint.ValidFrom(Instant.parse(values.single()))
        },
    )
}

object AuthorizationGrantTable : UUIDTable("auth_v1.authorization_grant") {
    val type = customEnumeration(
        name = "type",
        sql = "auth_v1.authorization_grant_type",
        fromDb = { value -> AuthorizationGrantType.valueOf(value as String) },
        toDb = { PGEnum("auth_v1.authorization_grant_type", it) },
    )
    val sourceType = customEnumeration(
        name = "source_type",
        sql = "auth_v1.authorization_grant_source_type",
        fromDb = { value -> AuthorizationGrantSourceType.valueOf(value as String) },
        toDb = { PGEnum("auth_v1.authorization_grant_source_type", it) },
    )
    val sourceId = javaUUID("source_id")
    val principal = javaUUID("principal").references(AuthorizationAgentTable.id)
    val grantedBy = javaUUID("granted_by").references(AuthorizationAgentTable.id)
    val grantedTo = javaUUID("granted_to").references(AuthorizationAgentTable.id)
    val approvedBy = javaUUID("approved_by").references(AuthorizationAgentTable.id)
    val status = customEnumeration(
        name = "status",
        sql = "auth_v1.authorization_grant_status",
        fromDb = { value -> DatabaseGrantStatus.valueOf(value as String) },
        toDb = { PGEnum("auth_v1.authorization_grant_status", it) },
    )
    val validFrom = timestamp("valid_from").clientDefault { now() }
    val validTo = timestamp("valid_to").nullable()
    val createdAt = timestamp("created_at").clientDefault { now() }
    val updatedAt = timestamp("updated_at").clientDefault { now() }
}

enum class DatabaseGrantStatus {
    Active,
    Revoked,
    Exhausted,
}

object AuthorizationGrantScopeTable : UUIDTable("auth_v1.authorization_grant_scope") {
    val grantId = javaUUID("grant_id").references(AuthorizationGrantTable.id)
    val capability = customEnumeration(
        name = "capability",
        sql = "auth_v1.authorization_capability",
        fromDb = { value -> PermissionCapability.valueOf(value as String) },
        toDb = { PGEnum("auth_v1.authorization_capability", it) },
    )
    val resourceType = customEnumeration(
        name = "resource_type",
        sql = "auth_v1.authorization_resource_type",
        fromDb = { value -> ResourceType.valueOf(value as String) },
        toDb = { PGEnum("auth_v1.authorization_resource_type", it) },
    )
}

object AuthorizationGrantScopeConstraintTable : UUIDTable("auth_v1.authorization_grant_scope_constraint") {
    val scopeId = javaUUID("scope_id").references(AuthorizationGrantScopeTable.id)
    val kind = customEnumeration(
        name = "kind",
        sql = "auth_v1.authorization_constraint_kind",
        fromDb = { value -> AuthorizationScopeConstraintKind.valueOf(value as String) },
        toDb = { PGEnum("auth_v1.authorization_constraint_kind", it) },
    )
    val attribute = customEnumeration(
        name = "attribute",
        sql = "auth_v1.authorization_constraint_attribute",
        fromDb = { value ->
            AuthorizationScopeConstraintAttribute.entries.single { it.apiName == value as String }
        },
        toDb = { attribute ->
            PGobject().apply {
                type = "auth_v1.authorization_constraint_attribute"
                value = attribute.apiName
            }
        },
    )
    val value = array<String>("value")
}
