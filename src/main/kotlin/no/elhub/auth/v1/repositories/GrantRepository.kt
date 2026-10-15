package no.elhub.auth.v1.repositories

import no.elhub.auth.v0.features.common.PGEnum
import no.elhub.auth.v0.features.common.Page
import no.elhub.auth.v0.features.common.Pagination
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v0.features.common.party.AuthorizationPartyTable
import no.elhub.auth.v1.common.now
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationGrantSourceType
import no.elhub.auth.v1.domain.AuthorizationGrantStatus
import no.elhub.auth.v1.domain.AuthorizationGrantType
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintAttribute
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintKind
import no.elhub.auth.v1.domain.PermissionCapability
import no.elhub.auth.v1.domain.ResourceType
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.datetime.timestamp
import org.postgresql.util.PGobject
import java.util.UUID

interface GrantRepository {
    suspend fun find(grantId: UUID): AuthorizationGrant
    suspend fun findBySourceIds(
        sourceType: AuthorizationGrantSourceType,
        sourceIds: List<UUID>
    ): Map<UUID, AuthorizationGrant>

    suspend fun findAll(
        party: AuthorizationParty,
        pagination: Pagination
    ): Page<AuthorizationGrant>

    suspend fun insert(grant: AuthorizationGrant): AuthorizationGrant
    suspend fun update(grantId: UUID, newStatus: AuthorizationGrantStatus): AuthorizationGrant
}

class ExposedGrantRepository : GrantRepository {
    override suspend fun find(grantId: UUID): AuthorizationGrant {
        TODO("Not yet implemented")
    }

    override suspend fun findBySourceIds(sourceType: AuthorizationGrantSourceType, sourceIds: List<UUID>): Map<UUID, AuthorizationGrant> {
        TODO("Not yet implemented")
    }

    override suspend fun findAll(party: AuthorizationParty, pagination: Pagination): Page<AuthorizationGrant> {
        TODO("Not yet implemented")
    }

    override suspend fun insert(grant: AuthorizationGrant): AuthorizationGrant {
        TODO("Not yet implemented")
    }

    override suspend fun update(grantId: UUID, newStatus: AuthorizationGrantStatus): AuthorizationGrant {
        TODO("Not yet implemented")
    }
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
    val principal = javaUUID("principal").references(AuthorizationPartyTable.id)
    val grantedBy = javaUUID("granted_by").references(AuthorizationPartyTable.id)
    val grantedTo = javaUUID("granted_to").references(AuthorizationPartyTable.id)
    val approvedBy = javaUUID("approved_by").references(AuthorizationPartyTable.id)
    val status = customEnumeration(
        name = "status",
        sql = "auth_v1.authorization_grant_status",
        fromDb = { value -> DatabaseGrantStatus.valueOf(value as String) },
        toDb = { PGEnum("auth_v1.authorization_grant_status", it) },
    )
    val validFrom = timestamp("valid_from").clientDefault { now() }
    val validTo = timestamp("valid_to").clientDefault { now() }
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
