package no.elhub.auth.v1.repositories

import no.elhub.auth.v0.config.withTransaction
import no.elhub.auth.v0.features.common.PGEnum
import no.elhub.auth.v0.features.common.Page
import no.elhub.auth.v0.features.common.Pagination
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v0.features.common.party.AuthorizationPartyRecord
import no.elhub.auth.v0.features.common.party.AuthorizationPartyTable
import no.elhub.auth.v0.features.common.party.toAuthorizationParty
import no.elhub.auth.v1.RepositoryReadError
import no.elhub.auth.v1.common.now
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationRequest
import no.elhub.auth.v1.domain.AuthorizationRequestStatus
import no.elhub.auth.v1.domain.AuthorizationRequestType
import no.elhub.auth.v1.domain.AuthorizationScopeConstraint
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintAttribute
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintKind
import no.elhub.auth.v1.domain.MeteringPointId
import no.elhub.auth.v1.domain.RequestedAuthorizationScope
import no.elhub.auth.v1.domain.ResourceConstraint
import no.elhub.auth.v1.domain.ResourceType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.postgresql.util.PGobject
import java.util.UUID
import kotlin.time.Instant

interface RequestRepository {
    suspend fun find(requestId: UUID): AuthorizationRequest
    suspend fun findAndSortByCreatedAt(
        party: AuthorizationParty,
        pagination: Pagination,
        statuses: List<AuthorizationRequestStatus>
    ): Page<AuthorizationRequest>

    suspend fun insert(request: AuthorizationRequest): AuthorizationRequest
    suspend fun reject(requestId: UUID): AuthorizationRequest
    suspend fun accept(requestId: UUID, grant: AuthorizationGrant): AuthorizationRequest
}

class ExposedRequestRepository(
    val partyRepository: PartyRepository,
) : RequestRepository {
    override suspend fun find(requestId: UUID): AuthorizationRequest {
        TODO("Not yet implemented")
    }

    override suspend fun findAndSortByCreatedAt(
        party: AuthorizationParty,
        pagination: Pagination,
        statuses: List<AuthorizationRequestStatus>
    ): Page<AuthorizationRequest> = withTransaction {
        val partyId = partyRepository.findOrInsert(type = party.type, partyId = party.id).id
        val currentTime = now()
        val whereClause = generateFilterByCondition(partyId, statuses, currentTime)

        val totalItems = AuthorizationRequestTable
            .selectAll()
            .where(whereClause)
            .count()

        val requestRows = AuthorizationRequestTable
            .selectAll()
            .where(whereClause)
            .orderBy(AuthorizationRequestTable.createdAt to SortOrder.DESC, AuthorizationRequestTable.id to SortOrder.DESC)
            .limit(pagination.size)
            .offset(pagination.offset)
            .toList()

        if (requestRows.isEmpty()) {
            return@withTransaction Page(emptyList(), totalItems, pagination)
        }

        val requestIds = requestRows.map { it[AuthorizationRequestTable.id].value }

        val allPartyIds = requestRows.flatMap {
            listOfNotNull(
                it[AuthorizationRequestTable.requestedBy],
                it[AuthorizationRequestTable.requestedFrom],
                it[AuthorizationRequestTable.requestedTo],
                it[AuthorizationRequestTable.approvedBy],
            )
        }.distinct()
        val partyMap: Map<UUID, AuthorizationPartyRecord> = AuthorizationPartyTable
            .selectAll()
            .where { AuthorizationPartyTable.id inList allPartyIds }
            .associate { it[AuthorizationPartyTable.id].value to it.toAuthorizationParty() }
        val scopesByRequestId = getScopesByRequestId(requestIds)

        val unexpectedMessage = "Unexpected error occurred."

        val items = requestRows.map { row ->
            val requestedBy = partyMap[row[AuthorizationRequestTable.requestedBy]]
                ?: throw RepositoryReadError.UnexpectedError(unexpectedMessage)
            val requestedFrom = partyMap[row[AuthorizationRequestTable.requestedFrom]]
                ?: throw RepositoryReadError.UnexpectedError(unexpectedMessage)
            val requestedTo = partyMap[row[AuthorizationRequestTable.requestedTo]]
                ?: throw RepositoryReadError.UnexpectedError(unexpectedMessage)
            val approvedBy = row[AuthorizationRequestTable.approvedBy]?.let {
                partyMap[it] ?: throw RepositoryReadError.UnexpectedError(unexpectedMessage)
            }
            val requestId = row[AuthorizationRequestTable.id].value
            row.toAuthorizationRequest(
                requestedBy = requestedBy,
                requestedFrom = requestedFrom,
                requestedTo = requestedTo,
                requestedScopes = scopesByRequestId[requestId].orEmpty(),
                approvedBy = approvedBy,
                currentTime = currentTime,
            )
        }

        Page(items = items, totalItems = totalItems, pagination = pagination)
    }

    override suspend fun insert(request: AuthorizationRequest): AuthorizationRequest = withTransaction {
        val requestedBy = partyRepository.findOrInsert(request.requestedBy.type, request.requestedBy.id)
        val requestedFrom = partyRepository.findOrInsert(request.requestedFrom.type, request.requestedFrom.id)
        val requestedTo = partyRepository.findOrInsert(request.requestedTo.type, request.requestedTo.id)
        val approvedBy = request.approvedBy?.let { partyRepository.findOrInsert(it.type, it.id) }

        val inserted = AuthorizationRequestTable.insert {
            it[id] = request.id
            it[requestType] = request.requestType
            it[status] = when (request.status) {
                AuthorizationRequestStatus.Rejected -> DatabaseRequestStatus.Rejected
                else -> DatabaseRequestStatus.Pending
            }
            it[AuthorizationRequestTable.requestedBy] = requestedBy.id
            it[AuthorizationRequestTable.requestedFrom] = requestedFrom.id
            it[AuthorizationRequestTable.requestedTo] = requestedTo.id
            it[AuthorizationRequestTable.approvedBy] = approvedBy?.id
            it[externalReference] = request.externalReference
            it[AuthorizationRequestTable.validTo] = request.validTo
            it[validFrom] = request.createdAt
            it[createdAt] = request.createdAt
            it[updatedAt] = request.updatedAt
        }

        request.requestedScopes.forEach { scope ->
            val scopeId = AuthorizationRequestScopeTable.insert {
                it[requestId] = request.id
                it[resourceType] = scope.resourceType
            }[AuthorizationRequestScopeTable.id].value

            scope.constraints.forEach { constraint ->
                AuthorizationRequestScopeConstraintTable.insert {
                    it[AuthorizationRequestScopeConstraintTable.scopeId] = scopeId
                    it[kind] = constraint.constraintKind
                    it[attribute] = constraint.attribute
                    it[value] = when (val resourceConstraint = constraint.value) {
                        is ResourceConstraint.MeteringPoints -> resourceConstraint.ids.map { it.value }
                    }
                }
            }
        }

        inserted.resultedValues!!.single().toAuthorizationRequest(
            requestedBy = requestedBy,
            requestedFrom = requestedFrom,
            requestedTo = requestedTo,
            requestedScopes = request.requestedScopes,
            approvedBy = approvedBy,
        )
    }

    override suspend fun reject(requestId: UUID): AuthorizationRequest {
        TODO("Not yet implemented")
    }

    override suspend fun accept(requestId: UUID, grant: AuthorizationGrant): AuthorizationRequest {
        TODO("Not yet implemented")
    }

    private fun generateFilterByCondition(
        partyId: UUID,
        statuses: List<AuthorizationRequestStatus>,
        currentTime: Instant,
    ): Op<Boolean> {
        val partyCondition = (AuthorizationRequestTable.requestedTo eq partyId) or
            (AuthorizationRequestTable.requestedBy eq partyId)
        if (statuses.isEmpty()) {
            return partyCondition
        }

        val statusCondition = statuses.map { status ->
            when (status) {
                AuthorizationRequestStatus.Pending ->
                    (AuthorizationRequestTable.status eq DatabaseRequestStatus.Pending) and
                        AuthorizationRequestTable.approvedBy.isNull() and
                        (AuthorizationRequestTable.validTo greater currentTime)

                AuthorizationRequestStatus.Expired ->
                    (AuthorizationRequestTable.status eq DatabaseRequestStatus.Pending) and
                        AuthorizationRequestTable.approvedBy.isNull() and
                        (AuthorizationRequestTable.validTo lessEq currentTime)

                AuthorizationRequestStatus.Accepted ->
                    (AuthorizationRequestTable.status eq DatabaseRequestStatus.Pending) and
                        AuthorizationRequestTable.approvedBy.isNotNull()

                AuthorizationRequestStatus.Rejected -> AuthorizationRequestTable.status eq DatabaseRequestStatus.Rejected
            }
        }.reduce { acc, op -> acc or op }
        return partyCondition and statusCondition
    }

    private fun getScopesByRequestId(
        requestIds: List<UUID>,
    ): Map<UUID, List<RequestedAuthorizationScope>> {
        if (requestIds.isEmpty()) {
            return emptyMap()
        }

        return (AuthorizationRequestScopeTable leftJoin AuthorizationRequestScopeConstraintTable)
            .selectAll()
            .where { AuthorizationRequestScopeTable.requestId inList requestIds }
            .groupBy { row -> row[AuthorizationRequestScopeTable.requestId] }
            .mapValues { (_, rows) ->
                rows.groupBy { row ->
                    row[AuthorizationRequestScopeTable.id].value
                }
                    .values.map { scopeRows ->
                        val first = scopeRows.first()

                        RequestedAuthorizationScope(
                            resourceType = first[AuthorizationRequestScopeTable.resourceType],
                            constraints = scopeRows.filter {
                                it.getOrNull(AuthorizationRequestScopeConstraintTable.id) != null
                            }.map(ResultRow::toConstraint),
                        )
                    }
            }
    }
}

object AuthorizationRequestTable : UUIDTable("auth_v1.authorization_request") {
    val requestType =
        customEnumeration(
            name = "request_type",
            sql = "auth_v1.authorization_request_type",
            fromDb = { value -> AuthorizationRequestType.valueOf(value as String) },
            toDb = { PGEnum("auth_v1.authorization_request_type", it) },
        )
    val status =
        customEnumeration(
            name = "status",
            sql = "auth_v1.authorization_request_status",
            fromDb = { value -> DatabaseRequestStatus.valueOf(value as String) },
            toDb = { PGEnum("auth_v1.authorization_request_status", it) },
        )
    val requestedBy = javaUUID("requested_by").references(AuthorizationPartyTable.id)
    val requestedFrom = javaUUID("requested_from").references(AuthorizationPartyTable.id)
    val requestedTo = javaUUID("requested_to").references(AuthorizationPartyTable.id)
    val approvedBy = javaUUID("approved_by").references(AuthorizationPartyTable.id).nullable()
    val approvedAt = timestamp("approved_at").nullable()
    val createdAt = timestamp("created_at").clientDefault { now() }
    val externalReference = varchar("external_reference", 255).nullable()
    val redirectUri = text("redirect_uri").nullable()
    val validFrom = timestamp("valid_from").clientDefault { now() }
    val validTo = timestamp("valid_to").clientDefault { now() }
    val updatedAt = timestamp("updated_at").clientDefault { now() }
}

fun ResultRow.toAuthorizationRequest(
    requestedBy: AuthorizationPartyRecord,
    requestedFrom: AuthorizationPartyRecord,
    requestedTo: AuthorizationPartyRecord,
    requestedScopes: List<RequestedAuthorizationScope>,
    approvedBy: AuthorizationPartyRecord? = null,
    currentTime: Instant = now(),
): AuthorizationRequest {
    val validTo = this[AuthorizationRequestTable.validTo]
    val dbStatus = this[AuthorizationRequestTable.status]
    val status: AuthorizationRequestStatus = when (dbStatus) {
        DatabaseRequestStatus.Rejected -> AuthorizationRequestStatus.Rejected
        DatabaseRequestStatus.Pending if this[AuthorizationRequestTable.approvedBy] != null -> AuthorizationRequestStatus.Accepted
        DatabaseRequestStatus.Pending if validTo <= currentTime -> AuthorizationRequestStatus.Expired
        else -> AuthorizationRequestStatus.Pending
    }
    return AuthorizationRequest(
        id = this[AuthorizationRequestTable.id].value,
        requestType = this[AuthorizationRequestTable.requestType],
        status = status,
        requestedScopes = requestedScopes,
        externalReference = this[AuthorizationRequestTable.externalReference],
        validTo = validTo,
        createdAt = this[AuthorizationRequestTable.createdAt],
        updatedAt = this[AuthorizationRequestTable.updatedAt],
        requestedBy = AuthorizationParty(id = requestedBy.resourceId, type = requestedBy.type),
        requestedFrom = AuthorizationParty(id = requestedFrom.resourceId, type = requestedFrom.type),
        requestedTo = AuthorizationParty(id = requestedTo.resourceId, type = requestedTo.type),
        approvedBy = approvedBy?.let { AuthorizationParty(id = approvedBy.resourceId, type = approvedBy.type) },
        authorizationGrant = null,
    )
}

enum class DatabaseRequestStatus {
    Pending,
    Rejected,
}

object AuthorizationRequestScopeTable : UUIDTable("auth_v1.authorization_request_scope") {
    val requestId = javaUUID("request_id")
        .references(AuthorizationRequestTable.id)
    val resourceType = customEnumeration(
        name = "resource_type",
        sql = "auth_v1.authorization_resource_type",
        fromDb = { value -> ResourceType.valueOf(value as String) },
        toDb = { PGEnum("auth_v1.authorization_resource_type", it) },
    )
}

object AuthorizationRequestScopeConstraintTable : UUIDTable("auth_v1.authorization_request_scope_constraint") {
    val scopeId = javaUUID("scope_id").references(AuthorizationRequestScopeTable.id)
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

fun ResultRow.toConstraint(): AuthorizationScopeConstraint {
    val attribute = this[AuthorizationRequestScopeConstraintTable.attribute]
    return AuthorizationScopeConstraint(
        constraintKind = this[AuthorizationRequestScopeConstraintTable.kind],
        attribute = attribute,
        value = when (attribute) {
            AuthorizationScopeConstraintAttribute.MeteringPointId -> ResourceConstraint.MeteringPoints(
                this[AuthorizationRequestScopeConstraintTable.value].map(MeteringPointId::create).toSet(),
            )
        },
    )
}
