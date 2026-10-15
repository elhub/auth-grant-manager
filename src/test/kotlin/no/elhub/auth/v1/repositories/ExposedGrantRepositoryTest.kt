package no.elhub.auth.v1.repositories

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import no.elhub.auth.v0.config.withTransaction
import no.elhub.auth.v0.features.common.PostgresTestContainer
import no.elhub.auth.v0.features.common.PostgresTestContainerExtension
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v0.features.common.party.PartyType
import no.elhub.auth.v1.RepositoryReadError
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationGrantSourceType
import no.elhub.auth.v1.domain.AuthorizationGrantStatus
import no.elhub.auth.v1.domain.AuthorizationGrantType
import no.elhub.auth.v1.domain.AuthorizationScopeConstraint
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintAttribute
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintKind
import no.elhub.auth.v1.domain.GrantedAuthorizationScope
import no.elhub.auth.v1.domain.MeteringPointContractType
import no.elhub.auth.v1.domain.MeteringPointId
import no.elhub.auth.v1.domain.PermissionCapability
import no.elhub.auth.v1.domain.ResourceConstraint
import no.elhub.auth.v1.domain.ResourceType
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import java.util.UUID
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class ExposedGrantRepositoryTest : FunSpec({
    extension(PostgresTestContainerExtension())

    beforeSpec {
        Database.connect(
            url = PostgresTestContainer.JDBC_URL,
            driver = PostgresTestContainer.DRIVER,
            user = PostgresTestContainer.USERNAME,
            password = PostgresTestContainer.PASSWORD,
        )
    }

    val partyRepository = ExposedPartyRepository()
    val repository = ExposedGrantRepository(partyRepository)
    val createdAt = Instant.parse("2025-01-01T00:00:00Z")
    val future = Instant.parse("2100-01-01T00:00:00Z")
    val past = Instant.parse("2000-01-01T00:00:00Z")

    fun party(type: PartyType) = AuthorizationParty(UUID.randomUUID().toString(), type)

    val constrained = GrantedAuthorizationScope(
        capability = PermissionCapability.Write,
        resourceType = ResourceType.MeteringPointContract,
        constraints = listOf(
            AuthorizationScopeConstraint(
                AuthorizationScopeConstraintKind.AppliesTo,
                AuthorizationScopeConstraintAttribute.MeteringPointId,
                ResourceConstraint.MeteringPoints(
                    setOf(MeteringPointId.create("707057500000000001"), MeteringPointId.create("707057500000000002")),
                ),
            ),
            AuthorizationScopeConstraint(
                AuthorizationScopeConstraintKind.AllowedChanges,
                AuthorizationScopeConstraintAttribute.ContractType,
                ResourceConstraint.ContractTypes(setOf(MeteringPointContractType.EndUser, MeteringPointContractType.EnergySupplier)),
            ),
            AuthorizationScopeConstraint(
                AuthorizationScopeConstraintKind.AllowedChanges,
                AuthorizationScopeConstraintAttribute.ValidFrom,
                ResourceConstraint.ValidFrom(createdAt),
            ),
        ),
    )
    val unconstrained = GrantedAuthorizationScope(PermissionCapability.Read, ResourceType.MeteringPointContract, emptyList())

    suspend fun insertGrant(
        sourceId: UUID = UUID.randomUUID(),
        sourceType: AuthorizationGrantSourceType = AuthorizationGrantSourceType.Document,
        type: AuthorizationGrantType = AuthorizationGrantType.Consumable,
        status: DatabaseGrantStatus = DatabaseGrantStatus.Active,
        validTo: Instant = future,
        scopes: List<GrantedAuthorizationScope> = emptyList(),
    ): AuthorizationGrant = withTransaction {
        val principal = party(PartyType.Organization)
        val grantedBy = party(PartyType.Person)
        val grantedTo = party(PartyType.OrganizationEntity)
        val approvedBy = party(PartyType.Person)
        suspend fun partyId(party: AuthorizationParty) = partyRepository.findOrInsert(party.type, party.id).id
        val principalId = partyId(principal)
        val byId = partyId(grantedBy)
        val toId = partyId(grantedTo)
        val approverId = partyId(approvedBy)
        val grantId = AuthorizationGrantTable.insert {
            it[AuthorizationGrantTable.type] = type
            it[AuthorizationGrantTable.sourceType] = sourceType
            it[AuthorizationGrantTable.sourceId] = sourceId
            it[AuthorizationGrantTable.principal] = principalId
            it[AuthorizationGrantTable.grantedBy] = byId
            it[AuthorizationGrantTable.grantedTo] = toId
            it[AuthorizationGrantTable.approvedBy] = approverId
            it[AuthorizationGrantTable.status] = status
            it[validFrom] = createdAt
            it[AuthorizationGrantTable.validTo] = validTo
            it[AuthorizationGrantTable.createdAt] = createdAt
            it[updatedAt] = createdAt + 1.days
        }[AuthorizationGrantTable.id].value

        scopes.forEach { scope ->
            val scopeId = AuthorizationGrantScopeTable.insert {
                it[AuthorizationGrantScopeTable.grantId] = grantId
                it[capability] = scope.capability
                it[resourceType] = scope.resourceType
            }[AuthorizationGrantScopeTable.id].value
            scope.constraints.forEach { constraint ->
                AuthorizationGrantScopeConstraintTable.insert {
                    it[AuthorizationGrantScopeConstraintTable.scopeId] = scopeId
                    it[kind] = constraint.constraintKind
                    it[attribute] = constraint.attribute
                    it[value] = when (val value = constraint.value) {
                        is ResourceConstraint.MeteringPoints -> value.ids.map { it.value }
                        is ResourceConstraint.ContractTypes -> value.types.map { it.name }
                        is ResourceConstraint.ValidFrom -> listOf(value.value.toString())
                    }
                }
            }
        }

        AuthorizationGrant(
            id = grantId,
            type = type,
            sourceType = sourceType,
            sourceId = sourceId,
            principal = principal,
            grantedBy = grantedBy,
            grantedTo = grantedTo,
            approvedBy = approvedBy,
            status = AuthorizationGrantStatus.valueOf(status.name),
            validFrom = createdAt,
            validTo = validTo,
            createdAt = createdAt,
            updatedAt = createdAt + 1.days,
            scopes = scopes,
        )
    }

    fun assertGrant(actual: AuthorizationGrant, expected: AuthorizationGrant) {
        actual.copy(scopes = emptyList()) shouldBe expected.copy(scopes = emptyList())
        actual.scopes.size shouldBe expected.scopes.size
        expected.scopes.forEach { scope ->
            val mapped = actual.scopes.single { it.capability == scope.capability }
            mapped.resourceType shouldBe scope.resourceType
            mapped.constraints.shouldContainExactlyInAnyOrder(scope.constraints)
        }
    }

    test("find maps grant fields, all party roles, scopes and typed constraints") {
        val grant = insertGrant(scopes = listOf(constrained, unconstrained))
        insertGrant(scopes = listOf(constrained))

        assertGrant(repository.find(grant.id), grant)
    }

    test("find maps a persistent grant without scopes") {
        val grant = insertGrant(type = AuthorizationGrantType.Persistent)

        repository.find(grant.id) shouldBe grant
    }

    test("find derives expiry with revoked and exhausted taking precedence") {
        val grants = listOf(
            insertGrant() to AuthorizationGrantStatus.Active,
            insertGrant(validTo = past) to AuthorizationGrantStatus.Expired,
            insertGrant(status = DatabaseGrantStatus.Revoked, validTo = past) to AuthorizationGrantStatus.Revoked,
            insertGrant(status = DatabaseGrantStatus.Exhausted, validTo = past) to AuthorizationGrantStatus.Exhausted,
        )

        grants.forEach { (grant, status) ->
            repository.find(grant.id) shouldBe grant.copy(status = status)
        }
    }

    test("find throws a modeled not found error for an unknown grant ID") {
        val id = UUID.randomUUID()

        shouldThrow<RepositoryReadError.NotFoundError> { repository.find(id) }.detail shouldBe "Grant not found: $id"
    }

    test("findBySourceIds maps grants by source ID and filters source type and missing IDs") {
        val first = insertGrant(scopes = listOf(constrained, unconstrained))
        val second = insertGrant(type = AuthorizationGrantType.Persistent, validTo = past)
        insertGrant(sourceId = first.sourceId, sourceType = AuthorizationGrantSourceType.Request)
        val unrelated = insertGrant(scopes = listOf(constrained))

        val result = repository.findBySourceIds(
            AuthorizationGrantSourceType.Document,
            listOf(first.sourceId, second.sourceId, first.sourceId, UUID.randomUUID()),
        )

        result.keys shouldBe setOf(first.sourceId, second.sourceId)
        assertGrant(result.getValue(first.sourceId), first)
        assertGrant(result.getValue(second.sourceId), second.copy(status = AuthorizationGrantStatus.Expired))
        repository.findBySourceIds(AuthorizationGrantSourceType.Request, listOf(unrelated.sourceId)) shouldBe emptyMap()
    }

    test("findBySourceIds reads request-sourced grants") {
        val grant = insertGrant(sourceType = AuthorizationGrantSourceType.Request, scopes = listOf(constrained))

        val result = repository.findBySourceIds(AuthorizationGrantSourceType.Request, listOf(grant.sourceId))

        result.keys shouldBe setOf(grant.sourceId)
        assertGrant(result.getValue(grant.sourceId), grant)
    }

    test("findBySourceIds returns an empty map for empty input or unknown sources") {
        repository.findBySourceIds(AuthorizationGrantSourceType.Document, emptyList()) shouldBe emptyMap()
        repository.findBySourceIds(AuthorizationGrantSourceType.Document, listOf(UUID.randomUUID())) shouldBe emptyMap()
    }
})
