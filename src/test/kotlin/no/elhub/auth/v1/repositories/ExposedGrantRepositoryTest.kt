package no.elhub.auth.v1.repositories

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.common.auth.PartyType
import no.elhub.auth.v0.config.withTransaction
import no.elhub.auth.v0.features.common.Pagination
import no.elhub.auth.v0.features.common.PostgresTestContainer
import no.elhub.auth.v0.features.common.PostgresTestContainerExtension
import no.elhub.auth.v0.features.common.party.AuthorizationPartyTable
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
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.nanoseconds
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
        validFrom: Instant = createdAt,
        validTo: Instant? = future,
        scopes: List<GrantedAuthorizationScope> = emptyList(),
        principal: AuthorizationParty = party(PartyType.Organization),
        grantedBy: AuthorizationParty = party(PartyType.Person),
        grantedTo: AuthorizationParty = party(PartyType.OrganizationEntity),
        approvedBy: AuthorizationParty = party(PartyType.Person),
        created: Instant = createdAt,
    ): AuthorizationGrant = withTransaction {
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
            it[AuthorizationGrantTable.validFrom] = validFrom
            it[AuthorizationGrantTable.validTo] = validTo
            it[AuthorizationGrantTable.createdAt] = created
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
            validFrom = validFrom,
            validTo = validTo,
            createdAt = created,
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
            insertGrant(validTo = null) to AuthorizationGrantStatus.Active,
            insertGrant(validTo = past) to AuthorizationGrantStatus.Expired,
            insertGrant(status = DatabaseGrantStatus.Revoked, validTo = past) to AuthorizationGrantStatus.Revoked,
            insertGrant(status = DatabaseGrantStatus.Exhausted, validTo = past) to AuthorizationGrantStatus.Exhausted,
        )

        grants.forEach { (grant, status) ->
            repository.find(grant.id) shouldBe grant.copy(status = status)
        }
    }

    test("find derives Pending for a grant with a future validFrom") {
        val grant = insertGrant(validFrom = future, validTo = future + 1.days)

        val result = repository.find(grant.id)

        result.status.name shouldBe "Pending"
        assertGrant(result.copy(status = grant.status), grant)
    }

    listOf(
        Triple("just before validFrom", future - 1.nanoseconds, "Pending"),
        Triple("at validFrom", future, "Active"),
        Triple("just after validFrom", future + 1.nanoseconds, "Active"),
        Triple("just before validTo", future + 1.days - 1.nanoseconds, "Active"),
        Triple("at validTo", future + 1.days, "Expired"),
        Triple("just after validTo", future + 1.days + 1.nanoseconds, "Expired"),
    ).forEach { (boundary, now, expectedStatus) ->
        test("grant status is $expectedStatus $boundary") {
            val grant = insertGrant(validFrom = future, validTo = future + 1.days)

            withTransaction {
                val row = AuthorizationGrantTable.selectAll().where { AuthorizationGrantTable.id eq grant.id }.single()

                row.toAuthorizationGrant(
                    principal = grant.principal,
                    grantedBy = grant.grantedBy,
                    grantedTo = grant.grantedTo,
                    approvedBy = grant.approvedBy,
                    scopes = grant.scopes,
                    now = now,
                ).status.name shouldBe expectedStatus
            }
        }
    }

    test("grant without an end date is Pending before validFrom and Active from validFrom onwards") {
        val grant = insertGrant(validFrom = future, validTo = null)

        withTransaction {
            val row = AuthorizationGrantTable.selectAll().where { AuthorizationGrantTable.id eq grant.id }.single()
            listOf(
                future - 1.nanoseconds to "Pending",
                future to "Active",
                future + 1.nanoseconds to "Active",
                future + 1.days to "Active",
            ).forEach { (now, expectedStatus) ->
                row.toAuthorizationGrant(
                    principal = grant.principal,
                    grantedBy = grant.grantedBy,
                    grantedTo = grant.grantedTo,
                    approvedBy = grant.approvedBy,
                    scopes = grant.scopes,
                    now = now,
                ).status.name shouldBe expectedStatus
            }
        }
    }

    test("terminal statuses take precedence over a future validFrom for both grant types") {
        AuthorizationGrantType.entries.forEach { type ->
            listOf(DatabaseGrantStatus.Revoked, DatabaseGrantStatus.Exhausted).forEach { status ->
                val grant = insertGrant(type = type, status = status, validFrom = future, validTo = future + 1.days)

                repository.find(grant.id) shouldBe grant
            }
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
        assertGrant(result.getValue(first.sourceId).single(), first)
        assertGrant(result.getValue(second.sourceId).single(), second.copy(status = AuthorizationGrantStatus.Expired))
        repository.findBySourceIds(AuthorizationGrantSourceType.Request, listOf(unrelated.sourceId)) shouldBe emptyMap()
    }

    test("findBySourceIds reads request-sourced grants") {
        val grant = insertGrant(sourceType = AuthorizationGrantSourceType.Request, scopes = listOf(constrained))

        val result = repository.findBySourceIds(AuthorizationGrantSourceType.Request, listOf(grant.sourceId))

        result.keys shouldBe setOf(grant.sourceId)
        assertGrant(result.getValue(grant.sourceId).single(), grant)
    }

    test("findBySourceIds derives Pending for a grant with a future validFrom") {
        val grant = insertGrant(validFrom = future, validTo = future + 1.days)

        val result = repository.findBySourceIds(grant.sourceType, listOf(grant.sourceId)).getValue(grant.sourceId).single()

        result.status.name shouldBe "Pending"
        assertGrant(result.copy(status = grant.status), grant)
    }

    test("findBySourceIds preserves multiple grants for the same source") {
        val first = insertGrant(sourceType = AuthorizationGrantSourceType.Request)
        val second = insertGrant(sourceId = first.sourceId, sourceType = first.sourceType)

        val result = repository.findBySourceIds(first.sourceType, listOf(first.sourceId))

        result.getValue(first.sourceId).shouldContainExactlyInAnyOrder(first, second)
    }

    test("findBySourceIds returns an empty map for empty input or unknown sources") {
        repository.findBySourceIds(AuthorizationGrantSourceType.Document, emptyList()) shouldBe emptyMap()
        repository.findBySourceIds(AuthorizationGrantSourceType.Document, listOf(UUID.randomUUID())) shouldBe emptyMap()
    }

    test("findAll includes principal, recipient and grantor once but excludes approver-only and unrelated parties") {
        val caller = party(PartyType.Person)
        val principalGrant = insertGrant(principal = caller, scopes = listOf(constrained, unconstrained))
        val recipientGrant = insertGrant(grantedTo = caller, validTo = past)
        val bothGrant = insertGrant(principal = caller, grantedTo = caller, status = DatabaseGrantStatus.Revoked)
        val grantorGrant = insertGrant(grantedBy = caller)
        insertGrant(approvedBy = caller)
        insertGrant()
        insertGrant(principal = caller.copy(type = PartyType.Organization))

        val result = repository.findAll(caller, Pagination())

        result.totalItems shouldBe 4L
        result.items.map { it.id }.shouldContainExactlyInAnyOrder(
            principalGrant.id,
            recipientGrant.id,
            bothGrant.id,
            grantorGrant.id,
        )
        assertGrant(result.items.single { it.id == principalGrant.id }, principalGrant)
        assertGrant(result.items.single { it.id == recipientGrant.id }, recipientGrant.copy(status = AuthorizationGrantStatus.Expired))
        assertGrant(result.items.single { it.id == bothGrant.id }, bothGrant)
        assertGrant(result.items.single { it.id == grantorGrant.id }, grantorGrant)
    }

    test("findAll sorts newest first with stable ID ordering and preserves counts beyond the last page") {
        val caller = party(PartyType.Person)
        val older = insertGrant(principal = caller, created = createdAt - 1.days)
        val tied = List(2) { insertGrant(principal = caller) }
            .sortedByDescending { it.id.toString() }
        val expected = tied + older

        val first = repository.findAll(caller, Pagination(page = 0, size = 2))
        first.items.map { it.id } shouldBe expected.take(2).map { it.id }
        first.totalItems shouldBe 3L
        first.pagination shouldBe Pagination(page = 0, size = 2)
        first.totalPages shouldBe 2

        val last = repository.findAll(caller, Pagination(page = 1, size = 2))
        last.items.map { it.id } shouldBe listOf(older.id)
        last.totalItems shouldBe 3L

        val beyond = repository.findAll(caller, Pagination(page = 2, size = 2))
        beyond.items shouldBe emptyList()
        beyond.totalItems shouldBe 3L
    }

    test("findAll derives Pending for a grant with a future validFrom") {
        val grant = insertGrant(validFrom = future, validTo = future + 1.days)

        val page = repository.findAll(grant.principal, Pagination())
        val result = page.items.single()

        page.totalItems shouldBe 1L
        result.status.name shouldBe "Pending"
        assertGrant(result.copy(status = grant.status), grant)
    }

    test("findAll returns an empty page for an unknown party") {
        val pagination = Pagination(page = 1, size = 2)
        val result = repository.findAll(party(PartyType.Person), pagination)

        result.items shouldBe emptyList()
        result.totalItems shouldBe 0L
        result.pagination shouldBe pagination
    }

    test("insert persists all fields, reuses parties and round-trips scopes and typed constraints") {
        val template = insertGrant()
        val grant = template.copy(id = UUID.randomUUID(), sourceId = UUID.randomUUID(), scopes = listOf(constrained, unconstrained))
        val existing = partyRepository.findOrInsert(grant.principal.type, grant.principal.id)

        assertGrant(repository.insert(grant), grant)
        assertGrant(repository.find(grant.id), grant)
        assertGrant(repository.findBySourceIds(grant.sourceType, listOf(grant.sourceId)).getValue(grant.sourceId).single(), grant)
        partyRepository.findOrInsert(grant.principal.type, grant.principal.id) shouldBe existing
        withTransaction {
            AuthorizationPartyTable.selectAll().where { AuthorizationPartyTable.partyId eq grant.principal.id }.count() shouldBe 1L
        }
    }

    test("insert persists a persistent grant without an end date") {
        val template = insertGrant(type = AuthorizationGrantType.Persistent)
        val grant = template.copy(id = UUID.randomUUID(), sourceId = UUID.randomUUID(), validTo = null)

        repository.insert(grant) shouldBe grant
        repository.find(grant.id) shouldBe grant
        repository.find(grant.id).status shouldBe AuthorizationGrantStatus.Active
    }

    test("insert returns Pending when an Active template has a future validFrom") {
        val template = insertGrant()
        val grant = template.copy(
            id = UUID.randomUUID(),
            sourceId = UUID.randomUUID(),
            validFrom = future,
            validTo = future + 1.days,
        )

        val result = repository.insert(grant)

        result.status.name shouldBe "Pending"
        assertGrant(result.copy(status = grant.status), grant)
    }

    test("insert creates missing parties and persists both grant types and all statuses without scopes") {
        val template = insertGrant()
        AuthorizationGrantType.entries.forEach { type ->
            AuthorizationGrantStatus.entries.forEach { status ->
                val person = party(PartyType.Person)
                val grant = template.copy(
                    id = UUID.randomUUID(),
                    type = type,
                    sourceType = AuthorizationGrantSourceType.Request,
                    sourceId = UUID.randomUUID(),
                    principal = person,
                    grantedBy = person,
                    grantedTo = person,
                    approvedBy = person,
                    status = status,
                    validFrom = if (status == AuthorizationGrantStatus.Pending) future else createdAt,
                    validTo = if (status == AuthorizationGrantStatus.Expired) past else future,
                )

                repository.insert(grant) shouldBe grant
                repository.find(grant.id) shouldBe grant
            }
        }
    }

    test("insert rolls back grant, scopes, constraints and new parties with the enclosing transaction") {
        val template = insertGrant()
        val person = party(PartyType.Person)
        val grant = template.copy(
            id = UUID.randomUUID(),
            sourceId = UUID.randomUUID(),
            principal = person,
            grantedBy = person,
            grantedTo = person,
            approvedBy = person,
            scopes = listOf(constrained, unconstrained),
        )
        val before = withTransaction {
            AuthorizationGrantScopeTable.selectAll().count() to AuthorizationGrantScopeConstraintTable.selectAll().count()
        }

        shouldThrow<IllegalStateException> {
            withTransaction {
                repository.insert(grant)
                error("Abort grant issuance")
            }
        }

        shouldThrow<RepositoryReadError.NotFoundError> { repository.find(grant.id) }
        withTransaction {
            AuthorizationGrantScopeTable.selectAll().count() shouldBe before.first
            AuthorizationGrantScopeConstraintTable.selectAll().count() shouldBe before.second
            AuthorizationPartyTable.selectAll().where { AuthorizationPartyTable.partyId eq person.id }.count() shouldBe 0L
        }
    }

    test("update allows revocation for both types and exhaustion only for consumable grants") {
        val legalTransitions = listOf(
            AuthorizationGrantType.Persistent to AuthorizationGrantStatus.Revoked,
            AuthorizationGrantType.Consumable to AuthorizationGrantStatus.Revoked,
            AuthorizationGrantType.Consumable to AuthorizationGrantStatus.Exhausted,
        )
        val illegalTransitions = listOf(
            AuthorizationGrantType.Persistent to AuthorizationGrantStatus.Exhausted,
        )
        legalTransitions.forEach { (type, status) ->
            val grant = insertGrant(type = type, scopes = listOf(constrained, unconstrained))

            val result = repository.update(grant.id, status)

            result.updatedAt shouldBeGreaterThan grant.updatedAt
            assertGrant(result, grant.copy(status = status, updatedAt = result.updatedAt))
            assertGrant(repository.find(grant.id), result)
        }
        illegalTransitions.forEach { (type, status) ->
            val grant = insertGrant(type = type, scopes = listOf(constrained, unconstrained))
            val error = shouldThrow<RepositoryWriteError.ConflictError> { repository.update(grant.id, status) }
            error.detail shouldBe "Invalid transition for grant ${grant.id} from Active to $status"
        }
    }

    test("update rejects expired grants") {
        AuthorizationGrantType.entries.forEach { type ->
            val expired = insertGrant(type = type, validTo = past)
            AuthorizationGrantStatus.entries.forEach { target ->
                shouldThrow<RepositoryWriteError.ExpiredError> { repository.update(expired.id, target) }
                    .detail shouldBe "Grant ${expired.id} has expired"
                repository.find(expired.id) shouldBe expired.copy(status = AuthorizationGrantStatus.Expired)
            }
            listOf(DatabaseGrantStatus.Revoked, DatabaseGrantStatus.Exhausted).forEach { status ->
                val terminal = insertGrant(type = type, status = status, validTo = past)
                shouldThrow<RepositoryWriteError.ConflictError> {
                    repository.update(terminal.id, AuthorizationGrantStatus.Revoked)
                }
                repository.find(terminal.id) shouldBe terminal
            }
        }
    }

    test("update rejects exhaustion of a pending consumable grant without changing the row") {
        val grant = insertGrant(validFrom = future, validTo = future + 1.days, scopes = listOf(constrained, unconstrained))

        shouldThrow<RepositoryWriteError.ConflictError> {
            repository.update(grant.id, AuthorizationGrantStatus.Exhausted)
        }

        withTransaction {
            val row = AuthorizationGrantTable.selectAll().where { AuthorizationGrantTable.id eq grant.id }.single()
            row[AuthorizationGrantTable.status] shouldBe DatabaseGrantStatus.Active
            row[AuthorizationGrantTable.updatedAt] shouldBe grant.updatedAt
        }
        val result = repository.find(grant.id)
        result.status.name shouldBe "Pending"
        assertGrant(result.copy(status = grant.status), grant)
    }

    test("update allows revocation of pending grants for both types") {
        AuthorizationGrantType.entries.forEach { type ->
            val grant = insertGrant(
                type = type,
                validFrom = future,
                validTo = future + 1.days,
                scopes = listOf(constrained, unconstrained),
            )

            val result = repository.update(grant.id, AuthorizationGrantStatus.Revoked)

            result.updatedAt shouldBeGreaterThan grant.updatedAt
            assertGrant(result, grant.copy(status = AuthorizationGrantStatus.Revoked, updatedAt = result.updatedAt))
            assertGrant(repository.find(grant.id), result)
            withTransaction {
                val row = AuthorizationGrantTable.selectAll().where { AuthorizationGrantTable.id eq grant.id }.single()
                row[AuthorizationGrantTable.status] shouldBe DatabaseGrantStatus.Revoked
                row[AuthorizationGrantTable.updatedAt] shouldBe result.updatedAt
            }
        }
    }

    test("update throws a modeled write not found error for an unknown grant") {
        val id = UUID.randomUUID()

        shouldThrow<RepositoryWriteError.NotFoundError> { repository.update(id, AuthorizationGrantStatus.Revoked) }
            .detail shouldBe "Grant not found: $id"
    }

    test("concurrent updates produce one successful transition and one conflict") {
        val grant = insertGrant(scopes = listOf(constrained, unconstrained))
        val ready = CompletableDeferred<Unit>()

        val results = coroutineScope {
            listOf(AuthorizationGrantStatus.Revoked, AuthorizationGrantStatus.Exhausted).map { target ->
                async {
                    ready.await()
                    try {
                        Result.success(repository.update(grant.id, target))
                    } catch (error: RepositoryWriteError.ConflictError) {
                        Result.failure<AuthorizationGrant>(error)
                    }
                }
            }.also { ready.complete(Unit) }.awaitAll()
        }

        results.count { it.isSuccess } shouldBe 1
        results.count { it.exceptionOrNull() is RepositoryWriteError.ConflictError } shouldBe 1
        assertGrant(repository.find(grant.id), results.single { it.isSuccess }.getOrThrow())
    }
})
