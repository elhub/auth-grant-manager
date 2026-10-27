package no.elhub.auth.v1.repositories

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import no.elhub.auth.v0.config.withTransaction
import no.elhub.auth.v0.features.common.PGEnum
import no.elhub.auth.v0.features.common.Page
import no.elhub.auth.v0.features.common.Pagination
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v0.features.common.party.AuthorizationPartyRecord
import no.elhub.auth.v0.features.common.party.AuthorizationPartyTable
import no.elhub.auth.v0.features.common.party.toAuthorizationParty
import no.elhub.auth.v0.features.grants.common.toAuthorizationParty
import no.elhub.auth.v1.common.now
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationGrantSourceType
import no.elhub.auth.v1.domain.AuthorizationRequest
import no.elhub.auth.v1.domain.AuthorizationRequestStatus
import no.elhub.auth.v1.domain.AuthorizationRequestType
import no.elhub.auth.v1.domain.AuthorizationScopeConstraint
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintAttribute
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintKind
import no.elhub.auth.v1.domain.MeteringPointContractType
import no.elhub.auth.v1.domain.MeteringPointId
import no.elhub.auth.v1.domain.RequestedAuthorizationScope
import no.elhub.auth.v1.domain.ResourceConstraint
import no.elhub.auth.v1.domain.ResourceType
import no.elhub.auth.v1.domain.TextLanguage
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
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.updateReturning
import org.jetbrains.exposed.v1.json.jsonb
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
    suspend fun reject(requestId: UUID, textLanguage: TextLanguage): AuthorizationRequest
    suspend fun accept(requestId: UUID, grants: List<AuthorizationGrant>, textLanguage: TextLanguage): AuthorizationRequest
}

class ExposedRequestRepository(
    val partyRepository: PartyRepository,
    val grantRepository: GrantRepository,
) : RequestRepository {
    override suspend fun find(requestId: UUID): AuthorizationRequest = withTransaction {
        val row = AuthorizationRequestTable.selectAll()
            .where { AuthorizationRequestTable.id eq requestId }
            .singleOrNull()
            ?: throw RepositoryReadError.NotFoundError("Request not found: $requestId")

        row.toAuthorizationRequest(
            requestedBy = partyRepository.find(row[AuthorizationRequestTable.requestedBy]).toAuthorizationParty(),
            requestedPrincipal = partyRepository.find(row[AuthorizationRequestTable.requestedPrincipal]).toAuthorizationParty(),
            requestedOf = partyRepository.find(row[AuthorizationRequestTable.requestedOf]).toAuthorizationParty(),
            requestedScopes = getScopesByRequestId(listOf(requestId))[requestId].orEmpty(),
            approvedBy = row[AuthorizationRequestTable.approvedBy]?.let { partyRepository.find(it).toAuthorizationParty() },
            now = now(),
            grants = grantRepository.findBySourceIds(AuthorizationGrantSourceType.Request, listOf(requestId))[requestId],
        )
    }

    override suspend fun findAndSortByCreatedAt(
        party: AuthorizationParty,
        pagination: Pagination,
        statuses: List<AuthorizationRequestStatus>
    ): Page<AuthorizationRequest> = withTransaction {
        val partyId = partyRepository.findOrInsert(type = party.type, partyId = party.id).id
        val now = now()
        val whereClause = generateFilterByCondition(partyId, statuses, now)

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
                it[AuthorizationRequestTable.requestedPrincipal],
                it[AuthorizationRequestTable.requestedOf],
                it[AuthorizationRequestTable.approvedBy],
            )
        }.distinct()
        val partyMap: Map<UUID, AuthorizationPartyRecord> = AuthorizationPartyTable
            .selectAll()
            .where { AuthorizationPartyTable.id inList allPartyIds }
            .associate { it[AuthorizationPartyTable.id].value to it.toAuthorizationParty() }
        val scopesByRequestId = getScopesByRequestId(requestIds)
        val grantsByRequestId = grantRepository.findBySourceIds(AuthorizationGrantSourceType.Request, requestIds)

        val unexpectedMessage = "Unexpected error occurred."

        val items = requestRows.map { row ->
            val requestedBy = partyMap[row[AuthorizationRequestTable.requestedBy]]
                ?: throw RepositoryReadError.UnexpectedError(unexpectedMessage)
            val requestedPrincipal = partyMap[row[AuthorizationRequestTable.requestedPrincipal]]
                ?: throw RepositoryReadError.UnexpectedError(unexpectedMessage)
            val requestedOf = partyMap[row[AuthorizationRequestTable.requestedOf]]
                ?: throw RepositoryReadError.UnexpectedError(unexpectedMessage)
            val approvedBy = row[AuthorizationRequestTable.approvedBy]?.let {
                partyMap[it] ?: throw RepositoryReadError.UnexpectedError(unexpectedMessage)
            }
            val requestId = row[AuthorizationRequestTable.id].value
            row.toAuthorizationRequest(
                requestedBy = requestedBy.toAuthorizationParty(),
                requestedPrincipal = requestedPrincipal.toAuthorizationParty(),
                requestedOf = requestedOf.toAuthorizationParty(),
                requestedScopes = scopesByRequestId[requestId].orEmpty(),
                approvedBy = approvedBy?.toAuthorizationParty(),
                now = now,
                grants = grantsByRequestId[requestId],
            )
        }

        Page(items = items, totalItems = totalItems, pagination = pagination)
    }

    override suspend fun insert(request: AuthorizationRequest): AuthorizationRequest = withTransaction {
        val requestedBy = partyRepository.findOrInsert(request.requestedBy.type, request.requestedBy.id)
        val requestedPrincipal = partyRepository.findOrInsert(request.requestedPrincipal.type, request.requestedPrincipal.id)
        val requestedOf = partyRepository.findOrInsert(request.requestedOf.type, request.requestedOf.id)
        val approvedBy = request.approvedBy?.let { partyRepository.findOrInsert(it.type, it.id) }

        val inserted = AuthorizationRequestTable.insert {
            it[id] = request.id
            it[requestType] = request.requestType
            it[status] = when (request.status) {
                AuthorizationRequestStatus.Rejected -> DatabaseRequestStatus.Rejected
                else -> DatabaseRequestStatus.Pending
            }
            it[AuthorizationRequestTable.requestedBy] = requestedBy.id
            it[AuthorizationRequestTable.requestedPrincipal] = requestedPrincipal.id
            it[AuthorizationRequestTable.requestedOf] = requestedOf.id
            it[AuthorizationRequestTable.approvedBy] = approvedBy?.id
            it[externalReference] = request.externalReference
            it[meta] = request.meta
            it[redirectUri] = request.redirectUri
            it[AuthorizationRequestTable.requestedGrantValidFrom] = request.requestedGrantValidFrom
            it[AuthorizationRequestTable.requestedGrantValidTo] = request.requestedGrantValidTo
            it[AuthorizationRequestTable.validTo] = request.validTo
            it[validFrom] = request.validFrom
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
                        is ResourceConstraint.MeteringPoints -> resourceConstraint.ids.map { id -> id.value }
                        is ResourceConstraint.ContractTypes -> resourceConstraint.types.map { type -> type.name }
                        is ResourceConstraint.ValidFrom -> listOf(resourceConstraint.value.toString())
                    }
                }
            }
        }

        inserted.resultedValues!!.single().toAuthorizationRequest(
            requestedBy = requestedBy.toAuthorizationParty(),
            requestedPrincipal = requestedPrincipal.toAuthorizationParty(),
            requestedOf = requestedOf.toAuthorizationParty(),
            requestedScopes = request.requestedScopes,
            approvedBy = approvedBy?.toAuthorizationParty(),
            now = now(),
        )
    }

    override suspend fun reject(requestId: UUID, textLanguage: TextLanguage): AuthorizationRequest = withTransaction {
        // Lock the row using SELECT ... FOR NO KEY UPDATE for atomicity
        val row = AuthorizationRequestTable.selectAll()
            .where { AuthorizationRequestTable.id eq requestId }
            .forUpdate(ForUpdateOption.PostgreSQL.ForNoKeyUpdate)
            .singleOrNull()
            ?: throw RepositoryWriteError.NotFoundError("Request not found: $requestId")

        val now = now()
        val status = row.toAuthorizationRequestStatus(now)

        when (status) {
            AuthorizationRequestStatus.Expired -> throw RepositoryWriteError.ExpiredError("Request $requestId has expired")

            AuthorizationRequestStatus.Accepted, AuthorizationRequestStatus.Rejected ->
                throw RepositoryWriteError.ConflictError("Invalid transition for request $requestId from $status to Rejected")

            AuthorizationRequestStatus.Pending -> Unit
        }

        AuthorizationRequestTable.updateReturning(
            where = { AuthorizationRequestTable.id eq requestId }
        ) {
            it[AuthorizationRequestTable.status] = DatabaseRequestStatus.Rejected
            it[AuthorizationRequestTable.textLanguage] = textLanguage
            it[updatedAt] = now
        }.single().toAuthorizationRequest(
            requestedBy = partyRepository.find(row[AuthorizationRequestTable.requestedBy]).toAuthorizationParty(),
            requestedPrincipal = partyRepository.find(row[AuthorizationRequestTable.requestedPrincipal]).toAuthorizationParty(),
            requestedOf = partyRepository.find(row[AuthorizationRequestTable.requestedOf]).toAuthorizationParty(),
            requestedScopes = getScopesByRequestId(listOf(requestId))[requestId].orEmpty(),
            now = now,
        )
    }

    override suspend fun accept(
        requestId: UUID,
        grants: List<AuthorizationGrant>,
        textLanguage: TextLanguage,
    ): AuthorizationRequest = withTransaction {
        val row = AuthorizationRequestTable.selectAll()
            .where { AuthorizationRequestTable.id eq requestId }
            .forUpdate(ForUpdateOption.PostgreSQL.ForNoKeyUpdate)
            .singleOrNull()
            ?: throw RepositoryWriteError.NotFoundError("Request not found: $requestId")

        val now = now()
        when (val status = row.toAuthorizationRequestStatus(now)) {
            AuthorizationRequestStatus.Expired -> throw RepositoryWriteError.ExpiredError("Request $requestId has expired")

            AuthorizationRequestStatus.Accepted, AuthorizationRequestStatus.Rejected ->
                throw RepositoryWriteError.ConflictError("Invalid transition for request $requestId from $status to Accepted")

            AuthorizationRequestStatus.Pending -> Unit
        }

        val insertedGrants = grants.map { grantRepository.insert(it) }

        val approvedBy = insertedGrants.first().approvedBy
        val approvedById = partyRepository.findOrInsert(approvedBy.type, approvedBy.id).id

        AuthorizationRequestTable.updateReturning(
            where = { AuthorizationRequestTable.id eq requestId }
        ) {
            it[AuthorizationRequestTable.approvedBy] = approvedById
            it[approvedAt] = now
            it[AuthorizationRequestTable.textLanguage] = textLanguage
            it[updatedAt] = now
        }.single().toAuthorizationRequest(
            requestedBy = partyRepository.find(row[AuthorizationRequestTable.requestedBy]).toAuthorizationParty(),
            requestedPrincipal = partyRepository.find(row[AuthorizationRequestTable.requestedPrincipal]).toAuthorizationParty(),
            requestedOf = partyRepository.find(row[AuthorizationRequestTable.requestedOf]).toAuthorizationParty(),
            requestedScopes = getScopesByRequestId(listOf(requestId))[requestId].orEmpty(),
            approvedBy = approvedBy,
            now = now,
            grants = insertedGrants,
        )
    }

    private fun generateFilterByCondition(
        partyId: UUID,
        statuses: List<AuthorizationRequestStatus>,
        now: Instant,
    ): Op<Boolean> {
        val partyCondition = (AuthorizationRequestTable.requestedOf eq partyId) or
            (AuthorizationRequestTable.requestedBy eq partyId)
        if (statuses.isEmpty()) {
            return partyCondition
        }

        val statusCondition = statuses.map { status ->
            when (status) {
                AuthorizationRequestStatus.Pending ->
                    (AuthorizationRequestTable.status eq DatabaseRequestStatus.Pending) and
                        AuthorizationRequestTable.approvedBy.isNull() and
                        (AuthorizationRequestTable.validTo greater now)

                AuthorizationRequestStatus.Expired ->
                    (AuthorizationRequestTable.status eq DatabaseRequestStatus.Pending) and
                        AuthorizationRequestTable.approvedBy.isNull() and
                        (AuthorizationRequestTable.validTo lessEq now)

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
    val requestedPrincipal = javaUUID("requested_principal").references(AuthorizationPartyTable.id)
    val requestedOf = javaUUID("requested_of").references(AuthorizationPartyTable.id)
    val approvedBy = javaUUID("approved_by").references(AuthorizationPartyTable.id).nullable()
    val approvedAt = timestamp("approved_at").nullable()
    val createdAt = timestamp("created_at").clientDefault { now() }
    val externalReference = varchar("external_reference", 255).nullable()
    val meta = jsonb("meta", Json, JsonObject.serializer())
    val redirectUri = text("redirect_uri").nullable()
    val requestedGrantValidFrom = timestamp("requested_grant_valid_from").nullable()
    val requestedGrantValidTo = timestamp("requested_grant_valid_to").nullable()
    val textLanguage = varchar("text_language", 2)
        .transform(wrap = TextLanguage::fromCode, unwrap = TextLanguage::code)
        .nullable()
    val validFrom = timestamp("valid_from").clientDefault { now() }
    val validTo = timestamp("valid_to").clientDefault { now() }
    val updatedAt = timestamp("updated_at").clientDefault { now() }
}

private fun ResultRow.toAuthorizationRequestStatus(now: Instant): AuthorizationRequestStatus =
    when (this[AuthorizationRequestTable.status]) {
        DatabaseRequestStatus.Rejected -> AuthorizationRequestStatus.Rejected

        DatabaseRequestStatus.Pending -> when {
            this[AuthorizationRequestTable.approvedBy] != null -> AuthorizationRequestStatus.Accepted
            this[AuthorizationRequestTable.validTo] <= now -> AuthorizationRequestStatus.Expired
            else -> AuthorizationRequestStatus.Pending
        }
    }

fun ResultRow.toAuthorizationRequest(
    requestedBy: AuthorizationParty,
    requestedPrincipal: AuthorizationParty,
    requestedOf: AuthorizationParty,
    requestedScopes: List<RequestedAuthorizationScope>,
    approvedBy: AuthorizationParty? = null,
    now: Instant,
    grants: List<AuthorizationGrant>? = null
): AuthorizationRequest {
    val validTo = this[AuthorizationRequestTable.validTo]
    return AuthorizationRequest(
        id = this[AuthorizationRequestTable.id].value,
        requestType = this[AuthorizationRequestTable.requestType],
        status = toAuthorizationRequestStatus(now),
        requestedScopes = requestedScopes,
        externalReference = this[AuthorizationRequestTable.externalReference],
        meta = this[AuthorizationRequestTable.meta],
        redirectUri = this[AuthorizationRequestTable.redirectUri],
        requestedGrantValidFrom = this[AuthorizationRequestTable.requestedGrantValidFrom],
        requestedGrantValidTo = this[AuthorizationRequestTable.requestedGrantValidTo],
        validFrom = this[AuthorizationRequestTable.validFrom],
        validTo = validTo,
        createdAt = this[AuthorizationRequestTable.createdAt],
        updatedAt = this[AuthorizationRequestTable.updatedAt],
        requestedBy = AuthorizationParty(id = requestedBy.id, type = requestedBy.type),
        requestedPrincipal = AuthorizationParty(id = requestedPrincipal.id, type = requestedPrincipal.type),
        requestedOf = AuthorizationParty(id = requestedOf.id, type = requestedOf.type),
        approvedBy = approvedBy?.let { AuthorizationParty(id = approvedBy.id, type = approvedBy.type) },
        authorizationGrants = grants,
        textLanguage = this[AuthorizationRequestTable.textLanguage],
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

            AuthorizationScopeConstraintAttribute.ContractType -> ResourceConstraint.ContractTypes(
                this[AuthorizationRequestScopeConstraintTable.value].map(MeteringPointContractType::valueOf).toSet(),
            )

            AuthorizationScopeConstraintAttribute.ValidFrom -> ResourceConstraint.ValidFrom(
                Instant.parse(this[AuthorizationRequestScopeConstraintTable.value].single()),
            )
        },
    )
}
