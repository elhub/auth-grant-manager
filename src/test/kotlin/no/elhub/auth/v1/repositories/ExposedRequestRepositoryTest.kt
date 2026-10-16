package no.elhub.auth.v1.repositories

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import no.elhub.auth.v0.config.withTransaction
import no.elhub.auth.v0.features.common.Pagination
import no.elhub.auth.v0.features.common.PostgresTestContainer
import no.elhub.auth.v0.features.common.PostgresTestContainerExtension
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v0.features.common.party.PartyType
import no.elhub.auth.v1.RepositoryReadError
import no.elhub.auth.v1.RepositoryWriteError
import no.elhub.auth.v1.common.now
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationGrantSourceType
import no.elhub.auth.v1.domain.AuthorizationGrantType
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
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class ExposedRequestRepositoryTest : FunSpec({
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
    val grantRepository = ExposedGrantRepository(partyRepository)
    val repository = ExposedRequestRepository(partyRepository, grantRepository)
    val createdAt = Instant.parse("2025-01-01T00:00:00Z")
    val future = Instant.parse("2100-01-01T00:00:00Z")
    val past = Instant.parse("2000-01-01T00:00:00Z")
    val metadata = Json.parseToJsonElement(
        """{"name":"alberto","nestedStuff":{"values":[null,false,1.5]}}""",
    ).jsonObject

    fun party(type: PartyType = PartyType.Person) = AuthorizationParty(id = UUID.randomUUID().toString(), type = type)

    fun grant(request: AuthorizationRequest) = AuthorizationGrant.new(
        type = AuthorizationGrantType.Consumable,
        sourceType = AuthorizationGrantSourceType.Request,
        sourceId = request.id,
        principal = request.requestedPrincipal,
        grantedBy = request.requestedOf,
        grantedTo = request.requestedBy,
        approvedBy = party(),
        validFrom = createdAt,
        validTo = future,
        scopes = emptyList(),
    ).copy(createdAt = createdAt, updatedAt = createdAt)

    suspend fun insertRequest(
        requestedBy: AuthorizationParty,
        requestedPrincipal: AuthorizationParty = requestedBy,
        requestedOf: AuthorizationParty = requestedBy,
        approvedBy: AuthorizationParty? = null,
        status: DatabaseRequestStatus = DatabaseRequestStatus.Pending,
        validTo: Instant = future,
        created: Instant = createdAt,
        externalReference: String? = null,
        scopes: List<RequestedAuthorizationScope> = emptyList(),
        meta: JsonObject = JsonObject(emptyMap()),
        redirectUri: String? = null,
        validFrom: Instant = createdAt + 2.days,
    ): UUID = withTransaction {
        suspend fun partyId(party: AuthorizationParty) = partyRepository.findOrInsert(party.type, party.id).id
        val byId = partyId(requestedBy)
        val principalId = partyId(requestedPrincipal)
        val ofId = partyId(requestedOf)
        val approverId = approvedBy?.let { partyId(it) }
        val requestId = AuthorizationRequestTable.insert {
            it[requestType] = AuthorizationRequestType.ChangeOfEnergySupplierForOrganization
            it[AuthorizationRequestTable.status] = status
            it[AuthorizationRequestTable.requestedBy] = byId
            it[AuthorizationRequestTable.requestedPrincipal] = principalId
            it[AuthorizationRequestTable.requestedOf] = ofId
            it[AuthorizationRequestTable.approvedBy] = approverId
            it[AuthorizationRequestTable.validTo] = validTo
            it[AuthorizationRequestTable.createdAt] = created
            it[updatedAt] = created + 1.days
            it[AuthorizationRequestTable.validFrom] = validFrom
            it[AuthorizationRequestTable.redirectUri] = redirectUri
            it[AuthorizationRequestTable.externalReference] = externalReference
            it[AuthorizationRequestTable.meta] = meta
        }[AuthorizationRequestTable.id].value

        scopes.forEach { scope ->
            val scopeId = AuthorizationRequestScopeTable.insert {
                it[AuthorizationRequestScopeTable.requestId] = requestId
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
        requestId
    }

    test("find maps request fields, parties, approver, scopes and constraints") {
        val requestedBy = party(PartyType.Organization)
        val requestedPrincipal = party(PartyType.Organization)
        val requestedOf = party()
        val approver = party()
        val appliesTo = AuthorizationScopeConstraint(
            AuthorizationScopeConstraintKind.AppliesTo,
            AuthorizationScopeConstraintAttribute.MeteringPointId,
            ResourceConstraint.MeteringPoints(
                setOf(
                    MeteringPointId.create("707057500000000001"),
                    MeteringPointId.create("707057500000000002"),
                ),
            ),
        )
        val allowedChanges = AuthorizationScopeConstraint(
            constraintKind = AuthorizationScopeConstraintKind.AllowedChanges,
            attribute = AuthorizationScopeConstraintAttribute.ContractType,
            value = ResourceConstraint.ContractTypes(setOf(MeteringPointContractType.EnergySupplier)),
        )
        val constrained = RequestedAuthorizationScope(ResourceType.MeteringPointContract, listOf(appliesTo, allowedChanges))
        val unconstrained = RequestedAuthorizationScope(ResourceType.MeteringPointContract, emptyList())
        val requestId = insertRequest(
            requestedBy = requestedBy,
            requestedPrincipal = requestedPrincipal,
            requestedOf = requestedOf,
            approvedBy = approver,
            externalReference = "contract-123",
            scopes = listOf(constrained, unconstrained),
        )
        insertRequest(requestedBy, scopes = listOf(constrained))

        val result = repository.find(requestId)

        result.copy(requestedScopes = emptyList()) shouldBe AuthorizationRequest(
            id = requestId,
            requestType = AuthorizationRequestType.ChangeOfEnergySupplierForOrganization,
            status = AuthorizationRequestStatus.Accepted,
            requestedScopes = emptyList(),
            externalReference = "contract-123",
            redirectUri = null,
            validFrom = createdAt + 2.days,
            validTo = future,
            meta = JsonObject(emptyMap()),
            createdAt = createdAt,
            updatedAt = createdAt + 1.days,
            requestedBy = requestedBy,
            requestedPrincipal = requestedPrincipal,
            requestedOf = requestedOf,
            approvedBy = approver,
            authorizationGrant = null,
        )
        result.requestedScopes.size shouldBe 2
        result.requestedScopes.single { it.constraints.isEmpty() } shouldBe unconstrained
        val mappedScope = result.requestedScopes.single { it.constraints.isNotEmpty() }
        mappedScope.resourceType shouldBe constrained.resourceType
        mappedScope.constraints.shouldContainExactlyInAnyOrder(appliesTo, allowedChanges)
    }

    test("find maps a request without scopes, approver or external reference") {
        val requestedBy = party()
        val requestId = insertRequest(requestedBy)

        val result = repository.find(requestId)

        result.id shouldBe requestId
        result.requestedBy shouldBe requestedBy
        result.requestedPrincipal shouldBe requestedBy
        result.requestedOf shouldBe requestedBy
        result.requestedScopes shouldBe emptyList()
        result.approvedBy shouldBe null
        result.externalReference shouldBe null
        result.redirectUri shouldBe null
        result.status shouldBe AuthorizationRequestStatus.Pending
    }

    test("find derives status with rejection and approval taking precedence over expiry") {
        val requestedBy = party()
        val approver = party()
        val expected = mapOf(
            insertRequest(requestedBy) to AuthorizationRequestStatus.Pending,
            insertRequest(requestedBy, approvedBy = approver) to AuthorizationRequestStatus.Accepted,
            insertRequest(requestedBy, approvedBy = approver, validTo = past) to AuthorizationRequestStatus.Accepted,
            insertRequest(requestedBy, validTo = past) to AuthorizationRequestStatus.Expired,
            insertRequest(requestedBy, status = DatabaseRequestStatus.Rejected) to AuthorizationRequestStatus.Rejected,
            insertRequest(requestedBy, status = DatabaseRequestStatus.Rejected, approvedBy = approver, validTo = past) to
                AuthorizationRequestStatus.Rejected,
        )

        expected.forEach { (requestId, status) ->
            repository.find(requestId).status shouldBe status
        }
    }

    test("find throws a modeled not found error for an unknown request ID") {
        val requestId = UUID.randomUUID()

        val error = shouldThrow<RepositoryReadError.NotFoundError> {
            repository.find(requestId)
        }

        error.detail shouldBe "Request not found: $requestId"
    }

    test("inserts request fields, reuses parties, and persists scopes and constraints") {
        val requestedBy = party(PartyType.Organization)
        val recipient = party()
        val existingrequestedBy = partyRepository.findOrInsert(requestedBy.type, requestedBy.id)
        val appliesTo = AuthorizationScopeConstraint(
            AuthorizationScopeConstraintKind.AppliesTo,
            AuthorizationScopeConstraintAttribute.MeteringPointId,
            ResourceConstraint.MeteringPoints(
                setOf(
                    MeteringPointId.create("707057500000000001"),
                    MeteringPointId.create("707057500000000002"),
                ),
            ),
        )
        val allowedChanges = AuthorizationScopeConstraint(
            constraintKind = AuthorizationScopeConstraintKind.AllowedChanges,
            attribute = AuthorizationScopeConstraintAttribute.ContractType,
            value = ResourceConstraint.ContractTypes(setOf(MeteringPointContractType.EnergySupplier)),
        )
        val constrained = RequestedAuthorizationScope(ResourceType.MeteringPointContract, listOf(appliesTo, allowedChanges))
        val unconstrained = RequestedAuthorizationScope(ResourceType.MeteringPointContract, emptyList())
        val request = AuthorizationRequest.new(
            requestType = AuthorizationRequestType.ChangeOfEnergySupplierForOrganization,
            requestedScopes = listOf(constrained, unconstrained),
            externalReference = "contract-123",
            validTo = future,
            meta = metadata,
            requestedBy = requestedBy,
            requestedPrincipal = requestedBy,
            requestedOf = recipient,
        ).copy(validFrom = createdAt + 2.days, createdAt = createdAt, updatedAt = createdAt + 1.days)

        repository.insert(request) shouldBe request

        repository.find(request.id).meta shouldBe metadata
        val stored = repository.findAndSortByCreatedAt(requestedBy, Pagination(), emptyList()).items.single()
        stored.copy(requestedScopes = emptyList()) shouldBe request.copy(requestedScopes = emptyList())
        stored.requestedScopes.single { it.constraints.isEmpty() } shouldBe unconstrained
        stored.requestedScopes.single { it.constraints.isNotEmpty() }.constraints
            .shouldContainExactlyInAnyOrder(appliesTo, allowedChanges)
        partyRepository.findOrInsert(requestedBy.type, requestedBy.id).id shouldBe existingrequestedBy.id
    }

    test("inserts a request without scopes or external reference") {
        val requestedBy = party()
        val request = AuthorizationRequest.new(
            requestType = AuthorizationRequestType.ChangeOfEnergySupplierForOrganization,
            requestedScopes = emptyList(),
            externalReference = null,
            validTo = future,
            meta = JsonObject(emptyMap()),
            requestedBy = requestedBy,
            requestedPrincipal = requestedBy,
            requestedOf = requestedBy,
        ).copy(validFrom = createdAt + 2.days, createdAt = createdAt, updatedAt = createdAt)

        repository.insert(request) shouldBe request

        repository.findAndSortByCreatedAt(requestedBy, Pagination(), emptyList()).items shouldBe listOf(request)
    }

    test("persists validFrom allowed changes and reads them by ID") {
        AuthorizationScopeConstraintAttribute.fromApiName("validFrom") shouldBe AuthorizationScopeConstraintAttribute.ValidFrom
        val requestedBy = party(PartyType.Organization)
        val recipient = party()
        val appliesTo = AuthorizationScopeConstraint(
            AuthorizationScopeConstraintKind.AppliesTo,
            AuthorizationScopeConstraintAttribute.MeteringPointId,
            ResourceConstraint.MeteringPoints(setOf(MeteringPointId.create("707000000000000001"))),
        )
        val validFrom = AuthorizationScopeConstraint(
            AuthorizationScopeConstraintKind.AllowedChanges,
            AuthorizationScopeConstraintAttribute.ValidFrom,
            ResourceConstraint.ValidFrom(Instant.parse("2026-10-01T00:00:00Z")),
        )
        val request = AuthorizationRequest.new(
            requestType = AuthorizationRequestType.MoveInAndChangeOfEnergySupplierForOrganization,
            requestedScopes = listOf(RequestedAuthorizationScope(ResourceType.MeteringPointContract, listOf(appliesTo, validFrom))),
            externalReference = "contract-124",
            validTo = future,
            meta = metadata,
            requestedBy = requestedBy,
            requestedPrincipal = requestedBy,
            requestedOf = recipient,
        ).copy(validFrom = createdAt + 2.days, createdAt = createdAt, updatedAt = createdAt)

        repository.insert(request) shouldBe request

        val found = repository.find(request.id)
        val listed = repository.findAndSortByCreatedAt(requestedBy, Pagination(), emptyList()).items.single()
        listOf(found, listed).forEach { stored ->
            stored.copy(requestedScopes = emptyList()) shouldBe request.copy(requestedScopes = emptyList())
            stored.requestedScopes.single().constraints.shouldContainExactlyInAnyOrder(appliesTo, validFrom)
        }
    }

    test("includes requestedBy and requestedOf once but excludes requestedPrincipal-only and unrelated parties") {
        val party = party()
        val other = party(PartyType.Organization)
        val by = insertRequest(party, requestedPrincipal = other, requestedOf = other)
        val of = insertRequest(other, requestedOf = party)
        val both = insertRequest(party)
        insertRequest(other, requestedPrincipal = party)
        insertRequest(other)

        val result = repository.findAndSortByCreatedAt(party, Pagination(), emptyList())

        result.items.map { it.id }.shouldContainExactlyInAnyOrder(by, of, both)
        result.totalItems shouldBe 3L
    }

    test("sorts createdAt descending and preserves total count on partial and beyond-end pages") {
        val party = party()
        val middle = insertRequest(party, created = createdAt + 2.days)
        val oldest = insertRequest(party, created = createdAt)
        val newest = insertRequest(party, created = createdAt + 4.days)
        val second = insertRequest(party, created = createdAt + 3.days)
        val fourth = insertRequest(party, created = createdAt + 1.days)

        val firstPage = repository.findAndSortByCreatedAt(party, Pagination(page = 0, size = 2), emptyList())
        val secondPage = repository.findAndSortByCreatedAt(party, Pagination(page = 1, size = 2), emptyList())
        val lastPage = repository.findAndSortByCreatedAt(party, Pagination(page = 2, size = 2), emptyList())
        val beyondEnd = repository.findAndSortByCreatedAt(party, Pagination(page = 3, size = 2), emptyList())

        firstPage.items.map { it.id } shouldBe listOf(newest, second)
        secondPage.items.map { it.id } shouldBe listOf(middle, fourth)
        lastPage.items.map { it.id } shouldBe listOf(oldest)
        beyondEnd.items shouldBe emptyList()

        listOf(firstPage, secondPage, lastPage, beyondEnd).forEachIndexed { page, result ->
            result.totalItems shouldBe 5L
            result.pagination shouldBe Pagination(page = page, size = 2)
        }

        val noStatusMatches = repository.findAndSortByCreatedAt(
            party,
            Pagination(size = 2),
            listOf(AuthorizationRequestStatus.Rejected),
        )
        noStatusMatches.items shouldBe emptyList()
        noStatusMatches.totalItems shouldBe 0L

        val noPartyMatches = repository.findAndSortByCreatedAt(party(), Pagination(), emptyList())
        noPartyMatches.items shouldBe emptyList()
        noPartyMatches.totalItems shouldBe 0L
    }

    test("batch maps parties including approver and scopes with multiple constraints") {
        val requestedBy = party(PartyType.Organization)
        val requestedPrincipal = party(PartyType.Organization)
        val requestedOf = party()
        val approver = party()
        val appliesTo = AuthorizationScopeConstraint(
            AuthorizationScopeConstraintKind.AppliesTo,
            AuthorizationScopeConstraintAttribute.MeteringPointId,
            ResourceConstraint.MeteringPoints(
                setOf(
                    MeteringPointId.create("707057500000000001"),
                    MeteringPointId.create("707057500000000002"),
                )
            ),
        )
        val allowedChanges = AuthorizationScopeConstraint(
            AuthorizationScopeConstraintKind.AllowedChanges,
            AuthorizationScopeConstraintAttribute.ContractType,
            ResourceConstraint.ContractTypes(setOf(MeteringPointContractType.EnergySupplier)),
        )
        val constrained = RequestedAuthorizationScope(ResourceType.MeteringPointContract, listOf(appliesTo, allowedChanges))
        val otherScope = RequestedAuthorizationScope(ResourceType.MeteringPointContract, listOf(appliesTo))
        val withScopes = insertRequest(
            requestedBy = requestedBy,
            requestedPrincipal = requestedPrincipal,
            requestedOf = requestedOf,
            approvedBy = approver,
            externalReference = "contract-123",
            scopes = listOf(constrained, otherScope),
        )
        val withOtherScope = insertRequest(requestedOf, requestedOf = requestedBy, scopes = listOf(otherScope))
        val withoutScopes = insertRequest(requestedBy)

        val result = repository.findAndSortByCreatedAt(requestedBy, Pagination(), emptyList())
        result.totalItems shouldBe 3L
        val items = result.items.associateBy { it.id }
        val mapped = items.getValue(withScopes)
        mapped.requestedBy shouldBe requestedBy
        mapped.requestedPrincipal shouldBe requestedPrincipal
        mapped.requestedOf shouldBe requestedOf
        mapped.approvedBy shouldBe approver
        mapped.status shouldBe AuthorizationRequestStatus.Accepted
        mapped.requestType shouldBe AuthorizationRequestType.ChangeOfEnergySupplierForOrganization
        mapped.externalReference shouldBe "contract-123"
        mapped.validTo shouldBe future
        mapped.createdAt shouldBe createdAt
        mapped.updatedAt shouldBe createdAt + 1.days
        mapped.authorizationGrant shouldBe null
        mapped.requestedScopes.size shouldBe 2
        mapped.requestedScopes.single { it.constraints.size == 1 } shouldBe otherScope
        val mappedScope = mapped.requestedScopes.single { it.constraints.size == 2 }
        mappedScope.resourceType shouldBe constrained.resourceType
        mappedScope.constraints.shouldContainExactlyInAnyOrder(appliesTo, allowedChanges)

        items.getValue(withOtherScope).requestedScopes shouldBe listOf(otherScope)
        items.getValue(withOtherScope).approvedBy shouldBe null
        items.getValue(withoutScopes).requestedScopes shouldBe emptyList()
        items.getValue(withoutScopes).approvedBy shouldBe null
        items.getValue(withoutScopes).externalReference shouldBe null
    }

    test("filters all derived statuses") {
        val party = party()
        val approver = party()
        val expected = mapOf(
            insertRequest(party) to AuthorizationRequestStatus.Pending,
            insertRequest(party, approvedBy = approver) to AuthorizationRequestStatus.Accepted,
            insertRequest(party, approvedBy = approver, validTo = past) to AuthorizationRequestStatus.Accepted,
            insertRequest(party, validTo = past) to AuthorizationRequestStatus.Expired,
            insertRequest(party, status = DatabaseRequestStatus.Rejected) to AuthorizationRequestStatus.Rejected,
            insertRequest(party, status = DatabaseRequestStatus.Rejected, approvedBy = approver, validTo = past) to
                AuthorizationRequestStatus.Rejected,
        )
        insertRequest(approver)
        insertRequest(approver, approvedBy = party)
        insertRequest(approver, validTo = past)
        insertRequest(approver, status = DatabaseRequestStatus.Rejected)

        val filters = AuthorizationRequestStatus.entries.map { listOf(it) } + listOf(
            listOf(AuthorizationRequestStatus.Pending, AuthorizationRequestStatus.Expired),
            AuthorizationRequestStatus.entries.toList(),
            emptyList(),
        )
        filters.forEach { statuses ->
            val matching = expected.filterValues { statuses.isEmpty() || it in statuses }
            val result = repository.findAndSortByCreatedAt(party, Pagination(), statuses)
            result.totalItems shouldBe matching.size.toLong()
            result.items.size shouldBe matching.size
            result.items.associate { it.id to it.status } shouldBe matching
        }
    }

    test("reject(id) persists status and updatedAt while preserving request fields") {
        val scope = RequestedAuthorizationScope(ResourceType.MeteringPointContract, emptyList())
        val id = insertRequest(
            requestedBy = party(),
            requestedPrincipal = party(),
            requestedOf = party(),
            externalReference = "reject-test",
            redirectUri = "https://example.com/rejected",
            scopes = listOf(scope),
            meta = metadata,
        )
        val original = repository.find(id)
        val before = now()
        val rejectedRequest = repository.reject(id)
        val after = now()
        rejectedRequest.status shouldBe AuthorizationRequestStatus.Rejected
        (rejectedRequest.updatedAt in before..after) shouldBe true
        rejectedRequest shouldBe original.copy(
            status = AuthorizationRequestStatus.Rejected,
            updatedAt = rejectedRequest.updatedAt,
        )
        repository.find(id) shouldBe rejectedRequest
    }

    test("reject(id) throws notfound for nonexistent id") {
        val randomUUID = UUID.randomUUID()
        val error = shouldThrow<RepositoryWriteError.NotFoundError> {
            repository.reject(randomUUID)
        }
        error.detail shouldBe "Request not found: $randomUUID"
    }

    test("reject(id) throws conflict on double-reject") {
        val id = insertRequest(party())
        val rejectedRequest = repository.reject(id)
        rejectedRequest.status shouldBe AuthorizationRequestStatus.Rejected
        val error = shouldThrow<RepositoryWriteError.ConflictError> {
            repository.reject(id)
        }
        error.detail shouldBe "Invalid transition for request $id from Rejected to Rejected"
        repository.find(id) shouldBe rejectedRequest
    }

    test("reject(id) throws conflict for accepted requests") {
        listOf(future, past).forEach { validTo ->
            val id = insertRequest(party(), approvedBy = party(), validTo = validTo)
            val original = repository.find(id)
            val error = shouldThrow<RepositoryWriteError.ConflictError> {
                repository.reject(id)
            }
            error.detail shouldBe "Invalid transition for request $id from Accepted to Rejected"
            repository.find(id) shouldBe original
        }
    }

    test("reject(id) throws expired for expired requests without changing them") {
        val id = insertRequest(party(), validTo = past)
        val original = repository.find(id)
        val error = shouldThrow<RepositoryWriteError.ExpiredError> {
            repository.reject(id)
        }
        error.detail shouldBe "Request $id has expired"
        repository.find(id) shouldBe original
    }

    test("accept(id, grant) persists approval and updatedAt while preserving request fields") {
        val scope = RequestedAuthorizationScope(ResourceType.MeteringPointContract, emptyList())
        val id = insertRequest(
            requestedBy = party(PartyType.Organization),
            requestedPrincipal = party(PartyType.Organization),
            requestedOf = party(),
            externalReference = "accept-test",
            redirectUri = "https://example.com/accepted",
            scopes = listOf(scope),
            meta = metadata,
        )
        val original = repository.find(id)
        val grant = grant(original)
        val before = now()

        val accepted = repository.accept(id, listOf(grant))

        val after = now()
        (accepted.updatedAt in before..after) shouldBe true
        accepted shouldBe original.copy(
            status = AuthorizationRequestStatus.Accepted,
            approvedBy = grant.approvedBy,
            updatedAt = accepted.updatedAt,
            authorizationGrant = listOf(grant),
        )
        withTransaction {
            val row = AuthorizationRequestTable.selectAll()
                .where { AuthorizationRequestTable.id eq id }
                .single()
            row[AuthorizationRequestTable.status] shouldBe DatabaseRequestStatus.Pending
            row[AuthorizationRequestTable.approvedAt] shouldBe accepted.updatedAt
        }
        repository.find(id) shouldBe accepted
    }

    test("accept persists multiple grants and reads them by ID and in a page") {
        val requestedBy = party(PartyType.Organization)
        val id = insertRequest(requestedBy)
        val original = repository.find(id)
        val first = grant(original).copy(approvedBy = requestedBy)
        val second = grant(original).copy(approvedBy = requestedBy)
        val grants = listOf(first, second)

        val accepted = repository.accept(id, grants)

        accepted.approvedBy shouldBe requestedBy
        accepted.authorizationGrant.shouldContainExactlyInAnyOrder(grants)
        val found = repository.find(id)
        val listed = repository.findAndSortByCreatedAt(
            requestedBy,
            Pagination(),
            listOf(AuthorizationRequestStatus.Accepted),
        ).items.single()
        listOf(found, listed).forEach { stored ->
            stored.copy(authorizationGrant = null) shouldBe accepted.copy(authorizationGrant = null)
            stored.authorizationGrant.shouldContainExactlyInAnyOrder(grants)
        }
        grants.forEach { grant -> grantRepository.find(grant.id) shouldBe grant }
    }

    test("accept(id, grant) throws not found for a nonexistent ID") {
        val requestedBy = party()
        val request = AuthorizationRequest.new(
            requestType = AuthorizationRequestType.ChangeOfEnergySupplierForOrganization,
            requestedScopes = emptyList(),
            externalReference = null,
            meta = JsonObject(emptyMap()),
            validTo = future,
            requestedBy = requestedBy,
            requestedPrincipal = requestedBy,
            requestedOf = requestedBy,
        )

        val error = shouldThrow<RepositoryWriteError.NotFoundError> {
            repository.accept(request.id, listOf(grant(request)))
        }

        error.detail shouldBe "Request not found: ${request.id}"
    }

    test("accept(id, grant) throws conflict on double-accept") {
        val id = insertRequest(party())
        val original = repository.find(id)
        val accepted = repository.accept(id, listOf(grant(original)))
        accepted.status shouldBe AuthorizationRequestStatus.Accepted

        val error = shouldThrow<RepositoryWriteError.ConflictError> {
            repository.accept(id, listOf(grant(original)))
        }

        error.detail shouldBe "Invalid transition for request $id from Accepted to Accepted"
        repository.find(id) shouldBe accepted
    }

    test("accept(id, grant) throws conflict for rejected requests") {
        listOf(future, past).forEach { validTo ->
            val id = insertRequest(party(), status = DatabaseRequestStatus.Rejected, validTo = validTo)
            val original = repository.find(id)

            val error = shouldThrow<RepositoryWriteError.ConflictError> {
                repository.accept(id, listOf(grant(original)))
            }

            error.detail shouldBe "Invalid transition for request $id from Rejected to Accepted"
            repository.find(id) shouldBe original
        }
    }

    test("accept(id, grant) throws expired for expired requests without changing them") {
        val id = insertRequest(party(), validTo = past)
        val original = repository.find(id)

        val error = shouldThrow<RepositoryWriteError.ExpiredError> {
            repository.accept(id, listOf(grant(original)))
        }

        error.detail shouldBe "Request $id has expired"
        repository.find(id) shouldBe original
    }

    test("concurrent accept(id, grant) calls produce one success and one conflict") {
        val id = insertRequest(party())
        val original = repository.find(id)
        val grants = List(2) { grant(original) }
        val results = coroutineScope {
            val start = CompletableDeferred<Unit>()
            val attempts = grants.map { grant ->
                async {
                    start.await()
                    runCatching { repository.accept(id, listOf(grant)) }
                }
            }
            start.complete(Unit)
            attempts.awaitAll()
        }

        results.count { it.isSuccess } shouldBe 1
        val error = results.single { it.isFailure }.exceptionOrNull()
        (error is RepositoryWriteError.ConflictError) shouldBe true
        val accepted = results.single { it.isSuccess }.getOrThrow()
        accepted.status shouldBe AuthorizationRequestStatus.Accepted
        accepted.authorizationGrant shouldBe listOf(grants[results.indexOfFirst { it.isSuccess }])
        repository.find(id) shouldBe accepted
    }

    test("concurrent accept(id, grant) and reject(id) calls produce one success and one conflict") {
        val id = insertRequest(party())
        val grant = grant(repository.find(id))
        val results = coroutineScope {
            val start = CompletableDeferred<Unit>()
            val attempts = listOf(
                async {
                    start.await()
                    runCatching { repository.accept(id, listOf(grant)) }
                },
                async {
                    start.await()
                    runCatching { repository.reject(id) }
                },
            )
            start.complete(Unit)
            attempts.awaitAll()
        }

        results.count { it.isSuccess } shouldBe 1
        val winner = results.single { it.isSuccess }.getOrThrow()
        val error = results.single { it.isFailure }.exceptionOrNull()
        (error is RepositoryWriteError.ConflictError) shouldBe true
        winner.status shouldBe if (results.first().isSuccess) {
            AuthorizationRequestStatus.Accepted
        } else {
            AuthorizationRequestStatus.Rejected
        }
        winner.approvedBy shouldBe if (results.first().isSuccess) grant.approvedBy else null
        winner.authorizationGrant shouldBe if (results.first().isSuccess) listOf(grant) else null
        repository.find(id) shouldBe winner
    }

    test("concurrent reject(id) calls produce one success and one conflict") {
        val id = insertRequest(party())
        val results = coroutineScope {
            val start = CompletableDeferred<Unit>()
            val attempts = List(2) {
                async {
                    start.await()
                    runCatching { repository.reject(id) }
                }
            }
            start.complete(Unit)
            attempts.awaitAll()
        }

        results.count { it.isSuccess } shouldBe 1
        val error = results.single { it.isFailure }.exceptionOrNull()
        (error is RepositoryWriteError.ConflictError) shouldBe true
        val rejectedRequest = results.single { it.isSuccess }.getOrThrow()
        rejectedRequest.status shouldBe AuthorizationRequestStatus.Rejected
        repository.find(id) shouldBe rejectedRequest
    }
})
