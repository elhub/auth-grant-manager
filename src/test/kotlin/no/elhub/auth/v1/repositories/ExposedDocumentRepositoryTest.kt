package no.elhub.auth.v1.repositories

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import no.elhub.auth.v0.features.common.Pagination
import no.elhub.auth.v0.features.common.PostgresTestContainer
import no.elhub.auth.v0.features.common.PostgresTestContainerExtension
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v0.features.common.party.PartyType
import no.elhub.auth.v1.RepositoryReadError
import no.elhub.auth.v1.common.now
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
    val past = Instant.parse("2000-01-01T00:00:00Z")

    fun party(type: PartyType = PartyType.Person) = AuthorizationParty(UUID.randomUUID().toString(), type)

    fun document(
        requestedBy: AuthorizationParty = party(),
        requestedPrincipal: AuthorizationParty = requestedBy,
        requestedOf: AuthorizationParty = requestedBy,
        approvedBy: AuthorizationParty? = null,
        validTo: Instant = future,
        created: Instant = createdAt,
        meta: JsonObject = JsonObject(emptyMap()),
        scopes: List<RequestedAuthorizationScope> = emptyList(),
    ) = AuthorizationDocument.new(
        documentType = AuthorizationDocumentType.MoveInAndChangeOfEnergySupplierForOrganization,
        requestedScopes = scopes,
        externalReference = null,
        meta = meta,
        validTo = validTo,
        requestedBy = requestedBy,
        requestedPrincipal = requestedPrincipal,
        requestedOf = requestedOf,
        pdfBytes = byteArrayOf(37, 80, 68, 70, 0, -1),
    ).copy(createdAt = created, updatedAt = created + 1.days, approvedBy = approvedBy)

    test("round-trips typed nested JSON metadata, PDF bytes, parties, scopes and constraints") {
        val requestedBy = party(PartyType.Organization)
        val existingParty = partyRepository.findOrInsert(requestedBy.type, requestedBy.id)
        val meta = Json.parseToJsonElement(
            """{"name":"contract","attempt":2,"enabled":true,"optional":null,"nested":{"values":["x",3,false,null,{"id":"123"}]}}""",
        ).jsonObject
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
                ResourceConstraint.ContractTypes(setOf(MeteringPointContractType.EnergySupplier, MeteringPointContractType.EndUser)),
            ),
            AuthorizationScopeConstraint(
                AuthorizationScopeConstraintKind.AllowedChanges,
                AuthorizationScopeConstraintAttribute.ValidFrom,
                ResourceConstraint.ValidFrom(Instant.parse("2026-10-01T00:00:00Z")),
            ),
        )
        val unconstrained = RequestedAuthorizationScope(ResourceType.MeteringPointContract, emptyList())
        val original = document(
            requestedBy = requestedBy,
            requestedPrincipal = party(PartyType.Organization),
            requestedOf = party(),
            approvedBy = party(),
            meta = meta,
            scopes = listOf(RequestedAuthorizationScope(ResourceType.MeteringPointContract, constraints), unconstrained),
        ).copy(externalReference = "document-123", status = AuthorizationDocumentStatus.Accepted)

        val inserted = repository.insert(original)
        val found = repository.find(UUID.fromString(original.id))
        val page = repository.findAndSortByCreatedAt(requestedBy, Pagination(), emptyList())

        page.totalItems shouldBe 1L
        listOf(inserted, found, page.items.single()).forEach { stored ->
            stored.pdfBytes.contentEquals(original.pdfBytes) shouldBe true
            stored.copy(pdfBytes = original.pdfBytes, requestedScopes = emptyList()) shouldBe
                original.copy(requestedScopes = emptyList())
            stored.meta shouldBe meta
            stored.requestedScopes.size shouldBe 2
            stored.requestedScopes.single { it.constraints.isEmpty() } shouldBe unconstrained
            stored.requestedScopes.single { it.constraints.isNotEmpty() }.constraints
                .shouldContainExactlyInAnyOrder(constraints)
        }
        partyRepository.findOrInsert(requestedBy.type, requestedBy.id).id shouldBe existingParty.id
    }

    test("round-trips empty metadata and optional fields without scopes or signatory") {
        val original = document()

        repository.insert(original)
        val found = repository.find(UUID.fromString(original.id))
        val listed = repository.findAndSortByCreatedAt(original.requestedBy, Pagination(), emptyList()).items.single()

        listOf(found, listed).forEach { stored ->
            stored.copy(pdfBytes = original.pdfBytes) shouldBe original
            stored.pdfBytes.contentEquals(original.pdfBytes) shouldBe true
            stored.meta shouldBe JsonObject(emptyMap())
        }
    }

    test("derives and filters statuses with signature taking precedence over expiry") {
        val requestedBy = party()
        val originals = listOf(
            document(requestedBy),
            document(requestedBy, approvedBy = party()),
            document(requestedBy, approvedBy = party(), validTo = past),
            document(requestedBy, validTo = past),
            document(requestedBy, validTo = now()),
        )
        val statuses = listOf(
            AuthorizationDocumentStatus.Pending,
            AuthorizationDocumentStatus.Accepted,
            AuthorizationDocumentStatus.Accepted,
            AuthorizationDocumentStatus.Expired,
            AuthorizationDocumentStatus.Expired,
        )
        val expected = originals.zip(statuses).associate { (document, status) ->
            repository.insert(document).status shouldBe status
            repository.find(UUID.fromString(document.id)).status shouldBe status
            document.id to status
        }
        repository.insert(document(approvedBy = requestedBy))

        val filters = AuthorizationDocumentStatus.entries.map { listOf(it) } + listOf(
            listOf(AuthorizationDocumentStatus.Accepted, AuthorizationDocumentStatus.Expired),
            emptyList(),
        )
        filters.forEach { filter ->
            val matching = expected.filterValues { filter.isEmpty() || it in filter }
            val page = repository.findAndSortByCreatedAt(requestedBy, Pagination(), filter)
            page.totalItems shouldBe matching.size.toLong()
            page.items.associate { it.id to it.status } shouldBe matching
        }
    }

    test("lists only the requester and sorts and paginates by creation time") {
        val requestedBy = party()
        val oldest = repository.insert(document(requestedBy, created = createdAt))
        val middle = repository.insert(document(requestedBy, created = createdAt + 1.days))
        val newest = repository.insert(document(requestedBy, created = createdAt + 2.days))
        repository.insert(document(requestedPrincipal = requestedBy))
        repository.insert(document(requestedOf = requestedBy))
        repository.insert(document(approvedBy = requestedBy))

        val first = repository.findAndSortByCreatedAt(requestedBy, Pagination(size = 2), emptyList())
        val second = repository.findAndSortByCreatedAt(requestedBy, Pagination(page = 1, size = 2), emptyList())
        val beyond = repository.findAndSortByCreatedAt(requestedBy, Pagination(page = 2, size = 2), emptyList())

        first.items.map { it.id } shouldBe listOf(newest.id, middle.id)
        second.items.map { it.id } shouldBe listOf(oldest.id)
        beyond.items shouldBe emptyList()
        listOf(first, second, beyond).forEachIndexed { index, page ->
            page.totalItems shouldBe 3L
            page.pagination shouldBe Pagination(page = index, size = 2)
        }
        val noMatches = repository.findAndSortByCreatedAt(requestedBy, Pagination(), listOf(AuthorizationDocumentStatus.Accepted))
        noMatches.items shouldBe emptyList()
        noMatches.totalItems shouldBe 0L
        val unknownParty = repository.findAndSortByCreatedAt(party(), Pagination(), emptyList())
        unknownParty.items shouldBe emptyList()
        unknownParty.totalItems shouldBe 0L
    }

    test("throws a modeled not found error for an unknown document ID") {
        val id = UUID.randomUUID()

        val error = shouldThrow<RepositoryReadError.NotFoundError> { repository.find(id) }

        error.detail shouldBe "Document not found: $id"
    }
})
