package no.elhub.auth.v1.repositories

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import no.elhub.auth.v0.config.withTransaction
import no.elhub.auth.v0.features.common.Pagination
import no.elhub.auth.v0.features.common.PostgresTestContainer
import no.elhub.auth.v0.features.common.PostgresTestContainerExtension
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v0.features.common.party.PartyType
import no.elhub.auth.v1.RepositoryReadError
import no.elhub.auth.v1.domain.AuthorizationDocument
import no.elhub.auth.v1.domain.AuthorizationDocumentStatus
import no.elhub.auth.v1.domain.AuthorizationDocumentType
import no.elhub.auth.v1.domain.AuthorizationScopeConstraint
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintAttribute
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintKind
import no.elhub.auth.v1.domain.MeteringPointContractType
import no.elhub.auth.v1.domain.MeteringPointId
import no.elhub.auth.v1.domain.RequestedAuthorizationScope
import no.elhub.auth.v1.domain.ResourceConstraint
import no.elhub.auth.v1.domain.ResourceType
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import java.util.UUID
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class ExposedDocumentRepositoryTest : FunSpec({
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
    val repository = ExposedDocumentRepository(partyRepository)
    val createdAt = Instant.parse("2025-01-01T00:00:00Z")
    val future = Instant.parse("2100-01-01T00:00:00Z")

    fun party(type: PartyType = PartyType.Person) = AuthorizationParty(UUID.randomUUID().toString(), type)

    fun document() = AuthorizationDocument.new(
        documentType = AuthorizationDocumentType.MoveInAndChangeOfEnergySupplierForOrganization,
        requestedScopes = emptyList(),
        externalReference = null,
        meta = Json.parseToJsonElement("""{"nested":{"values":[null,false,1.5]}}""").jsonObject,
        validTo = future,
        requestedBy = party(PartyType.Organization),
        requestedPrincipal = party(PartyType.Organization),
        requestedOf = party(),
        pdfBytes = byteArrayOf(0, 1, 127, -128, -1),
    ).copy(createdAt = createdAt, updatedAt = createdAt + 1.days)

    suspend fun seed(document: AuthorizationDocument): UUID = withTransaction {
        suspend fun partyId(party: AuthorizationParty) = partyRepository.findOrInsert(party.type, party.id).id
        val requestedBy = partyId(document.requestedBy)
        val requestedPrincipal = partyId(document.requestedPrincipal)
        val requestedOf = partyId(document.requestedOf)
        val approvedBy = document.approvedBy?.let { partyId(it) }
        val documentId = UUID.fromString(document.id)
        AuthorizationDocumentTable.insert {
            it[id] = documentId
            it[documentType] = document.documentType
            it[AuthorizationDocumentTable.requestedBy] = requestedBy
            it[AuthorizationDocumentTable.requestedPrincipal] = requestedPrincipal
            it[AuthorizationDocumentTable.requestedOf] = requestedOf
            it[AuthorizationDocumentTable.approvedBy] = approvedBy
            it[externalReference] = document.externalReference
            it[meta] = document.meta
            it[file] = document.pdfBytes
            it[validFrom] = document.createdAt
            it[validTo] = document.validTo
            it[AuthorizationDocumentTable.createdAt] = document.createdAt
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
                    it[value] = when (val value = constraint.value) {
                        is ResourceConstraint.MeteringPoints -> value.ids.map { it.value }
                        is ResourceConstraint.ContractTypes -> value.types.map { it.name }
                        is ResourceConstraint.ValidFrom -> listOf(value.value.toString())
                    }
                }
            }
        }
        documentId
    }

    test("find maps the stored document") {
        val constraints = listOf(
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
                ResourceConstraint.ContractTypes(setOf(MeteringPointContractType.EnergySupplier)),
            ),
            AuthorizationScopeConstraint(
                AuthorizationScopeConstraintKind.AllowedChanges,
                AuthorizationScopeConstraintAttribute.ValidFrom,
                ResourceConstraint.ValidFrom(createdAt),
            ),
        )
        val unconstrained = RequestedAuthorizationScope(ResourceType.MeteringPointContract, emptyList())
        val expected = document().copy(
            status = AuthorizationDocumentStatus.Accepted,
            approvedBy = party(),
            externalReference = "contract-123",
            requestedScopes = listOf(RequestedAuthorizationScope(ResourceType.MeteringPointContract, constraints), unconstrained),
        )
        val id = seed(expected)
        seed(document().copy(requestedScopes = expected.requestedScopes))

        val result = repository.find(id)

        result.copy(requestedScopes = emptyList(), pdfBytes = expected.pdfBytes) shouldBe expected.copy(requestedScopes = emptyList())
        result.pdfBytes.toList() shouldBe expected.pdfBytes.toList()
        result.requestedScopes.size shouldBe 2
        result.requestedScopes.single { it.constraints.isEmpty() } shouldBe unconstrained
        val constrained = result.requestedScopes.single { it.constraints.isNotEmpty() }
        constrained.resourceType shouldBe ResourceType.MeteringPointContract
        constrained.constraints.shouldContainExactlyInAnyOrder(constraints)
    }

    test("find maps a document without optional relationships") {
        val expected = document().copy(documentType = AuthorizationDocumentType.ChangeOfEnergySupplierForOrganization)
        val id = seed(expected)

        val result = repository.find(id)

        result.copy(pdfBytes = expected.pdfBytes) shouldBe expected
        result.pdfBytes.toList() shouldBe expected.pdfBytes.toList()
    }

    test("find derives status with approval taking precedence over expiry") {
        val past = Instant.parse("2000-01-01T00:00:00Z")
        val expected = listOf(
            document() to AuthorizationDocumentStatus.Pending,
            document().copy(validTo = past) to AuthorizationDocumentStatus.Expired,
            document().copy(approvedBy = party()) to AuthorizationDocumentStatus.Accepted,
            document().copy(approvedBy = party(), validTo = past) to AuthorizationDocumentStatus.Accepted,
        )

        expected.forEach { (document, status) ->
            val id = seed(document)
            repository.find(id).status shouldBe status
        }
    }

    test("find throws a modeled not found error for an unknown document ID") {
        val id = UUID.randomUUID()

        val error = shouldThrow<RepositoryReadError.NotFoundError> { repository.find(id) }

        error.detail shouldBe "Document not found: $id"
    }

    test("listing includes requester or principal but excludes other roles and party types") {
        val party = party(PartyType.Organization)
        val by = seed(document().copy(requestedBy = party))
        val principal = seed(document().copy(requestedPrincipal = party))
        val both = seed(document().copy(requestedBy = party, requestedPrincipal = party))
        seed(document().copy(requestedOf = party))
        seed(document().copy(approvedBy = party))
        seed(document().copy(requestedBy = party.copy(type = PartyType.Person)))
        seed(document())

        val result = repository.findAndSortByCreatedAt(party, Pagination(), emptyList())

        result.items.map { it.id }.shouldContainExactlyInAnyOrder(by.toString(), principal.toString(), both.toString())
        result.totalItems shouldBe 3L
    }

    test("listing sorts by creation time then UUID descending with consistent page totals") {
        val party = party()
        val ids = listOf(
            "00000000-0000-0000-0000-000000000001",
            "00000000-0000-0000-0000-000000000002",
            "00000000-0000-0000-0000-000000000003",
            "00000000-0000-0000-0000-000000000004",
            "00000000-0000-0000-0000-000000000005",
        )
        val times = listOf(createdAt + 2.days, createdAt, createdAt + 2.days, createdAt + 3.days, createdAt + 1.days)
        ids.zip(times).forEach { (id, time) -> seed(document().copy(id = id, requestedBy = party, createdAt = time)) }
        val expectedPages = listOf(listOf(ids[3], ids[2]), listOf(ids[0], ids[4]), listOf(ids[1]), emptyList())

        expectedPages.forEachIndexed { page, expected ->
            val pagination = Pagination(page = page, size = 2)
            val result = repository.findAndSortByCreatedAt(party, pagination, emptyList())
            result.items.map { it.id } shouldBe expected
            result.totalItems shouldBe 5L
            result.pagination shouldBe pagination
        }
    }

    test("listing filters derived document statuses including combined and empty filters") {
        val party = party()
        val past = Instant.parse("2000-01-01T00:00:00Z")
        val expected = listOf(
            document() to AuthorizationDocumentStatus.Pending,
            document().copy(validTo = past) to AuthorizationDocumentStatus.Expired,
            document().copy(approvedBy = party()) to AuthorizationDocumentStatus.Accepted,
            document().copy(approvedBy = party(), validTo = past) to AuthorizationDocumentStatus.Accepted,
        ).associate { (document, status) -> seed(document.copy(requestedBy = party)).toString() to status }
        seed(document().copy(validTo = past))
        seed(document().copy(approvedBy = party()))
        val filters = AuthorizationDocumentStatus.entries.map { listOf(it) } + listOf(
            listOf(AuthorizationDocumentStatus.Pending, AuthorizationDocumentStatus.Expired),
            AuthorizationDocumentStatus.entries.toList(),
            emptyList(),
        )

        filters.forEach { statuses ->
            val matching = expected.filterValues { statuses.isEmpty() || it in statuses }
            val result = repository.findAndSortByCreatedAt(party, Pagination(), statuses)
            result.items.associate { it.id to it.status } shouldBe matching
            result.totalItems shouldBe matching.size.toLong()
        }
    }

    test("listing returns empty pages when no party or status matches") {
        val party = party()
        seed(document().copy(requestedBy = party))

        val noParty = repository.findAndSortByCreatedAt(party(), Pagination(), emptyList())
        val noStatus = repository.findAndSortByCreatedAt(party, Pagination(), listOf(AuthorizationDocumentStatus.Accepted))

        listOf(noParty, noStatus).forEach {
            it.items shouldBe emptyList()
            it.totalItems shouldBe 0L
            it.pagination shouldBe Pagination()
        }
    }

    test("listing maps each document with its own parties and scopes") {
        val party = party(PartyType.Organization)
        val constraints = listOf(
            AuthorizationScopeConstraint(
                AuthorizationScopeConstraintKind.AppliesTo,
                AuthorizationScopeConstraintAttribute.MeteringPointId,
                ResourceConstraint.MeteringPoints(setOf(MeteringPointId.create("707057500000000001"))),
            ),
            AuthorizationScopeConstraint(
                AuthorizationScopeConstraintKind.AllowedChanges,
                AuthorizationScopeConstraintAttribute.ContractType,
                ResourceConstraint.ContractTypes(setOf(MeteringPointContractType.EnergySupplier)),
            ),
            AuthorizationScopeConstraint(
                AuthorizationScopeConstraintKind.AllowedChanges,
                AuthorizationScopeConstraintAttribute.ValidFrom,
                ResourceConstraint.ValidFrom(createdAt),
            ),
        )
        val unconstrained = RequestedAuthorizationScope(ResourceType.MeteringPointContract, emptyList())
        val constrained = RequestedAuthorizationScope(ResourceType.MeteringPointContract, constraints)
        val documents = listOf(
            document().copy(
                requestedBy = party,
                approvedBy = party(),
                status = AuthorizationDocumentStatus.Accepted,
                externalReference = "listed-contract",
                requestedScopes = listOf(constrained, unconstrained),
            ),
            document().copy(requestedPrincipal = party, requestedScopes = listOf(unconstrained)),
            document().copy(requestedBy = party, documentType = AuthorizationDocumentType.ChangeOfEnergySupplierForOrganization),
        )
        documents.forEach { seed(it) }
        seed(document().copy(requestedScopes = listOf(constrained)))

        val result = repository.findAndSortByCreatedAt(party, Pagination(), emptyList())

        result.totalItems shouldBe 3L
        val items = result.items.associateBy { it.id }
        documents.forEach { expected ->
            val actual = items.getValue(expected.id)
            actual.copy(requestedScopes = emptyList(), pdfBytes = expected.pdfBytes) shouldBe expected.copy(requestedScopes = emptyList())
            actual.pdfBytes.toList() shouldBe expected.pdfBytes.toList()
            actual.requestedScopes.size shouldBe expected.requestedScopes.size
            actual.requestedScopes.filter { it.constraints.isEmpty() } shouldBe expected.requestedScopes.filter { it.constraints.isEmpty() }
            if (expected.requestedScopes.any { it.constraints.isNotEmpty() }) {
                actual.requestedScopes.single { it.constraints.isNotEmpty() }.constraints.shouldContainExactlyInAnyOrder(constraints)
            }
        }
    }
})
