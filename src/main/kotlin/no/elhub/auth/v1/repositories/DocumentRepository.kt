package no.elhub.auth.v1.repositories

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import no.elhub.auth.v0.config.withTransaction
import no.elhub.auth.v0.features.common.PGEnum
import no.elhub.auth.v0.features.common.Page
import no.elhub.auth.v0.features.common.Pagination
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v0.features.common.party.AuthorizationPartyTable
import no.elhub.auth.v0.features.grants.common.toAuthorizationParty
import no.elhub.auth.v1.RepositoryReadError
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
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.json.jsonb
import org.postgresql.util.PGobject
import java.util.UUID
import kotlin.time.Instant

interface DocumentRepository {
    suspend fun find(id: UUID): AuthorizationDocument
    suspend fun findAndSortByCreatedAt(
        requestedBy: AuthorizationParty,
        pagination: Pagination,
        statuses: List<AuthorizationDocumentStatus>,
    ): Page<AuthorizationDocument>

    suspend fun insert(document: AuthorizationDocument): AuthorizationDocument
    suspend fun confirm(
        documentId: UUID,
        signedFile: ByteArray,
        signatory: AuthorizationParty,
        grant: AuthorizationGrant,
    ): AuthorizationDocument
}

class ExposedDocumentRepository(
    private val partyRepository: PartyRepository,
) : DocumentRepository {
    override suspend fun find(id: UUID): AuthorizationDocument = withTransaction {
        val row = AuthorizationDocumentTable.selectAll()
            .where { AuthorizationDocumentTable.id eq id }
            .singleOrNull()
            ?: throw RepositoryReadError.NotFoundError("Document not found: $id")

        val scopes = (AuthorizationDocumentScopeTable leftJoin AuthorizationDocumentScopeConstraintTable)
            .selectAll()
            .where { AuthorizationDocumentScopeTable.documentId eq id }
            .groupBy { it[AuthorizationDocumentScopeTable.id].value }
            .values.map { scopeRows ->
                scopeRows.first().toRequestedAuthorizationScope(
                    constraints = scopeRows.filter {
                        it.getOrNull(AuthorizationDocumentScopeConstraintTable.id) != null
                    }.map { it.toAuthorizationScopeConstraint() },
                )
            }

        row.toAuthorizationDocument(
            requestedBy = partyRepository.find(row[AuthorizationDocumentTable.requestedBy]).toAuthorizationParty(),
            requestedPrincipal = partyRepository.find(row[AuthorizationDocumentTable.requestedPrincipal]).toAuthorizationParty(),
            requestedOf = partyRepository.find(row[AuthorizationDocumentTable.requestedOf]).toAuthorizationParty(),
            requestedScopes = scopes,
            approvedBy = row[AuthorizationDocumentTable.approvedBy]?.let { partyRepository.find(it).toAuthorizationParty() },
            now = now(),
        )
    }

    override suspend fun findAndSortByCreatedAt(
        requestedBy: AuthorizationParty,
        pagination: Pagination,
        statuses: List<AuthorizationDocumentStatus>
    ): Page<AuthorizationDocument> {
        TODO("Not yet implemented")
    }

    override suspend fun insert(document: AuthorizationDocument): AuthorizationDocument {
        TODO("Not yet implemented")
    }

    override suspend fun confirm(documentId: UUID, signedFile: ByteArray, signatory: AuthorizationParty, grant: AuthorizationGrant): AuthorizationDocument {
        TODO("Not yet implemented")
    }
}

object AuthorizationDocumentTable : UUIDTable("auth_v1.authorization_document") {
    val documentType = customEnumeration(
        name = "document_type",
        sql = "auth_v1.authorization_document_type",
        fromDb = { value -> AuthorizationDocumentType.valueOf(value as String) },
        toDb = { PGEnum("auth_v1.authorization_document_type", it) },
    )
    val requestedBy = javaUUID("requested_by").references(AuthorizationPartyTable.id)
    val requestedPrincipal = javaUUID("requested_principal").references(AuthorizationPartyTable.id)
    val requestedOf = javaUUID("requested_of").references(AuthorizationPartyTable.id)
    val approvedBy = javaUUID("approved_by").references(AuthorizationPartyTable.id).nullable()
    val signedAt = timestamp("signed_at").nullable()
    val externalReference = varchar("external_reference", 255).nullable()
    val meta = jsonb("meta", Json, JsonObject.serializer())
    val file = binary("file")
    val validFrom = timestamp("valid_from").clientDefault { now() }
    val validTo = timestamp("valid_to").clientDefault { now() }
    val createdAt = timestamp("created_at").clientDefault { now() }
    val updatedAt = timestamp("updated_at").clientDefault { now() }
}

fun ResultRow.toAuthorizationDocument(
    requestedBy: AuthorizationParty,
    requestedPrincipal: AuthorizationParty,
    requestedOf: AuthorizationParty,
    requestedScopes: List<RequestedAuthorizationScope>,
    approvedBy: AuthorizationParty? = null,
    now: Instant,
): AuthorizationDocument = AuthorizationDocument(
    id = this[AuthorizationDocumentTable.id].value.toString(),
    documentType = this[AuthorizationDocumentTable.documentType],
    status = when {
        approvedBy != null -> AuthorizationDocumentStatus.Accepted
        this[AuthorizationDocumentTable.validTo] <= now -> AuthorizationDocumentStatus.Expired
        else -> AuthorizationDocumentStatus.Pending
    },
    requestedScopes = requestedScopes,
    externalReference = this[AuthorizationDocumentTable.externalReference],
    meta = this[AuthorizationDocumentTable.meta],
    validTo = this[AuthorizationDocumentTable.validTo],
    createdAt = this[AuthorizationDocumentTable.createdAt],
    updatedAt = this[AuthorizationDocumentTable.updatedAt],
    requestedBy = requestedBy,
    requestedPrincipal = requestedPrincipal,
    requestedOf = requestedOf,
    approvedBy = approvedBy,
    authorizationGrant = null,
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
