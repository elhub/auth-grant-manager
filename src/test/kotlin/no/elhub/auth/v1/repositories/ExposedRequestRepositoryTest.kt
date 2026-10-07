package no.elhub.auth.v1.repositories

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import no.elhub.auth.v0.config.withTransaction
import no.elhub.auth.v0.features.common.Pagination
import no.elhub.auth.v0.features.common.PostgresTestContainer
import no.elhub.auth.v0.features.common.PostgresTestContainerExtension
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v0.features.common.party.PartyType
import no.elhub.auth.v1.domain.AuthorizationRequestStatus
import no.elhub.auth.v1.domain.AuthorizationRequestType
import no.elhub.auth.v1.domain.AuthorizationScopeConstraint
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintAttribute
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintKind
import no.elhub.auth.v1.domain.MeteringPointId
import no.elhub.auth.v1.domain.RequestedAuthorizationScope
import no.elhub.auth.v1.domain.ResourceConstraint
import no.elhub.auth.v1.domain.ResourceType
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
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
    val repository = ExposedRequestRepository(partyRepository)
    val createdAt = Instant.parse("2025-01-01T00:00:00Z")
    val future = Instant.parse("2100-01-01T00:00:00Z")
    val past = Instant.parse("2000-01-01T00:00:00Z")

    fun party(type: PartyType = PartyType.Person) = AuthorizationParty(id = UUID.randomUUID().toString(), type = type)

    suspend fun insertRequest(
        requestedBy: AuthorizationParty,
        requestedFrom: AuthorizationParty = requestedBy,
        requestedTo: AuthorizationParty = requestedBy,
        approvedBy: AuthorizationParty? = null,
        status: DatabaseRequestStatus = DatabaseRequestStatus.Pending,
        validTo: Instant = future,
        created: Instant = createdAt,
        externalReference: String? = null,
        scopes: List<RequestedAuthorizationScope> = emptyList(),
    ): UUID = withTransaction {
        suspend fun partyId(party: AuthorizationParty) = partyRepository.findOrInsert(party.type, party.id).id
        val byId = partyId(requestedBy)
        val fromId = partyId(requestedFrom)
        val toId = partyId(requestedTo)
        val approverId = approvedBy?.let { partyId(it) }
        val requestId = AuthorizationRequestTable.insert {
            it[requestType] = AuthorizationRequestType.ChangeOfEnergySupplierForOrganization
            it[AuthorizationRequestTable.status] = status
            it[AuthorizationRequestTable.requestedBy] = byId
            it[AuthorizationRequestTable.requestedFrom] = fromId
            it[AuthorizationRequestTable.requestedTo] = toId
            it[AuthorizationRequestTable.approvedBy] = approverId
            it[AuthorizationRequestTable.validTo] = validTo
            it[AuthorizationRequestTable.createdAt] = created
            it[updatedAt] = created + 1.days
            it[validFrom] = created
            it[AuthorizationRequestTable.externalReference] = externalReference
        }[AuthorizationRequestTable.id].value

        scopes.forEach { scope ->
            val scopeId = AuthorizationRequestScopeTable.insert {
                it[AuthorizationRequestScopeTable.requestId] = requestId
                it[resourceType] = scope.resourceType.name
            }[AuthorizationRequestScopeTable.id].value
            scope.constraints.forEach { constraint ->
                AuthorizationRequestScopeConstraintTable.insert {
                    it[AuthorizationRequestScopeConstraintTable.scopeId] = scopeId
                    it[kind] = constraint.constraintKind
                    it[attribute] = constraint.attribute
                    it[value] = (constraint.value as ResourceConstraint.MeteringPoints).ids.map { id -> id.value }
                }
            }
        }
        requestId
    }

    test("includes requestedBy and requestedTo once but excludes requestedFrom-only and unrelated parties") {
        val party = party()
        val other = party(PartyType.Organization)
        val by = insertRequest(party, requestedFrom = other, requestedTo = other)
        val to = insertRequest(other, requestedTo = party)
        val both = insertRequest(party)
        insertRequest(other, requestedFrom = party)
        insertRequest(other)

        val result = repository.findAndSortByCreatedAt(party, Pagination(), emptyList())

        result.items.map { it.id }.shouldContainExactlyInAnyOrder(by, to, both)
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
        val requester = party(PartyType.Organization)
        val from = party(PartyType.Organization)
        val to = party()
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
            AuthorizationScopeConstraintAttribute.MeteringPointId,
            ResourceConstraint.MeteringPoints(setOf(MeteringPointId.create("707057500000000003"))),
        )
        val constrained = RequestedAuthorizationScope(ResourceType.MeteringPoint, listOf(appliesTo, allowedChanges))
        val otherScope = RequestedAuthorizationScope(ResourceType.MeteringPoint, listOf(appliesTo))
        val withScopes = insertRequest(
            requestedBy = requester,
            requestedFrom = from,
            requestedTo = to,
            approvedBy = approver,
            externalReference = "contract-123",
            scopes = listOf(constrained, otherScope),
        )
        val withOtherScope = insertRequest(to, requestedTo = requester, scopes = listOf(otherScope))
        val withoutScopes = insertRequest(requester)

        val result = repository.findAndSortByCreatedAt(requester, Pagination(), emptyList())
        result.totalItems shouldBe 3L
        val items = result.items.associateBy { it.id }
        val mapped = items.getValue(withScopes)
        mapped.requestedBy shouldBe requester
        mapped.requestedFrom shouldBe from
        mapped.requestedTo shouldBe to
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
            insertRequest(party, status = DatabaseRequestStatus.Revoked) to AuthorizationRequestStatus.Revoked,
            insertRequest(party, status = DatabaseRequestStatus.Revoked, approvedBy = approver, validTo = past) to
                AuthorizationRequestStatus.Revoked,
        )
        insertRequest(approver)
        insertRequest(approver, approvedBy = party)
        insertRequest(approver, validTo = past)
        insertRequest(approver, status = DatabaseRequestStatus.Rejected)
        insertRequest(approver, status = DatabaseRequestStatus.Revoked)

        val filters = AuthorizationRequestStatus.entries.map { listOf(it) } + listOf(
            listOf(AuthorizationRequestStatus.Pending, AuthorizationRequestStatus.Expired),
            listOf(AuthorizationRequestStatus.Accepted, AuthorizationRequestStatus.Rejected, AuthorizationRequestStatus.Revoked),
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
})
