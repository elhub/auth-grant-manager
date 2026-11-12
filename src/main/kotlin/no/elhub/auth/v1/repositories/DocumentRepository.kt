package no.elhub.auth.v1.repositories

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import no.elhub.auth.common.config.withTransaction
import no.elhub.auth.v0.features.common.PGEnum
import no.elhub.auth.v0.features.common.Page
import no.elhub.auth.v0.features.common.Pagination
import no.elhub.auth.v1.common.auth.AuthorizationAgent
import no.elhub.auth.v1.common.now
import no.elhub.auth.v1.domain.AuthorizationDocument
import no.elhub.auth.v1.domain.AuthorizationDocumentStatus
import no.elhub.auth.v1.domain.AuthorizationDocumentType
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationScopeConstraint
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintAttribute
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintKind
import no.elhub.auth.v1.domain.MeteringPointContractType
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
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.updateReturning
import org.jetbrains.exposed.v1.json.jsonb
import org.postgresql.util.PGobject
import java.util.UUID
import kotlin.time.Instant
import no.elhub.auth.v0.features.common.party.AuthorizationPartyTable as AuthorizationAgentTable
import no.elhub.auth.v0.features.common.party.toAuthorizationParty as toAuthorizationAgent
import no.elhub.auth.v0.features.grants.common.toAuthorizationParty as toAuthorizationAgent

interface DocumentRepository {
    suspend fun find(id: UUID): AuthorizationDocument
    suspend fun findAndSortByCreatedAt(
        requestedBy: AuthorizationAgent,
        pagination: Pagination,
        statuses: List<AuthorizationDocumentStatus>,
    ): Page<AuthorizationDocument>

    suspend fun insert(document: AuthorizationDocument): AuthorizationDocument
    suspend fun confirm(
        documentId: UUID,
        signedFile: ByteArray,
        signatory: AuthorizationAgent,
        grants: List<AuthorizationGrant>,
    ): AuthorizationDocument
}

class ExposedDocumentRepository(
    private val agentRepository: AgentRepository,
    private val grantRepository: GrantRepository,
) : DocumentRepository {
    override suspend fun find(id: UUID): AuthorizationDocument = withTransaction {
        val row = AuthorizationDocumentTable.selectAll()
            .where { AuthorizationDocumentTable.id eq id }
            .singleOrNull()
            ?: throw RepositoryReadError.NotFoundError("Document not found: $id")

        row.toAuthorizationDocument(
            requestedBy = agentRepository.find(row[AuthorizationDocumentTable.requestedBy]).toAuthorizationAgent(),
            requestedPrincipal = agentRepository.find(row[AuthorizationDocumentTable.requestedPrincipal]).toAuthorizationAgent(),
            requestedOf = agentRepository.find(row[AuthorizationDocumentTable.requestedOf]).toAuthorizationAgent(),
            requestedScopes = getScopesByDocumentId(listOf(id))[id].orEmpty(),
            approvedBy = row[AuthorizationDocumentTable.approvedBy]?.let { agentRepository.find(it).toAuthorizationAgent() },
            now = now(),
        )
    }

    override suspend fun findAndSortByCreatedAt(
        requestedBy: AuthorizationAgent,
        pagination: Pagination,
        statuses: List<AuthorizationDocumentStatus>
    ): Page<AuthorizationDocument> = withTransaction {
        val agentId = agentRepository.findOrInsert(requestedBy.type, requestedBy.id).id
        val now = now()
        val whereClause = generateFilterByCondition(agentId, statuses, now)
        val totalItems = AuthorizationDocumentTable.selectAll().where(whereClause).count()
        val documentRows = AuthorizationDocumentTable.selectAll()
            .where(whereClause)
            .orderBy(AuthorizationDocumentTable.createdAt to SortOrder.DESC, AuthorizationDocumentTable.id to SortOrder.DESC)
            .limit(pagination.size)
            .offset(pagination.offset)
            .toList()

        if (documentRows.isEmpty()) {
            return@withTransaction Page(emptyList(), totalItems, pagination)
        }

        val agentIds = documentRows.flatMap {
            listOfNotNull(
                it[AuthorizationDocumentTable.requestedBy],
                it[AuthorizationDocumentTable.requestedPrincipal],
                it[AuthorizationDocumentTable.requestedOf],
                it[AuthorizationDocumentTable.approvedBy],
            )
        }.distinct()
        val agents = AuthorizationAgentTable.selectAll()
            .where { AuthorizationAgentTable.id inList agentIds }
            .associate { it[AuthorizationAgentTable.id].value to it.toAuthorizationAgent().toAuthorizationAgent() }
        val scopes = getScopesByDocumentId(documentRows.map { it[AuthorizationDocumentTable.id].value })

        fun agent(id: UUID): AuthorizationAgent = agents[id]
            ?: throw RepositoryReadError.UnexpectedError("Agent not found for document: $id")

        val items = documentRows.map { row ->
            row.toAuthorizationDocument(
                requestedBy = agent(row[AuthorizationDocumentTable.requestedBy]),
                requestedPrincipal = agent(row[AuthorizationDocumentTable.requestedPrincipal]),
                requestedOf = agent(row[AuthorizationDocumentTable.requestedOf]),
                requestedScopes = scopes[row[AuthorizationDocumentTable.id].value].orEmpty(),
                approvedBy = row[AuthorizationDocumentTable.approvedBy]?.let { agent(it) },
                now = now,
            )
        }
        Page(items, totalItems, pagination)
    }

    override suspend fun insert(document: AuthorizationDocument): AuthorizationDocument = withTransaction {
        val documentId = UUID.fromString(document.id)
        val requestedBy = agentRepository.findOrInsert(document.requestedBy.type, document.requestedBy.id)
        val requestedPrincipal = agentRepository.findOrInsert(document.requestedPrincipal.type, document.requestedPrincipal.id)
        val requestedOf = agentRepository.findOrInsert(document.requestedOf.type, document.requestedOf.id)
        val approvedBy = document.approvedBy?.let { agentRepository.findOrInsert(it.type, it.id) }

        val inserted = AuthorizationDocumentTable.insert {
            it[id] = documentId
            it[documentType] = document.documentType
            it[AuthorizationDocumentTable.requestedBy] = requestedBy.id
            it[AuthorizationDocumentTable.requestedPrincipal] = requestedPrincipal.id
            it[AuthorizationDocumentTable.requestedOf] = requestedOf.id
            it[AuthorizationDocumentTable.approvedBy] = approvedBy?.id
            it[externalReference] = document.externalReference
            it[meta] = document.meta
            it[file] = document.pdfBytes
            it[AuthorizationDocumentTable.requestedGrantValidFrom] = document.requestedGrantValidFrom
            it[AuthorizationDocumentTable.requestedGrantValidTo] = document.requestedGrantValidTo
            it[validFrom] = document.validFrom
            it[validTo] = document.validTo
            it[createdAt] = document.createdAt
            it[updatedAt] = document.updatedAt
        }

        document.requestedScopes.forEach { scope ->
            val scopeId = AuthorizationDocumentScopeTable.insert {
                it[AuthorizationDocumentScopeTable.documentId] = documentId
                it[resourceType] = scope.resourceType
            }[AuthorizationDocumentScopeTable.id].value
            scope.constraints.forEach { constraint ->
                AuthorizationDocumentScopeConstraintTable.insert {
                    it[AuthorizationDocumentScopeConstraintTable.scopeId] = scopeId
                    it[kind] = constraint.constraintKind
                    it[attribute] = constraint.attribute
                    it[value] = when (val resourceConstraint = constraint.value) {
                        is ResourceConstraint.MeteringPoints -> resourceConstraint.ids.map { it.value }
                        is ResourceConstraint.ContractTypes -> resourceConstraint.types.map { it.name }
                        is ResourceConstraint.ValidFrom -> listOf(resourceConstraint.value.toString())
                    }
                }
            }
        }

        inserted.resultedValues!!.single().toAuthorizationDocument(
            requestedBy = requestedBy.toAuthorizationAgent(),
            requestedPrincipal = requestedPrincipal.toAuthorizationAgent(),
            requestedOf = requestedOf.toAuthorizationAgent(),
            requestedScopes = document.requestedScopes,
            approvedBy = approvedBy?.toAuthorizationAgent(),
            now = now(),
        )
    }

    override suspend fun confirm(
        documentId: UUID,
        signedFile: ByteArray,
        signatory: AuthorizationAgent,
        grants: List<AuthorizationGrant>,
    ): AuthorizationDocument = withTransaction {
        if (grants.isEmpty()) {
            throw RepositoryWriteError.UnexpectedError("Cannot confirm document $documentId without grants")
        }

        // Serialize confirmations before checking the current state.
        val row = AuthorizationDocumentTable.selectAll()
            .where { AuthorizationDocumentTable.id eq documentId }
            .forUpdate(ForUpdateOption.PostgreSQL.ForNoKeyUpdate)
            .singleOrNull()
            ?: throw RepositoryWriteError.NotFoundError("Document not found: $documentId")

        val now = now()
        when (row.toAuthorizationDocumentStatus(now)) {
            AuthorizationDocumentStatus.Approved -> throw RepositoryWriteError.ConflictError(
                "Invalid transition for document $documentId from Approved to Approved",
            )

            AuthorizationDocumentStatus.Expired -> throw RepositoryWriteError.ExpiredError("Document $documentId has expired")

            AuthorizationDocumentStatus.Pending -> Unit
        }

        val approvedBy = agentRepository.findOrInsert(signatory.type, signatory.id)
        val updated = AuthorizationDocumentTable.updateReturning(
            where = { AuthorizationDocumentTable.id eq documentId },
        ) {
            it[file] = signedFile
            it[AuthorizationDocumentTable.approvedBy] = approvedBy.id
            it[signedAt] = now
            it[updatedAt] = now
        }.single()
        val insertedGrants = grants.map { grantRepository.insert(it) }

        updated.toAuthorizationDocument(
            requestedBy = agentRepository.find(row[AuthorizationDocumentTable.requestedBy]).toAuthorizationAgent(),
            requestedPrincipal = agentRepository.find(row[AuthorizationDocumentTable.requestedPrincipal]).toAuthorizationAgent(),
            requestedOf = agentRepository.find(row[AuthorizationDocumentTable.requestedOf]).toAuthorizationAgent(),
            requestedScopes = getScopesByDocumentId(listOf(documentId))[documentId].orEmpty(),
            approvedBy = approvedBy.toAuthorizationAgent(),
            now = now,
        ).copy(authorizationGrants = insertedGrants)
    }

    private fun generateFilterByCondition(
        agentId: UUID,
        statuses: List<AuthorizationDocumentStatus>,
        now: Instant,
    ): Op<Boolean> {
        val agentCondition = (AuthorizationDocumentTable.requestedBy eq agentId) or
            (AuthorizationDocumentTable.requestedOf eq agentId)
        if (statuses.isEmpty()) {
            return agentCondition
        }
        val statusCondition = statuses.map { status ->
            when (status) {
                AuthorizationDocumentStatus.Pending -> AuthorizationDocumentTable.approvedBy.isNull() and
                    (AuthorizationDocumentTable.validTo greater now)

                AuthorizationDocumentStatus.Expired -> AuthorizationDocumentTable.approvedBy.isNull() and
                    (AuthorizationDocumentTable.validTo lessEq now)

                AuthorizationDocumentStatus.Approved -> AuthorizationDocumentTable.approvedBy.isNotNull()
            }
        }.reduce { acc, op -> acc or op }
        return agentCondition and statusCondition
    }

    private fun getScopesByDocumentId(documentIds: List<UUID>): Map<UUID, List<RequestedAuthorizationScope>> {
        if (documentIds.isEmpty()) {
            return emptyMap()
        }
        return (AuthorizationDocumentScopeTable leftJoin AuthorizationDocumentScopeConstraintTable)
            .selectAll()
            .where { AuthorizationDocumentScopeTable.documentId inList documentIds }
            .groupBy { it[AuthorizationDocumentScopeTable.documentId] }
            .mapValues { (_, rows) ->
                rows.groupBy { it[AuthorizationDocumentScopeTable.id].value }.values.map { scopeRows ->
                    scopeRows.first().toRequestedAuthorizationScope(
                        constraints = scopeRows.filter {
                            it.getOrNull(AuthorizationDocumentScopeConstraintTable.id) != null
                        }.map { it.toAuthorizationScopeConstraint() },
                    )
                }
            }
    }
}

object AuthorizationDocumentTable : UUIDTable("auth_v1.authorization_document") {
    val documentType = customEnumeration(
        name = "document_type",
        sql = "auth_v1.authorization_document_type",
        fromDb = { value -> AuthorizationDocumentType.valueOf(value as String) },
        toDb = { PGEnum("auth_v1.authorization_document_type", it) },
    )
    val requestedBy = javaUUID("requested_by").references(AuthorizationAgentTable.id)
    val requestedPrincipal = javaUUID("requested_principal").references(AuthorizationAgentTable.id)
    val requestedOf = javaUUID("requested_of").references(AuthorizationAgentTable.id)
    val approvedBy = javaUUID("approved_by").references(AuthorizationAgentTable.id).nullable()
    val signedAt = timestamp("signed_at").nullable()
    val externalReference = varchar("external_reference", 255).nullable()
    val meta = jsonb("meta", Json, JsonObject.serializer())
    val file = binary("file")
    val requestedGrantValidFrom = timestamp("requested_grant_valid_from").nullable()
    val requestedGrantValidTo = timestamp("requested_grant_valid_to").nullable()
    val validFrom = timestamp("valid_from").clientDefault { now() }
    val validTo = timestamp("valid_to").clientDefault { now() }
    val createdAt = timestamp("created_at").clientDefault { now() }
    val updatedAt = timestamp("updated_at").clientDefault { now() }
}

private fun ResultRow.toAuthorizationDocumentStatus(now: Instant): AuthorizationDocumentStatus = when {
    this[AuthorizationDocumentTable.approvedBy] != null -> AuthorizationDocumentStatus.Approved
    this[AuthorizationDocumentTable.validTo] <= now -> AuthorizationDocumentStatus.Expired
    else -> AuthorizationDocumentStatus.Pending
}

fun ResultRow.toAuthorizationDocument(
    requestedBy: AuthorizationAgent,
    requestedPrincipal: AuthorizationAgent,
    requestedOf: AuthorizationAgent,
    requestedScopes: List<RequestedAuthorizationScope>,
    approvedBy: AuthorizationAgent? = null,
    now: Instant,
): AuthorizationDocument = AuthorizationDocument(
    id = this[AuthorizationDocumentTable.id].value.toString(),
    documentType = this[AuthorizationDocumentTable.documentType],
    status = toAuthorizationDocumentStatus(now),
    requestedScopes = requestedScopes,
    externalReference = this[AuthorizationDocumentTable.externalReference],
    meta = this[AuthorizationDocumentTable.meta],
    requestedGrantValidFrom = this[AuthorizationDocumentTable.requestedGrantValidFrom],
    requestedGrantValidTo = this[AuthorizationDocumentTable.requestedGrantValidTo],
    validFrom = this[AuthorizationDocumentTable.validFrom],
    validTo = this[AuthorizationDocumentTable.validTo],
    createdAt = this[AuthorizationDocumentTable.createdAt],
    updatedAt = this[AuthorizationDocumentTable.updatedAt],
    requestedBy = requestedBy,
    requestedPrincipal = requestedPrincipal,
    requestedOf = requestedOf,
    approvedBy = approvedBy,
    authorizationGrants = null,
    pdfBytes = this[AuthorizationDocumentTable.file],
)

object AuthorizationDocumentScopeTable : UUIDTable("auth_v1.authorization_document_scope") {
    val documentId = javaUUID("document_id").references(AuthorizationDocumentTable.id)
    val resourceType = customEnumeration(
        name = "resource_type",
        sql = "auth_v1.authorization_resource_type",
        fromDb = { value -> ResourceType.valueOf(value as String) },
        toDb = { PGEnum("auth_v1.authorization_resource_type", it) },
    )
}

fun ResultRow.toRequestedAuthorizationScope(
    constraints: List<AuthorizationScopeConstraint>,
): RequestedAuthorizationScope = RequestedAuthorizationScope(
    resourceType = this[AuthorizationDocumentScopeTable.resourceType],
    constraints = constraints,
)

object AuthorizationDocumentScopeConstraintTable : UUIDTable("auth_v1.authorization_document_scope_constraint") {
    val scopeId = javaUUID("scope_id").references(AuthorizationDocumentScopeTable.id)
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

fun ResultRow.toAuthorizationScopeConstraint(): AuthorizationScopeConstraint {
    val attribute = this[AuthorizationDocumentScopeConstraintTable.attribute]
    val values = this[AuthorizationDocumentScopeConstraintTable.value]
    return AuthorizationScopeConstraint(
        constraintKind = this[AuthorizationDocumentScopeConstraintTable.kind],
        attribute = attribute,
        value = when (attribute) {
            AuthorizationScopeConstraintAttribute.MeteringPointId -> ResourceConstraint.MeteringPoints(
                values.map(MeteringPointId::create).toSet(),
            )

            AuthorizationScopeConstraintAttribute.ContractType -> ResourceConstraint.ContractTypes(
                values.map(MeteringPointContractType::valueOf).toSet(),
            )

            AuthorizationScopeConstraintAttribute.ValidFrom -> ResourceConstraint.ValidFrom(
                Instant.parse(values.single()),
            )
        },
    )
}
